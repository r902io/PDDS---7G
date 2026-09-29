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

/**
 * Simulación batch diaria hasta el primer deadline incumplido.
 *
 * Diseño mejorado:
 * - NORMAL, ALTA y CRITICA parten de la misma demanda esperada P50;
 * - solo cambia la tasa de crecimiento compuesto (g, 2g, 3g);
 * - los tres perfiles comparten la misma realización aleatoria máxima de demanda;
 * - los bloqueos se mantienen en P50 y con la misma semilla para aislar el efecto
 *   del crecimiento de demanda;
 * - los pedidos pendientes se arrastran a jornadas posteriores mientras su deadline
 *   siga vigente;
 * - se registra el pedido concreto que provoca el colapso.
 */
public final class SimuladorColapso {
    private static final long SALTO_SEMILLA_DIA_ALGORITMO = 104_729L;
    private static final double NANOS_POR_HORA = 3_600_000_000_000.0;

    private SimuladorColapso() {}

    private record EventoColapso(Pedido pedido, LocalDateTime instante) {
        private EventoColapso {
            if (pedido == null || instante == null)
                throw new IllegalArgumentException("Evento de colapso incompleto");
        }
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
            int diasCompletados,
            Long pedidoCausaColapso,
            String prioridadCausaColapso,
            LocalDateTime deadlineCausaColapso,
            Integer noAsignadosAlColapso,
            Double lambdaAlColapso) {
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

        // La semilla externa NO depende del perfil. El mismo Nxx recibe el mismo mundo base.
        Random randomDemanda = new Random(
                GeneradorEscenarios.semillaDemanda(
                        plan,
                        EscenarioOperativo.COLAPSO_LOGISTICO,
                        escenario.instancia()));

        Random randomBloqueos = new Random(
                GeneradorEscenarios.semillaBloqueos(
                        plan,
                        EscenarioOperativo.COLAPSO_LOGISTICO,
                        escenario.instancia()));

        List<Bloqueo> bloqueosGenerados = new ArrayList<>();
        List<Pedido> pendientes = new ArrayList<>();

        double tiempoTotalMs = 0.0;
        int evaluacionesTotales = 0;

        Solucion ultimaSolucion = null;
        ContextoPlanificacion ultimoContexto = null;
        IAlgoritmoPlanificacion ultimoMotor = null;
        int tamanioUltimaJornada = 0;
        double ultimaLambda = plan.demandaP50Diaria();

        for (int dia = 0; dia < maxDias; dia++) {
            LocalDate fechaJornada = fechaInicio.plusDays(dia);
            LocalDateTime inicioJornada = fechaJornada.atStartOfDay();
            LocalDateTime finJornada = inicioJornada.plusDays(1);

            EventoColapso vencidoAntesDePlanificar = primerPendienteVencido(
                    pendientes,
                    inicioJornada);

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
                        dia,
                        ultimaLambda);
            }

            double lambda = plan.lambdaColapso(
                    escenario.perfilPresion(),
                    dia);
            double lambdaMaxima = plan.lambdaColapsoMaxima(dia);
            ultimaLambda = lambda;

            long idInicial = GeneradorEscenarios.idInicial(
                    EscenarioOperativo.COLAPSO_LOGISTICO,
                    escenario.instancia(),
                    dia);

            List<Pedido> pedidosNuevos = ModeloDemandaHistorica.generarDiaAcoplado(
                    demanda,
                    lambda,
                    lambdaMaxima,
                    fechaJornada,
                    randomDemanda,
                    idInicial);

            List<Pedido> pedidosJornada = combinarPedidos(
                    pendientes,
                    pedidosNuevos);

            // En colapso los bloqueos no aumentan con el perfil: P50 para todos.
            bloqueosGenerados.addAll(ModeloBloqueosHistoricos.generarDia(
                    bloqueosHistoricos,
                    bloqueosHistoricos.p50(),
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

            EventoColapso evento = primerDeadlineIncumplidoHasta(
                    ultimaSolucion,
                    finJornada);

            if (evento != null) {
                return resultadoColapso(
                        ultimaSolucion,
                        ultimoContexto,
                        ultimoMotor,
                        tiempoTotalMs,
                        evaluacionesTotales,
                        tamanioUltimaJornada,
                        inicioSimulacion,
                        evento,
                        dia,
                        lambda);
            }

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
                maxDias,
                null,
                null,
                null,
                null,
                null);
    }

    private static EventoColapso primerPendienteVencido(
            List<Pedido> pendientes,
            LocalDateTime instante) {
        Pedido causa = pendientes.stream()
                .filter(p -> !p.getFechaLimite().isAfter(instante))
                .min(java.util.Comparator
                        .comparing(Pedido::getFechaLimite)
                        .thenComparing(Pedido::getIdPedido))
                .orElse(null);
        return causa == null ? null : new EventoColapso(causa, causa.getFechaLimite());
    }

    /** Une pendientes y demanda nueva sin permitir IDs repetidos. */
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
     * Devuelve el primer pedido cuyo deadline fue incumplido dentro de la jornada.
     * Los deadlines posteriores se arrastran a jornadas futuras.
     */
    private static EventoColapso primerDeadlineIncumplidoHasta(
            Solucion solucion,
            LocalDateTime finJornada) {

        Pedido causa = solucion.getPedidosNoAsignados().stream()
                .filter(p -> !p.getFechaLimite().isAfter(finJornada))
                .min(java.util.Comparator
                        .comparing(Pedido::getFechaLimite)
                        .thenComparing(Pedido::getIdPedido))
                .orElse(null);

        for (var ruta : solucion.getRutas()) {
            for (int i = 0; i < ruta.getSecuenciaPedidos().size(); i++) {
                if (ruta.retrasoDe(i) <= 0) continue;

                Pedido pedido = ruta.getSecuenciaPedidos().get(i);
                LocalDateTime limite = pedido.getFechaLimite();
                if (limite.isAfter(finJornada)) continue;

                if (causa == null
                        || limite.isBefore(causa.getFechaLimite())
                        || (limite.equals(causa.getFechaLimite())
                            && pedido.getIdPedido() < causa.getIdPedido())) {
                    causa = pedido;
                }
            }
        }

        return causa == null ? null : new EventoColapso(causa, causa.getFechaLimite());
    }

    /** Construye la cola de pedidos que deben volver a planificarse al día siguiente. */
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

        if (!Double.isFinite(horas) || horas < 0) return LocalDateTime.MAX;
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
            EventoColapso evento,
            int diasCompletados,
            double lambdaAlColapso) {

        double horas = Duration.between(
                inicioSimulacion,
                evento.instante())
                .toNanos() / NANOS_POR_HORA;

        if (horas < 0) {
            throw new IllegalStateException(
                    "El instante de colapso no puede ser anterior al inicio de la simulación");
        }

        Pedido causa = evento.pedido();
        return new Resultado(
                solucion,
                contexto,
                motor,
                tiempoTotalMs,
                evaluacionesTotales,
                tamanioUltimaJornada,
                true,
                horas,
                evento.instante(),
                diasCompletados,
                causa.getIdPedido(),
                causa.getPrioridad().name(),
                causa.getFechaLimite(),
                solucion.getPedidosNoAsignados().size(),
                lambdaAlColapso);
    }
}