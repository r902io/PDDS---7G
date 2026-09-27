package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
 * Simulación del escenario COLAPSO_LOGISTICO.
 *
 * La primera jornada parte del percentil del perfil:
 * NORMAL=P50, ALTA=P75, CRITICA=P90.
 * Después, la cantidad esperada aumenta cada día en (P75-P50) pedidos.
 *
 * La demanda y los bloqueos de N01/N02/N03 quedan fijos entre repeticiones;
 * solamente cambia la semilla interna del algoritmo.
 *
 * Cada día se resuelve con la misma aproximación batch de SimuladorJornada.
 * La simulación termina cuando la solución de una jornada contiene al menos un
 * pedido que ya no puede cumplir su deadline.
 */
public final class SimuladorColapso {
    private static final long SALTO_SEMILLA_DIA_ALGORITMO = 104_729L;

    private SimuladorColapso() {}

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
            int diasCompletados) {}

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

        double tiempoTotalMs = 0.0;
        int evaluacionesTotales = 0;

        Solucion ultimaSolucion = null;
        ContextoPlanificacion ultimoContexto = null;
        IAlgoritmoPlanificacion ultimoMotor = null;
        int tamanioUltimaJornada = 0;

        for (int dia = 0; dia < maxDias; dia++) {
            LocalDate fechaJornada = fechaInicio.plusDays(dia);

            double lambda = plan.lambdaPedidos(escenario.perfilPresion())
                    + (double) plan.incrementoColapsoDiario() * dia;

            long idInicial = GeneradorEscenarios.idInicial(
                    EscenarioOperativo.COLAPSO_LOGISTICO,
                    escenario.perfilPresion(),
                    escenario.instancia(),
                    dia);

            List<Pedido> pedidosJornada = ModeloDemandaHistorica.generarDia(
                    demanda,
                    lambda,
                    fechaJornada,
                    randomDemanda,
                    idInicial);

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

            LocalDateTime instanteFallo = primerDeadlineIncumplido(ultimaSolucion);
            if (instanteFallo != null) {
                double horas = Duration.between(inicioSimulacion, instanteFallo)
                        .toNanos() / 3_600_000_000_000.0;

                return new Resultado(
                        ultimaSolucion,
                        ultimoContexto,
                        ultimoMotor,
                        tiempoTotalMs,
                        evaluacionesTotales,
                        tamanioUltimaJornada,
                        true,
                        horas,
                        instanteFallo,
                        dia);
            }
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

    /** Devuelve el deadline más temprano que la solución no logra cumplir. */
    private static LocalDateTime primerDeadlineIncumplido(Solucion solucion) {
        LocalDateTime primero = solucion.getPedidosNoAsignados().stream()
                .map(Pedido::getFechaLimite)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        for (var ruta : solucion.getRutas()) {
            for (int i = 0; i < ruta.getSecuenciaPedidos().size(); i++) {
                if (ruta.retrasoDe(i) <= 0) continue;

                LocalDateTime limite = ruta.getSecuenciaPedidos()
                        .get(i)
                        .getFechaLimite();

                if (primero == null || limite.isBefore(primero)) {
                    primero = limite;
                }
            }
        }

        return primero;
    }
}