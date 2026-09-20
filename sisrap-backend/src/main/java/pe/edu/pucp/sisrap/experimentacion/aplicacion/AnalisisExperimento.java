package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Estadistica;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.Comparacion;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.CorridaRegistrada;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.ResumenGrupo;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.PuntoConvergencia;

/** Resúmenes por grupo y comparación pareada entre algoritmos. */
public final class AnalisisExperimento {
    public static final double NIVEL_SIGNIFICANCIA = 0.05;
    public static final int MINIMO_PARES = 6;
    public static final String SIN_DIFERENCIA = "SIN_DIFERENCIA_SIGNIFICATIVA";
    public static final String MUESTRA_INSUFICIENTE = "MUESTRA_INSUFICIENTE";

    private AnalisisExperimento() {}

    public static List<ResumenGrupo> resumir(List<CorridaRegistrada> corridas) {
        Map<String, List<CorridaRegistrada>> grupos = new LinkedHashMap<>();
        for (var c : corridas)
            grupos.computeIfAbsent(c.variante() + "|" + c.corrida().algoritmo() + "|" + c.corrida().tamanio(), k -> new ArrayList<>()).add(c);
        List<ResumenGrupo> salida = new ArrayList<>();
        for (var grupo : grupos.values()) {
            var primera = grupo.get(0);
            Map<String, Estadistica> m = new LinkedHashMap<>();
            m.put("objetivo", estadistica(grupo, c -> c.corrida().objetivo(), false));
            m.put("tiempoMs", estadistica(grupo, c -> c.corrida().tiempoMs(), false));
            m.put("evaluaciones", estadistica(grupo, c -> c.evaluaciones(), false));
            m.put("cumplimientoPct", estadistica(grupo, c -> c.corrida().cumplimiento(), true));
            var prioritarios = grupo.stream().filter(c -> c.corrida().cumplimientoPrioritarios() != null).toList();
            if (!prioritarios.isEmpty())
                m.put("cumplimientoPrioritariosPct", estadistica(prioritarios, c -> c.corrida().cumplimientoPrioritarios(), true));
            m.put("retrasoHoras", estadistica(grupo, c -> c.corrida().r(), false));
            m.put("tiempoAtencionHoras", estadistica(grupo, c -> c.corrida().t(), false));
            m.put("noAsignados", estadistica(grupo, c -> c.corrida().n(), false));
            m.put("costo", estadistica(grupo, c -> c.corrida().costo(), false));
            m.put("distanciaKm", estadistica(grupo, c -> c.corrida().distancia(), false));
            m.put("utilizacionPct", estadistica(grupo, c -> c.corrida().utilizacion(), true));
            double factibles = grupo.stream().filter(c -> c.corrida().factible()).count();
            salida.add(new ResumenGrupo(primera.variante(), primera.corrida().algoritmo(), primera.corrida().tamanio(),
                    grupo.size(), 100.0 * factibles / grupo.size(), m));
        }
        return List.copyOf(salida);
    }

    private static Estadistica estadistica(List<CorridaRegistrada> filas, ToDoubleFunction<CorridaRegistrada> f, boolean maximizar) {
        return MedicionCorridas.estadistica(filas.stream().mapToDouble(f).toArray(), maximizar);
    }

    /** Compara cada par de algoritmos por variante y tamaño, emparejando (instancia, repetición). */
    public static List<Comparacion> comparar(List<CorridaRegistrada> corridas, List<String> algoritmos) {
        Map<String, CorridaRegistrada> indice = new LinkedHashMap<>();
        for (var c : corridas)
            indice.put(clave(c.variante(), c.corrida().algoritmo(), c.escenario(), c.repeticion()), c);
        List<String> variantes = corridas.stream().map(CorridaRegistrada::variante).distinct().toList();
        List<Integer> tamanios = corridas.stream().map(c -> c.corrida().tamanio()).distinct().sorted().toList();
        List<Comparacion> salida = new ArrayList<>();
        for (String variante : variantes) {
            for (int tamanio : tamanios) {
                for (int i = 0; i < algoritmos.size(); i++) {
                    for (int j = i + 1; j < algoritmos.size(); j++) {
                        List<CorridaRegistrada[]> pares = new ArrayList<>();
                        for (var a : corridas) {
                            if (!a.variante().equals(variante) || a.corrida().tamanio() != tamanio
                                    || !a.corrida().algoritmo().equals(algoritmos.get(i))) continue;
                            var b = indice.get(clave(variante, algoritmos.get(j), a.escenario(), a.repeticion()));
                            if (b != null) pares.add(new CorridaRegistrada[] {a, b});
                        }
                        if (!pares.isEmpty()) salida.add(comparar(variante, tamanio, algoritmos.get(i), algoritmos.get(j), pares));
                    }
                }
            }
        }
        return List.copyOf(salida);
    }

    private static Comparacion comparar(String variante, int tamanio, String algoritmoA, String algoritmoB,
                                        List<CorridaRegistrada[]> pares) {
        int n = pares.size(), victoriasA = 0, victoriasB = 0;
        double[] diferencia = new double[n], diferenciaPresupuestoIgual = new double[n];
        double sumaA = 0, sumaB = 0, evalA = 0, evalB = 0, msA = 0, msB = 0;
        for (int k = 0; k < n; k++) {
            var a = pares.get(k)[0];
            var b = pares.get(k)[1];
            double fa = a.corrida().objetivo(), fb = b.corrida().objetivo();
            diferencia[k] = fa - fb;
            if (fa < fb) victoriasA++;
            else if (fb < fa) victoriasB++;
            int presupuesto = Math.min(a.evaluaciones(), b.evaluaciones());
            diferenciaPresupuestoIgual[k] = mejorHasta(a.corrida().convergencia(), presupuesto)
                    - mejorHasta(b.corrida().convergencia(), presupuesto);
            sumaA += fa; sumaB += fb; evalA += a.evaluaciones(); evalB += b.evaluaciones();
            msA += a.corrida().tiempoMs(); msB += b.corrida().tiempoMs();
        }
        double p = wilcoxon(diferencia), pIgual = wilcoxon(diferenciaPresupuestoIgual);
        return new Comparacion(variante, tamanio, algoritmoA, algoritmoB, n, sumaA / n, sumaB / n, victoriasA, victoriasB,
                n - victoriasA - victoriasB, p, veredicto(p, n, sumaA - sumaB, algoritmoA, algoritmoB),
                evalA / n, evalB / n, msA / n, msB / n, pIgual,
                veredicto(pIgual, n, Arrays.stream(diferenciaPresupuestoIgual).sum(), algoritmoA, algoritmoB));
    }

    private static String veredicto(double p, int pares, double diferenciaSuma, String a, String b) {
        if (pares < MINIMO_PARES || Double.isNaN(p)) return pares < MINIMO_PARES ? MUESTRA_INSUFICIENTE : SIN_DIFERENCIA;
        if (p >= NIVEL_SIGNIFICANCIA || diferenciaSuma == 0) return SIN_DIFERENCIA;
        return "GANA_" + (diferenciaSuma < 0 ? a : b);
    }

    /** Mejor objetivo encontrado usando a lo sumo {@code presupuesto} evaluaciones. */
    static double mejorHasta(List<PuntoConvergencia> traza, int presupuesto) {
        double mejor = traza.get(0).mejorObjetivo();
        for (var punto : traza) {
            if (punto.evaluaciones() > presupuesto) break;
            mejor = punto.mejorObjetivo();
        }
        return mejor;
    }

    private static String clave(String variante, String algoritmo, String escenario, int repeticion) {
        return variante + "|" + algoritmo + "|" + escenario + "|" + repeticion;
    }

    /**
     * Prueba de rangos con signo de Wilcoxon, dos colas. Exacta cuando hay como máximo 50 diferencias no nulas
     * y ninguna empata en valor absoluto; en otro caso, aproximación normal con corrección por empates y
     * por continuidad. Devuelve NaN si no queda ninguna diferencia distinta de cero.
     */
    static double wilcoxon(double[] diferencias) {
        double[] d = Arrays.stream(diferencias).filter(x -> x != 0).toArray();
        int n = d.length;
        if (n == 0) return Double.NaN;
        Integer[] orden = new Integer[n];
        for (int i = 0; i < n; i++) orden[i] = i;
        Arrays.sort(orden, Comparator.comparingDouble(i -> Math.abs(d[i])));
        double[] rango = new double[n];
        double correccionEmpates = 0;
        boolean hayEmpates = false;
        for (int i = 0; i < n; ) {
            int j = i;
            while (j + 1 < n && Math.abs(d[orden[j + 1]]) == Math.abs(d[orden[i]])) j++;
            double promedio = (i + j) / 2.0 + 1;
            for (int k = i; k <= j; k++) rango[orden[k]] = promedio;
            double t = j - i + 1;
            if (t > 1) hayEmpates = true;
            correccionEmpates += t * t * t - t;
            i = j + 1;
        }
        double wPositiva = 0;
        for (int i = 0; i < n; i++) if (d[i] > 0) wPositiva += rango[i];
        if (!hayEmpates && n <= 50) return wilcoxonExacto(n, (int) Math.round(wPositiva));
        double media = n * (n + 1) / 4.0;
        double varianza = n * (n + 1.0) * (2 * n + 1) / 24.0 - correccionEmpates / 48.0;
        if (varianza <= 0) return 1.0;
        double z = (Math.abs(wPositiva - media) - 0.5) / Math.sqrt(varianza);
        return z <= 0 ? 1.0 : Math.min(1.0, erfc(z / Math.sqrt(2)));
    }

    /** P(W <= min(w, total-w)) x 2 bajo H0, contando subconjuntos de {1..n} por suma. */
    private static double wilcoxonExacto(int n, int wPositiva) {
        int total = n * (n + 1) / 2;
        double[] formas = new double[total + 1];
        formas[0] = 1;
        for (int rango = 1; rango <= n; rango++)
            for (int suma = total; suma >= rango; suma--) formas[suma] += formas[suma - rango];
        int extremo = Math.min(wPositiva, total - wPositiva);
        double acumulado = 0;
        for (int suma = 0; suma <= extremo; suma++) acumulado += formas[suma];
        return Math.min(1.0, 2 * acumulado / Math.pow(2, n));
    }

    /** erfc con error relativo < 1.2e-7 (Numerical Recipes, erfcc). */
    private static double erfc(double x) {
        double z = Math.abs(x), t = 1.0 / (1.0 + 0.5 * z);
        double r = t * Math.exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418
                + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587
                + t * (-0.82215223 + t * 0.17087277)))))))));
        return x >= 0 ? r : 2.0 - r;
    }
}