package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;

import pe.edu.pucp.sisrap.experimentacion.dominio.BaseOperativa;
import pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo;
import pe.edu.pucp.sisrap.experimentacion.dominio.DatosArchivos;
import pe.edu.pucp.sisrap.experimentacion.dominio.Escenario;
import pe.edu.pucp.sisrap.experimentacion.dominio.EscenarioOperativo;
import pe.edu.pucp.sisrap.experimentacion.dominio.PlanExperimento;
import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
import pe.edu.pucp.sisrap.parametros.dominio.ParametrosAlgoritmo;
import pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.algoritmo.IAlgoritmoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;
import pe.edu.pucp.sisrap.planificador.dominio.objetivo.FuncionObjetivo;


public final class SimuladorColapso {
    private static final long SALTO_SEMILLA_DIA_ALGORITMO = 104_729L;
    private static final double NANOS_POR_HORA = 3_600_000_000_000.0;

    private SimuladorColapso() {
    }

    public record Resultado(
            Solucion solucionFinal,
            ContextoPlanificacion contextoFinal,
            IAlgoritmoPlanificacion motorFinal,
            double tiempoEjecucionTotalMs,
            int evaluacionesTotales,
            int tamanioUltimaJornada,
            boolean colapsoAlcanzado,
            Double tiempoColapsoHoras,
            LocalDateTime instanteColapso,
            int diasCompletados) {
    }

    public static Resultado simular(
            String algoritmo,
            BiFunction<ParametrosAlgoritmo, FuncionObjetivo, IAlgoritmoPlanificacion> fabrica,
            Configuracion cfg,
            ReglasPlanificacion reglas,
            PlanExperimento plan,
            BaseOperativa base,
            DatosArchivos archivos,
            Escenario escenario,
            long semillaAlgoritmoBase) {

        ModeloDemandaHistorica.Resumen demanda = ModeloDemandaHistorica.analizar(
                archivos,
                plan.desde());

        ModeloBloqueosHistoricos.Resumen bloqueosHistoricos = ModeloBloqueosHistoricos.analizar(
                archivos,
                demanda.desde(),
                demanda.hasta());

        LocalDate fechaInicio = demanda.hasta().plusDays(1);
        LocalDateTime inicioSimulacion = fechaInicio.atStartOfDay();
        int maxDias = cfg.entero("experimento.colapso.maxDias");

        Random randomDemanda = new Random(
                GeneradorEscenarios.semillaDemanda(
                        plan,
                        EscenarioOperativo.COLAPSO_LOGISTICO,
                        escenario.instancia()));

        Random randomBloqueos = new Random(
                GeneradorEscenarios.semillaBloqueos(
                        plan,
                        EscenarioOperativo.COLAPSO_LOGISTICO,
                        escenario.perfilPresion(),
                        escenario.instancia()));

        List<Bloqueo> bloqueosGenerados = new ArrayList<>();
        List<Pedido> pendientes = new ArrayList<>();

        double tiempoTotalMs = 0.0;
        int evaluacionesTotales = 0;

        Solucion ultimaSolucion = null;
        ContextoPlanificacion ultimoContexto = null;
        IAlgoritmoPlanificacion ultimoMotor = null;
        int tamanioUltimaJornada = 0;

        for (int dia = 0; dia < maxDias; dia++) {
            LocalDate fechaJornada = fechaInicio.plusDays(dia);
            LocalDateTime inicioJornada = fechaJornada.atStartOfDay();
            LocalDateTime finJornada = inicioJornada.plusDays(1);

            /*
             * Salvaguarda: un pedido no debería llegar a una nueva jornada con
             * el deadline ya vencido. Si ocurre, el colapso real fue exactamente
             * en ese deadline y no al comienzo de la nueva jornada.
             */
            LocalDateTime vencidoAntesDePlanificar = pendientes.stream()
                    .map(Pedido::getFechaLimite)
                    .filter(limite -> !limite.isAfter(inicioJornada))
                    .min(LocalDateTime::compareTo)
                    .orElse(null);

            if (vencidoAntesDePlanificar != null) {
                if (ultimaSolucion == null || ultimoContexto == null || ultimoMotor == null) {
                    throw new IllegalStateException(
                            "Se detectó un pedido vencido antes de ejecutar la primera jornada de colapso");
                }

                return resultadoColapso(
                        ultimaSolucion,
                        ultimoContexto,
                        ultimoMotor,
                        tiempoTotalMs,
                        evaluacionesTotales,
                        tamanioUltimaJornada,
                        inicioSimulacion,
                        vencidoAntesDePlanificar,
                        dia);
            }

            double lambda = plan.lambdaPedidos(escenario.perfilPresion())
                    + (double) plan.incrementoColapsoDiario() * dia;

            long idInicial = GeneradorEscenarios.idInicial(
                    EscenarioOperativo.COLAPSO_LOGISTICO,
                    escenario.perfilPresion(),
                    escenario.instancia(),
                    dia);

            List<Pedido> pedidosNuevos = ModeloDemandaHistorica.generarDia(
                    demanda,
                    lambda,
                    fechaJornada,
                    randomDemanda,
                    idInicial);

            /*
             * En cada jornada se vuelven a presentar al planificador los pedidos
             * pendientes del día anterior junto con la nueva demanda generada.
             */
            List<Pedido> pedidosJornada = combinarPedidos(
                    pendientes,
                    pedidosNuevos);

            bloqueosGenerados.addAll(ModeloBloqueosHistoricos.generarDia(
                    bloqueosHistoricos,
                    escenario.perfilPresion(),
                    fechaJornada,
                    randomBloqueos,
                    base.anchoKm(),
                    base.altoKm()));

            long semillaDiaAlgoritmo = Math.addExact(
                    semillaAlgoritmoBase,
                    Math.multiplyExact((long) dia, SALTO_SEMILLA_DIA_ALGORITMO));

            SimuladorJornada.Resultado resultadoDia = SimuladorJornada.ejecutar(
                    fabrica,
                    cfg,
                    reglas,
                    plan,
                    base,
                    archivos,
                    fechaJornada,
                    pedidosJornada,
                    bloqueosGenerados,
                    semillaDiaAlgoritmo);

            tiempoTotalMs += resultadoDia.tiempoEjecucionMs();
            evaluacionesTotales += resultadoDia.evaluaciones();

            ultimaSolucion = resultadoDia.solucion();
            ultimoContexto = resultadoDia.contexto();
            ultimoMotor = resultadoDia.motor();
            tamanioUltimaJornada = resultadoDia.tamanio();

            /*
             * Solo se declara colapso si el deadline incumplido ocurre dentro
             * de la jornada que realmente acabamos de simular.
             *
             * Un pedido no asignado cuyo deadline cae mañana NO provoca todavía
             * colapso: se conserva como pendiente y se vuelve a planificar.
             */
            LocalDateTime instanteFallo = primerDeadlineIncumplidoHasta(
                    ultimaSolucion,
                    finJornada);

            if (instanteFallo != null) {
                return resultadoColapso(
                        ultimaSolucion,
                        ultimoContexto,
                        ultimoMotor,
                        tiempoTotalMs,
                        evaluacionesTotales,
                        tamanioUltimaJornada,
                        inicioSimulacion,
                        instanteFallo,
                        dia);
            }

            /*
             * Se arrastran únicamente los pedidos que al finalizar la jornada
             * todavía requieren otra oportunidad de planificación y que siguen
             * teniendo un deadline futuro.
             */
            pendientes = pendientesParaSiguienteJornada(
                    ultimaSolucion,
                    ultimoContexto,
                    finJornada);
        }

        if (ultimaSolucion == null || ultimoContexto == null || ultimoMotor == null) {
            throw new IllegalStateException(
                    "No se pudo ejecutar ninguna jornada de colapso para " + escenario.id());
        }

        return new Resultado(
                ultimaSolucion,
                ultimoContexto,
                ultimoMotor,
                tiempoTotalMs,
                evaluacionesTotales,
                tamanioUltimaJornada,
                false,
                null,
                null,
                maxDias);
    }

    /**
     * Une pendientes y demanda nueva sin permitir IDs repetidos.
     */
    private static List<Pedido> combinarPedidos(
            List<Pedido> pendientes,
            List<Pedido> nuevos) {

        Map<Long, Pedido> porId = new LinkedHashMap<>();

        for (Pedido pedido : pendientes) {
            if (porId.putIfAbsent(pedido.getIdPedido(), pedido) != null) {
                throw new IllegalStateException(
                        "Pedido pendiente duplicado: " + pedido.getIdPedido());
            }
        }

        for (Pedido pedido : nuevos) {
            if (porId.putIfAbsent(pedido.getIdPedido(), pedido) != null) {
                throw new IllegalStateException(
                        "ID de pedido repetido entre pendientes y nueva demanda: "
                                + pedido.getIdPedido());
            }
        }

        return new ArrayList<>(porId.values());
    }

    /**
     * Devuelve el primer deadline realmente incumplido hasta el cierre de la
     * jornada. Los deadlines posteriores se dejan para jornadas futuras.
     */
    private static LocalDateTime primerDeadlineIncumplidoHasta(
            Solucion solucion,
            LocalDateTime finJornada) {

        LocalDateTime primero = solucion.getPedidosNoAsignados().stream()
                .map(Pedido::getFechaLimite)
                .filter(limite -> !limite.isAfter(finJornada))
                .min(LocalDateTime::compareTo)
                .orElse(null);

        for (var ruta : solucion.getRutas()) {
            for (int i = 0; i < ruta.getSecuenciaPedidos().size(); i++) {
                if (ruta.retrasoDe(i) <= 0) {
                    continue;
                }

                LocalDateTime limite = ruta.getSecuenciaPedidos()
                        .get(i)
                        .getFechaLimite();

                if (limite.isAfter(finJornada)) {
                    continue;
                }

                if (primero == null || limite.isBefore(primero)) {
                    primero = limite;
                }
            }
        }

        return primero;
    }

    /**
     * Construye la cola de pedidos que deben volver a planificarse en la
     * siguiente jornada.
     *
     * 1) Todo pedido no asignado cuyo deadline todavía está vigente.
     * 2) Una entrega asignada pero prevista después del cierre de la jornada.
     * 3) Una entrega que ya se sabe tardía, pero cuyo deadline ocurre después
     *    del cierre actual; se le da una nueva oportunidad al día siguiente.
     */
    private static List<Pedido> pendientesParaSiguienteJornada(
            Solucion solucion,
            ContextoPlanificacion contexto,
            LocalDateTime finJornada) {

        Map<Long, Pedido> pendientes = new LinkedHashMap<>();

        for (Pedido pedido : solucion.getPedidosNoAsignados()) {
            if (pedido.getFechaLimite().isAfter(finJornada)) {
                pendientes.put(pedido.getIdPedido(), pedido);
            }
        }

        for (var ruta : solucion.getRutas()) {
            for (int i = 0; i < ruta.getSecuenciaPedidos().size(); i++) {
                Pedido pedido = ruta.getSecuenciaPedidos().get(i);

                double horasEntrega = ruta.horaEntregaDe(i);
                LocalDateTime instanteEntrega = sumarHoras(
                        contexto.instante(),
                        horasEntrega);

                boolean terminaDespuesDeLaJornada = instanteEntrega.isAfter(finJornada);
                boolean entregaTardiaConDeadlineFuturo = ruta.retrasoDe(i) > 0
                        && pedido.getFechaLimite().isAfter(finJornada);

                if ((terminaDespuesDeLaJornada || entregaTardiaConDeadlineFuturo)
                        && pedido.getFechaLimite().isAfter(finJornada)) {
                    pendientes.putIfAbsent(pedido.getIdPedido(), pedido);
                }
            }
        }

        return new ArrayList<>(pendientes.values());
    }

    private static LocalDateTime sumarHoras(
            LocalDateTime inicio,
            double horas) {

        if (!Double.isFinite(horas) || horas < 0) {
            return LocalDateTime.MAX;
        }

        long nanos = Math.round(horas * NANOS_POR_HORA);
        return inicio.plusNanos(nanos);
    }

    private static Resultado resultadoColapso(
            Solucion solucion,
            ContextoPlanificacion contexto,
            IAlgoritmoPlanificacion motor,
            double tiempoTotalMs,
            int evaluacionesTotales,
            int tamanioUltimaJornada,
            LocalDateTime inicioSimulacion,
            LocalDateTime instanteFallo,
            int diasCompletados) {

        double horas = Duration.between(
                inicioSimulacion,
                instanteFallo)
                .toNanos() / NANOS_POR_HORA;

        if (horas < 0) {
            throw new IllegalStateException(
                    "El instante de colapso no puede ser anterior al inicio de la simulación");
        }

        return new Resultado(
                solucion,
                contexto,
                motor,
                tiempoTotalMs,
                evaluacionesTotales,
                tamanioUltimaJornada,
                true,
                horas,
                instanteFallo,
                diasCompletados);
    }
}