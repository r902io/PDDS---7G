package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import pe.edu.pucp.sisrap.experimentacion.dominio.BaseOperativa;
import pe.edu.pucp.sisrap.experimentacion.dominio.DatosArchivos;
import pe.edu.pucp.sisrap.experimentacion.dominio.Escenario;
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
 * Ejecuta SIMULACION_CINCO_DIAS como cinco jornadas batch consecutivas.
 *
 * Cada día constituye un nuevo ciclo de planificación. Esto permite reutilizar
 * la flota al día siguiente, aplicar el mantenimiento correspondiente a cada
 * fecha y evitar comparar toda la demanda de cinco días contra una sola salida
 * de la flota.
 */
public final class SimuladorCincoDias {
    private static final int DIAS = 5;
    private static final long SALTO_SEMILLA_DIA_ALGORITMO = 104_729L;

    private SimuladorCincoDias() {}

    public record Jornada(
            int numeroDia,
            LocalDate fecha,
            Solucion solucion,
            ContextoPlanificacion contexto,
            IAlgoritmoPlanificacion motor,
            double tiempoEjecucionMs,
            int evaluaciones) {}

    public record Resultado(
            List<Jornada> jornadas,
            double tiempoEjecucionTotalMs,
            int evaluacionesTotales) {
        public Resultado {
            jornadas = List.copyOf(jornadas);
            if (jornadas.size() != DIAS) {
                throw new IllegalArgumentException(
                        "SIMULACION_CINCO_DIAS requiere exactamente " + DIAS + " jornadas");
            }
        }
    }

    public static Resultado simular(
            BiFunction<ParametrosAlgoritmo, FuncionObjetivo, IAlgoritmoPlanificacion> fabrica,
            Configuracion cfg,
            ReglasPlanificacion reglas,
            PlanExperimento plan,
            BaseOperativa base,
            DatosArchivos archivos,
            Escenario escenario,
            long semillaAlgoritmoBase) {

        if (fabrica == null || cfg == null || reglas == null || plan == null
                || base == null || archivos == null || escenario == null) {
            throw new IllegalArgumentException("Simulación de cinco días incompleta");
        }

        Map<LocalDate, List<Pedido>> pedidosPorDia = escenario.pedidos().stream()
                .collect(Collectors.groupingBy(
                        p -> p.getFechaLlegada().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()));

        if (pedidosPorDia.size() != DIAS) {
            throw new IllegalStateException(
                    escenario.id() + " contiene " + pedidosPorDia.size()
                            + " días de pedidos; se esperaban " + DIAS);
        }

        List<Jornada> jornadas = new ArrayList<>(DIAS);
        double tiempoTotalMs = 0.0;
        int evaluacionesTotales = 0;
        int indiceDia = 0;

        for (var entrada : pedidosPorDia.entrySet()) {
            LocalDate fecha = entrada.getKey();
            long semillaDia = Math.addExact(
                    semillaAlgoritmoBase,
                    Math.multiplyExact((long) indiceDia, SALTO_SEMILLA_DIA_ALGORITMO));

            SimuladorJornada.Resultado resultado = SimuladorJornada.ejecutar(
                    fabrica,
                    cfg,
                    reglas,
                    plan,
                    base,
                    archivos,
                    fecha,
                    entrada.getValue(),
                    escenario.bloqueosProgramados(),
                    semillaDia);

            jornadas.add(new Jornada(
                    indiceDia + 1,
                    fecha,
                    resultado.solucion(),
                    resultado.contexto(),
                    resultado.motor(),
                    resultado.tiempoEjecucionMs(),
                    resultado.evaluaciones()));

            tiempoTotalMs += resultado.tiempoEjecucionMs();
            evaluacionesTotales += resultado.evaluaciones();
            indiceDia++;
        }

        return new Resultado(
                jornadas,
                tiempoTotalMs,
                evaluacionesTotales);
    }
}