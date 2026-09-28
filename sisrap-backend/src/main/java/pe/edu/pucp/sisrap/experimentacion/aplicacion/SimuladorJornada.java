package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import pe.edu.pucp.sisrap.experimentacion.dominio.BaseOperativa;
import pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo;
import pe.edu.pucp.sisrap.experimentacion.dominio.DatosArchivos;
import pe.edu.pucp.sisrap.experimentacion.dominio.PlanExperimento;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
import pe.edu.pucp.sisrap.parametros.dominio.ParametrosAlgoritmo;
import pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.algoritmo.IAlgoritmoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;
import pe.edu.pucp.sisrap.planificador.dominio.objetivo.FuncionObjetivo;

public final class SimuladorJornada {

    private SimuladorJornada() {
    }

    public record Resultado(
            LocalDate fecha,
            Solucion solucion,
            ContextoPlanificacion contexto,
            IAlgoritmoPlanificacion motor,
            double tiempoEjecucionMs,
            int evaluaciones,
            int tamanio) {
    }

    public static Resultado ejecutar(
            BiFunction<
                    ParametrosAlgoritmo,
                    FuncionObjetivo,
                    IAlgoritmoPlanificacion> fabrica,
            Configuracion cfg,
            ReglasPlanificacion reglas,
            PlanExperimento plan,
            BaseOperativa base,
            DatosArchivos archivos,
            LocalDate fecha,
            List<Pedido> pedidosOriginales,
            List<Bloqueo> bloqueosProgramados,
            long semillaAlgoritmo) {

        if (fabrica == null
                || cfg == null
                || reglas == null
                || plan == null
                || base == null
                || archivos == null
                || fecha == null
                || pedidosOriginales == null
                || pedidosOriginales.isEmpty()
                || bloqueosProgramados == null) {

            throw new IllegalArgumentException(
                    "Jornada incompleta");
        }

        ContextoPlanificacion contexto = crearContexto(
                reglas,
                plan,
                base,
                archivos,
                fecha,
                pedidosOriginales,
                bloqueosProgramados);

        ParametrosAlgoritmo parametros =
                new ParametrosAlgoritmo(
                        cfg,
                        semillaAlgoritmo);

        FuncionObjetivo objetivo =
                new FuncionObjetivo(
                        cfg,
                        contexto);

        IAlgoritmoPlanificacion motor =
                fabrica.apply(
                        parametros,
                        objetivo);

        long t0 = System.nanoTime();

        Solucion solucion =
                motor.planificar(contexto);

        double tiempoMs =
                (System.nanoTime() - t0)
                        / 1_000_000.0;

        /*
         * Asegura que todos los componentes de F correspondan
         * exactamente a la solución final.
         */
        objetivo.calcular(solucion);

        int evaluaciones =
                motor.getConvergencia().isEmpty()
                        ? 0
                        : motor.getConvergencia()
                                .get(
                                        motor.getConvergencia().size()
                                                - 1)
                                .evaluaciones();

        return new Resultado(
                fecha,
                solucion,
                contexto,
                motor,
                tiempoMs,
                evaluaciones,
                contexto.getPedidos().size());
    }

    /**
     * Construye el contexto batch correspondiente a una jornada.
     *
     * Este método se usa tanto para la ejecución experimental como
     * para el warm-up, evitando que ambas rutas construyan contextos
     * de manera diferente.
     */
    public static ContextoPlanificacion crearContexto(
            ReglasPlanificacion reglas,
            PlanExperimento plan,
            BaseOperativa base,
            DatosArchivos archivos,
            LocalDate fecha,
            List<Pedido> pedidosOriginales,
            List<Bloqueo> bloqueosProgramados) {

        if (reglas == null
                || plan == null
                || base == null
                || archivos == null
                || fecha == null
                || pedidosOriginales == null
                || pedidosOriginales.isEmpty()
                || bloqueosProgramados == null) {

            throw new IllegalArgumentException(
                    "Datos incompletos para crear contexto de jornada");
        }

        LocalDateTime inicioJornada =
                fecha.atStartOfDay();

        /*
         * El experimento es batch diario con información completa de la demanda
         * sintética del día. Se conserva la llegada real de cada pedido para
         * calcular espera, entrega y deadline; únicamente se adelanta el instante
         * desde el que el planificador conoce el pedido.
         */
        List<Pedido> pedidos =
                prepararPedidosBatch(
                        pedidosOriginales,
                        inicioJornada);

        /*
         * El mantenimiento se determina para esta fecha concreta.
         */
        Set<String> mantenimiento =
                GeneradorEscenarios.mantenimientoEn(
                        plan,
                        archivos,
                        fecha);

        /*
         * No se agregan averías ni bajas artificiales.
         */
        List<Vehiculo> flota =
                GeneradorEscenarios.copiarFlota(
                        base,
                        mantenimiento,
                        Set.of());

        /*
         * Se utiliza el snapshot con mayor cantidad de bloqueos simultáneamente
         * activos. Esto preserva la presión vial sin convertir todos los eventos
         * del día en un único bloqueo permanente.
         */
        List<Bloqueo> bloqueosJornada =
                bloqueosPicoJornada(
                        bloqueosProgramados,
                        inicioJornada);

        Set<String> nodosBloqueados =
                pe.edu.pucp.sisrap.experimentacion.dominio.Escenario
                        .expandirNodosBloqueados(
                                bloqueosJornada);

        return new ContextoPlanificacion(
                pedidos,
                flota,
                base.almacenes(),
                inicioJornada,
                reglas,
                nodosBloqueados);
    }

    /**
     * Hace visible el pedido desde el inicio de la jornada sin modificar su
     * fecha real de llegada ni, por tanto, su fecha límite.
     */
    static List<Pedido> prepararPedidosBatch(
            List<Pedido> fuente,
            LocalDateTime inicioJornada) {

        return fuente.stream()
                .sorted(
                        Comparator
                                .comparing(Pedido::getFechaLlegada)
                                .thenComparing(Pedido::getIdPedido))
                .map(p -> {
                    /*
                     * Pedido nuevo del día: el experimento batch permite que el
                     * planificador lo conozca desde las 00:00.
                     *
                     * Pedido pendiente de una jornada anterior: ya era conocido,
                     * por lo que se conserva su disponibilidad original. Intentar
                     * moverla al inicio del día actual la haría posterior a su
                     * fecha real de llegada y sería conceptualmente incorrecto.
                     */
                    if (!p.getFechaDisponiblePlanificacion().isAfter(inicioJornada)) {
                        return p;
                    }
                    return p.disponibleDesde(inicioJornada);
                })
                .toList();
    }

    /**
     * Devuelve los bloqueos simultáneamente activos en el instante de máxima
     * concurrencia dentro de la jornada. Los bloqueos que cruzan medianoche se
     * consideran también al inicio del día.
     */
    public static List<Bloqueo> bloqueosPicoJornada(
            List<Bloqueo> programados,
            LocalDateTime inicioJornada) {

        LocalDateTime finJornada = inicioJornada.plusDays(1);

        List<Bloqueo> delDia = programados.stream()
                .filter(b -> b.inicio().isBefore(finJornada)
                        && b.fin().isAfter(inicioJornada))
                .toList();

        if (delDia.isEmpty()) return List.of();

        List<LocalDateTime> candidatos = new java.util.ArrayList<>();
        candidatos.add(inicioJornada);
        delDia.stream()
                .map(Bloqueo::inicio)
                .filter(t -> !t.isBefore(inicioJornada) && t.isBefore(finJornada))
                .sorted()
                .forEach(candidatos::add);

        LocalDateTime mejorInstante = inicioJornada;
        int mejorCantidad = -1;

        for (LocalDateTime candidato : candidatos) {
            int cantidad = 0;
            for (Bloqueo bloqueo : delDia) {
                boolean activo = !bloqueo.inicio().isAfter(candidato)
                        && bloqueo.fin().isAfter(candidato);
                if (activo) cantidad++;
            }

            if (cantidad > mejorCantidad) {
                mejorCantidad = cantidad;
                mejorInstante = candidato;
            }
        }

        LocalDateTime instantePico = mejorInstante;
        return delDia.stream()
                .filter(b -> !b.inicio().isAfter(instantePico)
                        && b.fin().isAfter(instantePico))
                .toList();
    }

}