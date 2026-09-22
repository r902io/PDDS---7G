package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;

import pe.edu.pucp.sisrap.experimentacion.dominio.EscenarioOperativo;
import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Estadistica;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.Comparacion;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.Comparacion.MetricaSecundaria;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.CorridaRegistrada;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.PuntoColapso;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.ResumenGrupo;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.PuntoConvergencia;

/**
 * Resúmenes por grupo y comparación pareada entre algoritmos, siguiendo el informe "Diseño de Experimento":
 * prueba de Wilcoxon bilateral (sección 4.1), tamaño de efecto r (sección 4.1) y corrección de Holm sobre
 * las métricas secundarias (sección 4.1: "los valores p de las métricas secundarias se ajustarán mediante
 * el procedimiento de Holm"). La métrica primaria de decisión es la tasa de cumplimiento de plazos
 * (sección 3.3); las demás (retraso, costo, tiempo, no asignados) son secundarias y se ajustan entre sí.
 */
public final class AnalisisExperimento {
    public static final double NIVEL_SIGNIFICANCIA = 0.05;
    public static final int MINIMO_PARES = 6;
    public static final double UMBRAL_COLAPSO = 0.5;
    public static final String SIN_DIFERENCIA = "SIN_DIFERENCIA_SIGNIFICATIVA";
    public static final String MUESTRA_INSUFICIENTE = "MUESTRA_INSUFICIENTE";
    public static final String SIN_COLAPSO_OBSERVADO = "SIN_COLAPSO_OBSERVADO";

    private AnalisisExperimento() {}

    public static List<ResumenGrupo> resumir(List<CorridaRegistrada> corridas) {
        Map<String, List<CorridaRegistrada>> grupos = new LinkedHashMap<>();
        for (var c : corridas)
            grupos.computeIfAbsent(c.variante() + "|" + c.escenario() + "|" + c.corrida().algoritmo(), k -> new ArrayList<>()).add(c);
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
            var conReplan = grupo.stream().filter(c -> c.corrida().tiempoReplanificacionMs() != null).toList();
            if (!conReplan.isEmpty()) {
                m.put("tiempoReplanificacionMs", estadistica(conReplan, c -> c.corrida().tiempoReplanificacionMs(), false));
                double exitosas = conReplan.stream().filter(c -> Boolean.TRUE.equals(c.corrida().replanificacionExitosa())).count();
                m.put("tasaReplanificacionExitosaPct", MedicionCorridas.estadistica(new double[]{100.0 * exitosas / conReplan.size()}, true));
            }
            double factibles = grupo.stream().filter(c -> c.corrida().factible()).count();
            salida.add(new ResumenGrupo(primera.variante(), primera.escenario(), primera.corrida().escenarioOperativo(),
                    primera.corrida().perfilPresion(), primera.instancia(), primera.corrida().algoritmo(), primera.corrida().tamanio(),
                    grupo.size(), 100.0 * factibles / grupo.size(), m));
        }
        return List.copyOf(salida);
    }

    private static Estadistica estadistica(List<CorridaRegistrada> filas, ToDoubleFunction<CorridaRegistrada> f, boolean maximizar) {
        return MedicionCorridas.estadistica(filas.stream().mapToDouble(f).toArray(), maximizar);
    }

    /**
     * Compara cada par de algoritmos dentro de una condición experimental concreta, emparejando
     * (escenario, repetición): misma instancia y misma semilla. No se agregan perfiles de presión,
     * escenarios operativos o niveles solo porque tengan el mismo tamaño de pedidos.
     */
    public static List<Comparacion> comparar(List<CorridaRegistrada> corridas, List<String> algoritmos) {
        Map<String, CorridaRegistrada> indice = new LinkedHashMap<>();
        for (var c : corridas)
            indice.put(clave(c.variante(), c.corrida().algoritmo(), c.escenario(), c.repeticion()), c);
        List<String> variantes = corridas.stream().map(CorridaRegistrada::variante).distinct().toList();
        List<String> escenarios = corridas.stream().map(CorridaRegistrada::escenario).distinct().sorted().toList();
        List<Comparacion> salida = new ArrayList<>();
        for (String variante : variantes) {
            for (String escenario : escenarios) {
                for (int i = 0; i < algoritmos.size(); i++) {
                    for (int j = i + 1; j < algoritmos.size(); j++) {
                        List<CorridaRegistrada[]> pares = new ArrayList<>();
                        for (var a : corridas) {
                            if (!a.variante().equals(variante) || !a.escenario().equals(escenario)
                                    || !a.corrida().algoritmo().equals(algoritmos.get(i))) continue;
                            var b = indice.get(clave(variante, algoritmos.get(j), escenario, a.repeticion()));
                            if (b != null) pares.add(new CorridaRegistrada[] {a, b});
                        }
                        if (!pares.isEmpty()) salida.add(comparar(variante, escenario, algoritmos.get(i), algoritmos.get(j), pares));
                    }
                }
            }
        }
        return List.copyOf(salida);
    }

    private static Comparacion comparar(String variante, String escenario, String algoritmoA, String algoritmoB,
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
        Wilcoxon wF = wilcoxon(diferencia);
        Wilcoxon wIgual = wilcoxon(diferenciaPresupuestoIgual);

        // Métrica primaria de decisión (sección 3.3/4.1): tasa de cumplimiento de plazos, y las secundarias
        // que la complementan; los p-valores de las 5 se ajustan juntos con la corrección de Holm.
        Map<String, ToDoubleFunction<CorridaRegistrada>> metricas = new LinkedHashMap<>();
        metricas.put("cumplimientoPlazosPct", c -> c.corrida().cumplimiento());
        metricas.put("retrasoHoras", c -> c.corrida().r());
        metricas.put("costo", c -> c.corrida().costo());
        metricas.put("tiempoMs", c -> c.corrida().tiempoMs());
        metricas.put("noAsignados", c -> c.corrida().n());
        Map<String, Wilcoxon> crudo = new LinkedHashMap<>();
        for (var e : metricas.entrySet()) {
            double[] d = new double[n];
            for (int k = 0; k < n; k++) d[k] = e.getValue().applyAsDouble(pares.get(k)[0]) - e.getValue().applyAsDouble(pares.get(k)[1]);
            crudo.put(e.getKey(), wilcoxon(d));
        }
        Map<String, Double> ajustados = holm(crudo);
        Map<String, MetricaSecundaria> secundarias = new LinkedHashMap<>();
        for (var e : crudo.entrySet()) {
            double r = tamanoEfecto(e.getValue().z(), n);
            double diferenciaSuma = sumaSigno(pares, metricas.get(e.getKey()));
            secundarias.put(e.getKey(), new MetricaSecundaria(e.getValue().pValor(), ajustados.get(e.getKey()), r,
                    veredicto(ajustados.get(e.getKey()), n, diferenciaSuma, algoritmoA, algoritmoB)));
        }

        var condicion = pares.get(0)[0];
        return new Comparacion(variante, escenario, condicion.corrida().escenarioOperativo(), condicion.corrida().perfilPresion(),
                condicion.instancia(), condicion.corrida().tamanio(), algoritmoA, algoritmoB, n,
                sumaA / n, sumaB / n, victoriasA, victoriasB,
                n - victoriasA - victoriasB, wF.pValor(), tamanoEfecto(wF.z(), n), interpretarEfecto(tamanoEfecto(wF.z(), n)),
                veredicto(wF.pValor(), n, sumaA - sumaB, algoritmoA, algoritmoB),
                evalA / n, evalB / n, msA / n, msB / n, wIgual.pValor(),
                veredicto(wIgual.pValor(), n, Arrays.stream(diferenciaPresupuestoIgual).sum(), algoritmoA, algoritmoB),
                Map.copyOf(secundarias));
    }

    private static double sumaSigno(List<CorridaRegistrada[]> pares, ToDoubleFunction<CorridaRegistrada> f) {
        double suma = 0;
        for (var par : pares) suma += f.applyAsDouble(par[0]) - f.applyAsDouble(par[1]);
        return suma;
    }

    /** Tamaño de efecto r = |Z| / sqrt(N) (sección 4.1); interpretación por umbrales del propio informe. */
    static double tamanoEfecto(double z, int n) {
        return n == 0 ? Double.NaN : Math.abs(z) / Math.sqrt(n);
    }

    static String interpretarEfecto(double r) {
        if (Double.isNaN(r)) return "N/D";
        if (r < 0.10) return "DESPRECIABLE";
        if (r < 0.30) return "PEQUENO";
        if (r < 0.50) return "MEDIANO";
        return "GRANDE";
    }

    /**
     * Corrección de Holm (Holm, 1979) sobre un conjunto de p-valores: se ordenan ascendentemente y cada
     * uno se multiplica por (cantidad de pruebas restantes), garantizando monotonía no decreciente.
     */
    static Map<String, Double> holm(Map<String, Wilcoxon> crudo) {
        List<String> orden = crudo.entrySet().stream()
                .sorted(Comparator.comparingDouble(e -> valorOrdenable(e.getValue().pValor())))
                .map(Map.Entry::getKey).toList();
        int m = orden.size();
        Map<String, Double> ajustado = new LinkedHashMap<>();
        double maximoPrevio = 0;
        for (int i = 0; i < m; i++) {
            double p = crudo.get(orden.get(i)).pValor();
            double ajuste = Double.isNaN(p) ? Double.NaN : Math.min(1.0, p * (m - i));
            if (!Double.isNaN(ajuste)) maximoPrevio = Math.max(maximoPrevio, ajuste);
            ajustado.put(orden.get(i), Double.isNaN(ajuste) ? Double.NaN : maximoPrevio);
        }
        return ajustado;
    }

    private static double valorOrdenable(double p) {
        return Double.isNaN(p) ? Double.POSITIVE_INFINITY : p;
    }

    private static String veredicto(double p, int pares, double diferenciaSuma, String a, String b) {
        if (pares < MINIMO_PARES || Double.isNaN(p)) return pares < MINIMO_PARES ? MUESTRA_INSUFICIENTE : SIN_DIFERENCIA;
        if (p >= NIVEL_SIGNIFICANCIA || diferenciaSuma == 0) return SIN_DIFERENCIA;
        return "GANA_" + (diferenciaSuma < 0 ? a : b);
    }

    /**
     * Punto de colapso por (variante, algoritmo): primer nivel de COLAPSO_LOGISTICO en el que más del 50%
     * de las repeticiones producen pedidos no atendidos (N(S) > 0), según la sección 3.4 del informe
     * "Diseño de Experimento". Si ningún nivel evaluado supera el umbral, se reporta SIN_COLAPSO_OBSERVADO.
     */
    public static List<PuntoColapso> puntosColapso(List<CorridaRegistrada> corridas) {
        Map<String, List<CorridaRegistrada>> grupos = new LinkedHashMap<>();
        for (var c : corridas)
            if (EscenarioOperativo.COLAPSO_LOGISTICO.name().equals(c.corrida().escenarioOperativo()))
                grupos.computeIfAbsent(c.variante() + "|" + c.corrida().algoritmo(), k -> new ArrayList<>()).add(c);
        List<PuntoColapso> salida = new ArrayList<>();
        for (var entrada : grupos.entrySet()) {
            String[] partes = entrada.getKey().split("\\|", 2);
            Map<Integer, List<CorridaRegistrada>> porNivel = new TreeMap<>();
            for (var c : entrada.getValue()) porNivel.computeIfAbsent(c.instancia(), k -> new ArrayList<>()).add(c);
            Integer nivelColapso = null;
            double tasaEnColapso = 0;
            for (var nivel : porNivel.entrySet()) {
                double conPendientes = nivel.getValue().stream().filter(c -> c.corrida().n() > 0).count();
                double tasa = conPendientes / nivel.getValue().size();
                if (tasa > UMBRAL_COLAPSO) { nivelColapso = nivel.getKey(); tasaEnColapso = tasa; break; }
            }
            salida.add(new PuntoColapso(partes[0], partes[1],
                    nivelColapso == null ? SIN_COLAPSO_OBSERVADO : String.valueOf(nivelColapso), 100.0 * tasaEnColapso));
        }
        return List.copyOf(salida);
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

    record Wilcoxon(double pValor, double z) {}

    /**
     * Prueba de rangos con signo de Wilcoxon, dos colas. Exacta cuando hay como máximo 50 diferencias no nulas
     * y ninguna empata en valor absoluto; en otro caso, aproximación normal con corrección por empates y
     * por continuidad. z siempre se calcula por la aproximación normal (para el tamaño de efecto, sección 4.1),
     * incluso cuando el p-valor reportado proviene de la variante exacta. pValor es NaN si no queda ninguna
     * diferencia distinta de cero.
     */
    static Wilcoxon wilcoxon(double[] diferencias) {
        double[] d = Arrays.stream(diferencias).filter(x -> x != 0).toArray();
        int n = d.length;
        if (n == 0) return new Wilcoxon(Double.NaN, 0);
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
        double media = n * (n + 1) / 4.0;
        double varianza = n * (n + 1.0) * (2 * n + 1) / 24.0 - correccionEmpates / 48.0;
        double z = varianza <= 0 ? 0 : (wPositiva - media) / Math.sqrt(varianza);
        double p = (!hayEmpates && n <= 50) ? wilcoxonExacto(n, (int) Math.round(wPositiva))
                : (varianza <= 0 ? 1.0 : (Math.abs(z) <= 0 ? 1.0 : Math.min(1.0, erfc(Math.abs(z) / Math.sqrt(2)))));
        return new Wilcoxon(p, z);
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
