package pe.edu.pucp.sisrap.experimentacion.dominio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Corrida;
import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Estadistica;

/** Todo lo recopilado en una ejecución del experimento, listo para exportar. */
public record ResultadoExperimento(PlanExperimento plan,
                                   BaseOperativa base,
                                   DatosArchivos archivos,
                                   List<Escenario> escenarios,
                                   List<CorridaRegistrada> corridas,
                                   List<ResumenGrupo> resumenes,
                                   List<Comparacion> comparaciones,
                                   List<String> advertencias,
                                   LocalDateTime inicio,
                                   LocalDateTime fin) {

    public record CorridaRegistrada(String variante,
                                    String escenario,
                                    int instancia,
                                    int repeticion,
                                    int evaluaciones,
                                    boolean verificada,
                                    Corrida corrida) {}

    /** Estadística de una condición experimental concreta. */
    public record ResumenGrupo(String variante,
                               String escenario,
                               String escenarioOperativo,
                               String perfilPresion,
                               int instancia,
                               String algoritmo,
                               int tamanio,
                               int corridas,
                               double tasaFactibles,
                               Map<String, Estadistica> metricas) {}

    /** Comparación pareada de dos algoritmos sobre la misma instancia y repetición. */
    public record Comparacion(String variante,
                              String escenario,
                              String escenarioOperativo,
                              String perfilPresion,
                              int instancia,
                              int tamanio,
                              String algoritmoA,
                              String algoritmoB,
                              int pares,
                              double mediaObjetivoA,
                              double mediaObjetivoB,
                              int victoriasA,
                              int victoriasB,
                              int empates,
                              double pValor,
                              double tamanoEfecto,
                              String interpretacionEfecto,
                              String veredicto,
                              double mediaEvaluacionesA,
                              double mediaEvaluacionesB,
                              double mediaTiempoMsA,
                              double mediaTiempoMsB,
                              double pValorPresupuestoIgual,
                              String veredictoPresupuestoIgual,
                              Map<String, MetricaSecundaria> metricasSecundarias) {

        public record MetricaSecundaria(double pValor,
                                        double pValorHolm,
                                        double tamanoEfecto,
                                        String veredicto) {}
    }
}