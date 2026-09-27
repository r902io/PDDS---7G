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

/**
 * Ejecuta una jornada como un problema batch diario.
 *
 * Todos los pedidos generados para una jornada se consideran disponibles
 * al inicio del día. Se conservan cantidad, prioridad, ubicación y horas
 * comprometidas de entrega.
 *
 * Los bloqueos cuyo intervalo intersecta la jornada se consideran durante
 * la planificación de esa jornada.
 */
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
         * El planificador es batch.
         *
         * Por ello todos los pedidos del día se liberan a las 00:00
         * preservando las horas de plazo.
         */
        List<Pedido> pedidos =
                normalizarPedidos(
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
         * Se toman todos los bloqueos cuyo intervalo intersecta
         * la jornada.
         */
        List<Bloqueo> bloqueosJornada =
                bloqueosQueIntersectanJornada(
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
     * Convierte los pedidos generados del día al modelo batch.
     *
     * El nuevo deadline se obtiene automáticamente mediante
     * Pedido#getFechaLimite():
     *
     * fechaLlegadaNormalizada + horasLimite.
     */
    static List<Pedido> normalizarPedidos(
            List<Pedido> fuente,
            LocalDateTime inicioJornada) {

        return fuente.stream()
                .sorted(
                        Comparator
                                .comparing(
                                        Pedido::getFechaLlegada)
                                .thenComparing(
                                        Pedido::getIdPedido))
                .map(p ->
                        new Pedido(
                                p.getIdPedido(),
                                p.getIdCliente(),
                                p.getCantidadQq(),
                                p.getPrioridad(),
                                p.getUbicacion(),
                                inicioJornada,
                                p.getHorasLimite()))
                .toList();
    }

    /**
     * Un bloqueo pertenece a una jornada cuando existe intersección
     * entre su intervalo temporal y [inicioDia, inicioDia + 24h).
     */
    static List<Bloqueo> bloqueosQueIntersectanJornada(
            List<Bloqueo> programados,
            LocalDateTime inicioJornada) {

        LocalDateTime finJornada =
                inicioJornada.plusDays(1);

        return programados.stream()
                .filter(b ->
                        b.inicio().isBefore(finJornada)
                                && b.fin()
                                        .isAfter(inicioJornada))
                .toList();
    }
}