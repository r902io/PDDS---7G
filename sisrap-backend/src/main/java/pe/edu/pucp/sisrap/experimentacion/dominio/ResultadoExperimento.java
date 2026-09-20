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
                                   List<String> advertencias, LocalDateTime inicio, LocalDateTime fin) {

    /** Una corrida con el contexto que la produjo. */
    public record CorridaRegistrada(String variante, String escenario, int instancia, int repeticion,
                                    int evaluaciones, boolean verificada, Corrida corrida) {}

    /** Estadística de un grupo variante x algoritmo x tamaño (todas las instancias y repeticiones). */
    public record ResumenGrupo(String variante, String algoritmo, int tamanio, int corridas,
                               double tasaFactibles, Map<String, Estadistica> metricas) {}

    /**
     * Comparación pareada de dos algoritmos: mismas instancias y misma semilla.
     * Diferencias en F se prueban con Wilcoxon (aprox. normal). "Presupuesto igual" compara ambos
     * al mismo número de evaluaciones de la función objetivo.
     */
    public record Comparacion(String variante, int tamanio, String algoritmoA, String algoritmoB, int pares,
                              double mediaObjetivoA, double mediaObjetivoB, int victoriasA, int victoriasB, int empates,
                              double pValor, String veredicto,
                              double mediaEvaluacionesA, double mediaEvaluacionesB,
                              double mediaTiempoMsA, double mediaTiempoMsB,
                              double pValorPresupuestoIgual, String veredictoPresupuestoIgual) {}
}