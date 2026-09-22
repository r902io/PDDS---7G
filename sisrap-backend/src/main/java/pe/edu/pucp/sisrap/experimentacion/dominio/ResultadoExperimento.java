package pe.edu.pucp.sisrap.experimentacion.dominio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Corrida;
import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Estadistica;

/** Todo lo recopilado en una ejecución del experimento, listo para exportar. */
public record ResultadoExperimento(PlanExperimento plan, BaseOperativa base, DatosArchivos archivos,
                                   List<Escenario> escenarios, List<CorridaRegistrada> corridas,
                                   List<ResumenGrupo> resumenes, List<Comparacion> comparaciones,
                                   List<PuntoColapso> puntosColapso, List<String> advertencias,
                                   LocalDateTime inicio, LocalDateTime fin) {

    public ResultadoExperimento(PlanExperimento plan, BaseOperativa base, DatosArchivos archivos,
                                List<Escenario> escenarios, List<CorridaRegistrada> corridas,
                                List<ResumenGrupo> resumenes, List<Comparacion> comparaciones,
                                List<String> advertencias, LocalDateTime inicio, LocalDateTime fin) {
        this(plan, base, archivos, escenarios, corridas, resumenes, comparaciones, List.of(), advertencias, inicio, fin);
    }

    /** Una corrida con el contexto que la produjo. {@code instancia} es el nivel/ordinal del escenario (Escenario#nivel). */
    public record CorridaRegistrada(String variante, String escenario, int instancia, int repeticion,
                                    int evaluaciones, boolean verificada, Corrida corrida) {}

    /**
     * Estadística de una condición experimental concreta: variante x escenario x algoritmo.
     * No se mezclan perfiles de presión, escenarios operativos ni niveles aunque coincidan en tamaño.
     */
    public record ResumenGrupo(String variante, String escenario, String escenarioOperativo, String perfilPresion,
                               int nivel, String algoritmo, int tamanio, int corridas,
                               double tasaFactibles, Map<String, Estadistica> metricas) {}

    /**
     * Comparación pareada de dos algoritmos: mismas instancias y misma semilla (sección 3.2 del informe
     * "Diseño de Experimento"). {@code pValor}/{@code veredicto} son sobre el valor de F (objetivo);
     * {@code tamanoEfecto} es r = |Z|/sqrt(N) (sección 4.1). "Presupuesto igual" compara ambos al mismo
     * número de evaluaciones de la función objetivo. {@code metricasSecundarias} contiene, además, la
     * comparación pareada de la tasa de cumplimiento de plazos (métrica primaria de decisión, sección 3.3)
     * y de retraso, costo, tiempo y pedidos no asignados, con sus p-valores ajustados por el método de
     * Holm entre sí (sección 4.1).
     */
    public record Comparacion(String variante, String escenario, String escenarioOperativo, String perfilPresion,
                              int nivel, int tamanio, String algoritmoA, String algoritmoB, int pares,
                              double mediaObjetivoA, double mediaObjetivoB, int victoriasA, int victoriasB, int empates,
                              double pValor, double tamanoEfecto, String interpretacionEfecto, String veredicto,
                              double mediaEvaluacionesA, double mediaEvaluacionesB,
                              double mediaTiempoMsA, double mediaTiempoMsB,
                              double pValorPresupuestoIgual, String veredictoPresupuestoIgual,
                              Map<String, MetricaSecundaria> metricasSecundarias) {
        public record MetricaSecundaria(double pValor, double pValorHolm, double tamanoEfecto, String veredicto) {}
    }

    /**
     * Primer nivel de COLAPSO_LOGISTICO en el que más del 50% de las repeticiones dejan pedidos sin
     * atender (N(S) > 0), por (variante, algoritmo), según la sección 3.4 del informe "Diseño de
     * Experimento". {@code nivelColapso} es "SIN_COLAPSO_OBSERVADO" si ningún nivel evaluado lo alcanza.
     */
    public record PuntoColapso(String variante, String algoritmo, String nivelColapso, double tasaNoAtendidosPct) {}
}
