package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import pe.edu.pucp.sisrap.experimentacion.dominio.EscenarioOperativo;
import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Estadistica;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.Comparacion;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.Comparacion.MetricaSecundaria;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.CorridaRegistrada;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.ResumenGrupo;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.PuntoConvergencia;

/** Análisis descriptivo y pareado del experimento. */
public final class AnalisisExperimento {
    public static final double NIVEL_SIGNIFICANCIA = 0.05;
    public static final int MINIMO_PARES = 6;
    public static final String SIN_DIFERENCIA = "SIN_DIFERENCIA_SIGNIFICATIVA";
    public static final String MUESTRA_INSUFICIENTE = "MUESTRA_INSUFICIENTE";
    public static final String NO_COMPARABLE_COLAPSO = "NO_COMPARABLE_EN_COLAPSO";

    private AnalisisExperimento() {}

    public static List<ResumenGrupo> resumir(List<CorridaRegistrada> corridas) {
        Map<String, List<CorridaRegistrada>> grupos = new LinkedHashMap<>();
        for (var c : corridas) {
            grupos.computeIfAbsent(
                    c.variante() + "|" + c.escenario() + "|" + c.corrida().algoritmo(),
                    k -> new ArrayList<>()).add(c);
        }

        List<ResumenGrupo> salida = new ArrayList<>();
        for (var grupo : grupos.values()) {
            var primera = grupo.get(0);
            Map<String, Estadistica> m = new LinkedHashMap<>();

            m.put("objetivo", estadistica(grupo, x -> x.corrida().objetivo(), false));
            m.put("tiempoMs", estadistica(grupo, x -> x.corrida().tiempoMs(), false));
            m.put("evaluaciones", estadistica(grupo, CorridaRegistrada::evaluaciones, false));
            m.put("cumplimientoPct", estadistica(grupo, x -> x.corrida().cumplimiento(), true));

            var prioritarios = grupo.stream()
                    .filter(x -> x.corrida().cumplimientoPrioritarios() != null)
                    .toList();
            if (!prioritarios.isEmpty()) {
                m.put("cumplimientoPrioritariosPct",
                        estadistica(prioritarios, x -> x.corrida().cumplimientoPrioritarios(), true));
            }

            m.put("retrasoHoras", estadistica(grupo, x -> x.corrida().r(), false));
            m.put("tiempoAtencionHoras", estadistica(grupo, x -> x.corrida().t(), false));
            m.put("noAsignados", estadistica(grupo, x -> x.corrida().n(), false));
            m.put("costo", estadistica(grupo, x -> x.corrida().costo(), false));
            m.put("distanciaKm", estadistica(grupo, x -> x.corrida().distancia(), false));
            m.put("utilizacionPct", estadistica(grupo, x -> x.corrida().utilizacion(), true));

            var conReplan = grupo.stream()
                    .filter(x -> x.corrida().tiempoReplanificacionMs() != null)
                    .toList();
            if (!conReplan.isEmpty()) {
                m.put("tiempoReplanificacionMs",
                        estadistica(conReplan, x -> x.corrida().tiempoReplanificacionMs(), false));
                m.put("replanificacionExitosaPct",
                        estadistica(conReplan,
                                x -> Boolean.TRUE.equals(x.corrida().replanificacionExitosa()) ? 100.0 : 0.0,
                                true));
            }

            var conColapso = grupo.stream()
                    .filter(x -> x.corrida().tiempoColapsoHoras() != null)
                    .toList();
            if (!conColapso.isEmpty()) {
                m.put("tiempoColapsoHoras",
                        estadistica(conColapso, x -> x.corrida().tiempoColapsoHoras(), true));
            }

            var corridasColapso = grupo.stream()
                    .filter(x -> x.corrida().colapsoAlcanzado() != null)
                    .toList();
            if (!corridasColapso.isEmpty()) {
                m.put("colapsoAlcanzadoPct",
                        estadistica(corridasColapso,
                                x -> Boolean.TRUE.equals(x.corrida().colapsoAlcanzado()) ? 100.0 : 0.0,
                                false));
            }

            double factibles = grupo.stream().filter(x -> x.corrida().factible()).count();
            salida.add(new ResumenGrupo(
                    primera.variante(),
                    primera.escenario(),
                    primera.corrida().escenarioOperativo(),
                    primera.corrida().perfilPresion(),
                    primera.instancia(),
                    primera.corrida().algoritmo(),
                    primera.corrida().tamanio(),
                    grupo.size(),
                    100.0 * factibles / grupo.size(),
                    Map.copyOf(m)));
        }
        return List.copyOf(salida);
    }

    private static Estadistica estadistica(List<CorridaRegistrada> filas,
                                           ToDoubleFunction<CorridaRegistrada> f,
                                           boolean maximizar) {
        return MedicionCorridas.estadistica(filas.stream().mapToDouble(f).toArray(), maximizar);
    }

    public static List<Comparacion> comparar(List<CorridaRegistrada> corridas,
                                             List<String> algoritmos) {
        Map<String, CorridaRegistrada> indice = new LinkedHashMap<>();
        for (var c : corridas) {
            indice.put(clave(c.variante(), c.corrida().algoritmo(), c.escenario(), c.repeticion()), c);
        }

        List<String> variantes = corridas.stream().map(CorridaRegistrada::variante).distinct().toList();
        List<String> escenarios = corridas.stream().map(CorridaRegistrada::escenario).distinct().sorted().toList();
        List<Comparacion> salida = new ArrayList<>();

        for (String variante : variantes) {
            for (String escenario : escenarios) {
                for (int i = 0; i < algoritmos.size(); i++) {
                    for (int j = i + 1; j < algoritmos.size(); j++) {
                        List<CorridaRegistrada[]> pares = new ArrayList<>();
                        for (var a : corridas) {
                            if (!a.variante().equals(variante)
                                    || !a.escenario().equals(escenario)
                                    || !a.corrida().algoritmo().equals(algoritmos.get(i))) {
                                continue;
                            }
                            var b = indice.get(clave(
                                    variante,
                                    algoritmos.get(j),
                                    escenario,
                                    a.repeticion()));
                            if (b != null) pares.add(new CorridaRegistrada[]{a, b});
                        }
                        if (!pares.isEmpty()) {
                            salida.add(comparar(
                                    variante,
                                    escenario,
                                    algoritmos.get(i),
                                    algoritmos.get(j),
                                    pares));
                        }
                    }
                }
            }
        }
        return List.copyOf(salida);
    }

    private static Comparacion comparar(String variante,
                                        String escenario,
                                        String algoritmoA,
                                        String algoritmoB,
                                        List<CorridaRegistrada[]> pares) {
        int n = pares.size();
        int victoriasA = 0;
        int victoriasB = 0;
        double[] diferenciaF = new double[n];
        double sumaA = 0;
        double sumaB = 0;
        double evalA = 0;
        double evalB = 0;
        double msA = 0;
        double msB = 0;

        for (int k = 0; k < n; k++) {
            var a = pares.get(k)[0];
            var b = pares.get(k)[1];
            double fa = a.corrida().objetivo();
            double fb = b.corrida().objetivo();
            diferenciaF[k] = fa - fb;
            if (fa < fb) victoriasA++;
            else if (fb < fa) victoriasB++;
            sumaA += fa;
            sumaB += fb;
            evalA += a.evaluaciones();
            evalB += b.evaluaciones();
            msA += a.corrida().tiempoMs();
            msB += b.corrida().tiempoMs();
        }

        var condicion = pares.get(0)[0];
        boolean esColapso = EscenarioOperativo.COLAPSO_LOGISTICO.name()
                .equals(condicion.corrida().escenarioOperativo());

        // En colapso cada algoritmo puede detenerse en un día/tamaño distinto; por eso el F
        // de la última jornada no es comparable entre algoritmos. La comparación principal
        // de este escenario es el tiempo hasta el colapso.
        Wilcoxon wF = esColapso ? new Wilcoxon(Double.NaN, 0.0, 0) : wilcoxon(diferenciaF);
        double efectoF = esColapso ? Double.NaN : tamanoEfecto(wF.z(), wF.nNoCero());
        double mediaObjetivoA = sumaA / n;
        double mediaObjetivoB = sumaB / n;
        double diferenciaRelativaObjetivoPct = esColapso
                ? Double.NaN
                : diferenciaRelativaPct(mediaObjetivoA, mediaObjetivoB);

        double pPresupuesto = Double.NaN;
        String veredictoPresupuesto = SIN_DIFERENCIA;
        if (!esColapso && pares.stream().allMatch(p ->
                !p[0].corrida().convergencia().isEmpty() && !p[1].corrida().convergencia().isEmpty())) {
            double[] dPresupuesto = new double[n];
            for (int k = 0; k < n; k++) {
                var a = pares.get(k)[0];
                var b = pares.get(k)[1];
                int presupuesto = Math.min(a.evaluaciones(), b.evaluaciones());
                dPresupuesto[k] = mejorHasta(a.corrida().convergencia(), presupuesto)
                        - mejorHasta(b.corrida().convergencia(), presupuesto);
            }
            Wilcoxon w = wilcoxon(dPresupuesto);
            pPresupuesto = w.pValor();
            veredictoPresupuesto = veredicto(
                    w.pValor(), paresValidos(dPresupuesto), direccion(dPresupuesto),
                    false, algoritmoA, algoritmoB);
        }

        Map<String, MetricaDef> definiciones = new LinkedHashMap<>();
        definiciones.put("cumplimientoPlazosPct", new MetricaDef(
                x -> x.corrida().cumplimiento(), true, false));
        definiciones.put("cumplimientoPrioritariosPct", new MetricaDef(
                x -> x.corrida().cumplimientoPrioritarios() == null
                        ? Double.NaN : x.corrida().cumplimientoPrioritarios(), true, true));
        definiciones.put("retrasoHoras", new MetricaDef(x -> x.corrida().r(), false, true));
        definiciones.put("costo", new MetricaDef(x -> x.corrida().costo(), false, true));
        definiciones.put("tiempoMs", new MetricaDef(x -> x.corrida().tiempoMs(), false, true));
        definiciones.put("noAsignados", new MetricaDef(x -> x.corrida().n(), false, true));
        definiciones.put("tiempoReplanificacionMs", new MetricaDef(
                x -> x.corrida().tiempoReplanificacionMs() == null
                        ? Double.NaN : x.corrida().tiempoReplanificacionMs(), false, true));
        definiciones.put("replanificacionExitosa", new MetricaDef(
                x -> x.corrida().replanificacionExitosa() == null
                        ? Double.NaN
                        : Boolean.TRUE.equals(x.corrida().replanificacionExitosa()) ? 1.0 : 0.0,
                true, true));
        definiciones.put("tiempoColapsoHoras", new MetricaDef(
                x -> x.corrida().tiempoColapsoHoras() == null
                        ? Double.NaN : x.corrida().tiempoColapsoHoras(), true, false));

        Map<String, ResultadoMetrica> crudos = new LinkedHashMap<>();
        for (var e : definiciones.entrySet()) {
            double[] diferencias = diferencias(pares, e.getValue().valor());
            if (diferencias.length == 0) continue;
            Wilcoxon w = wilcoxon(diferencias);
            double[] medias = mediasPares(pares, e.getValue().valor());
            crudos.put(e.getKey(), new ResultadoMetrica(
                    w,
                    diferencias.length,
                    direccion(diferencias),
                    e.getValue().maximizar(),
                    e.getValue().ajustarHolm(),
                    medias[0],
                    medias[1]));
        }

        Map<String, Wilcoxon> paraHolm = new LinkedHashMap<>();
        for (var e : crudos.entrySet()) {
            if (e.getValue().ajustarHolm()) paraHolm.put(e.getKey(), e.getValue().wilcoxon());
        }
        Map<String, Double> ajustados = holm(paraHolm);

        Map<String, MetricaSecundaria> metricas = new LinkedHashMap<>();
        for (var e : crudos.entrySet()) {
            ResultadoMetrica rm = e.getValue();
            double pCrudo = rm.wilcoxon().pValor();
            double pDecision = rm.ajustarHolm()
                    ? ajustados.getOrDefault(e.getKey(), pCrudo)
                    : pCrudo;
            double pHolm = rm.ajustarHolm() ? pDecision : pCrudo;
            double efecto = tamanoEfecto(rm.wilcoxon().z(), rm.wilcoxon().nNoCero());
            metricas.put(e.getKey(), new MetricaSecundaria(
                    pCrudo,
                    pHolm,
                    efecto,
                    rm.paresValidos(),
                    rm.wilcoxon().nNoCero(),
                    rm.mediaA(),
                    rm.mediaB(),
                    diferenciaRelativaPct(rm.mediaA(), rm.mediaB()),
                    veredicto(pDecision, rm.paresValidos(), rm.direccion(), rm.maximizar(), algoritmoA, algoritmoB)));
        }

        return new Comparacion(
                variante,
                escenario,
                condicion.corrida().escenarioOperativo(),
                condicion.corrida().perfilPresion(),
                condicion.instancia(),
                condicion.corrida().tamanio(),
                algoritmoA,
                algoritmoB,
                n,
                mediaObjetivoA,
                mediaObjetivoB,
                victoriasA,
                victoriasB,
                n - victoriasA - victoriasB,
                wF.pValor(),
                efectoF,
                wF.nNoCero(),
                diferenciaRelativaObjetivoPct,
                interpretarEfecto(efectoF),
                esColapso ? NO_COMPARABLE_COLAPSO
                        : veredicto(wF.pValor(), paresValidos(diferenciaF), direccion(diferenciaF), false, algoritmoA, algoritmoB),
                evalA / n,
                evalB / n,
                msA / n,
                msB / n,
                pPresupuesto,
                veredictoPresupuesto,
                Map.copyOf(metricas));
    }

    private record MetricaDef(ToDoubleFunction<CorridaRegistrada> valor,
                              boolean maximizar,
                              boolean ajustarHolm) {}

    private record ResultadoMetrica(Wilcoxon wilcoxon,
                                    int paresValidos,
                                    double direccion,
                                    boolean maximizar,
                                    boolean ajustarHolm,
                                    double mediaA,
                                    double mediaB) {}

    private static double[] diferencias(List<CorridaRegistrada[]> pares,
                                        ToDoubleFunction<CorridaRegistrada> f) {
        return pares.stream()
                .mapToDouble(par -> {
                    double a = f.applyAsDouble(par[0]);
                    double b = f.applyAsDouble(par[1]);
                    return Double.isFinite(a) && Double.isFinite(b) ? a - b : Double.NaN;
                })
                .filter(Double::isFinite)
                .toArray();
    }

    private static double[] mediasPares(List<CorridaRegistrada[]> pares,
                                       ToDoubleFunction<CorridaRegistrada> f) {
        double sumaA = 0.0;
        double sumaB = 0.0;
        int n = 0;
        for (CorridaRegistrada[] par : pares) {
            double a = f.applyAsDouble(par[0]);
            double b = f.applyAsDouble(par[1]);
            if (!Double.isFinite(a) || !Double.isFinite(b)) continue;
            sumaA += a;
            sumaB += b;
            n++;
        }
        return n == 0
                ? new double[]{Double.NaN, Double.NaN}
                : new double[]{sumaA / n, sumaB / n};
    }

    static double diferenciaRelativaPct(double mediaA, double mediaB) {
        if (!Double.isFinite(mediaA) || !Double.isFinite(mediaB) || Math.abs(mediaB) < 1e-12) {
            return Double.NaN;
        }
        return 100.0 * (mediaA - mediaB) / Math.abs(mediaB);
    }

    private static int paresValidos(double[] diferencias) {
        return (int) Arrays.stream(diferencias).filter(Double::isFinite).count();
    }

    private static double direccion(double[] diferencias) {
        double[] validas = Arrays.stream(diferencias).filter(Double::isFinite).toArray();
        if (validas.length == 0) return 0.0;
        Arrays.sort(validas);
        double mediana = (validas[(validas.length - 1) / 2] + validas[validas.length / 2]) / 2.0;
        if (mediana != 0.0) return mediana;
        return Arrays.stream(validas).sum();
    }

    static double tamanoEfecto(double z, int nNoCero) {
        return nNoCero == 0 ? 0.0 : Math.abs(z) / Math.sqrt(nNoCero);
    }

    static String interpretarEfecto(double r) {
        if (Double.isNaN(r)) return "N/D";
        if (r < 0.10) return "DESPRECIABLE";
        if (r < 0.30) return "PEQUENO";
        if (r < 0.50) return "MEDIANO";
        return "GRANDE";
    }

    /** Holm solo para las métricas secundarias; SLA principal queda sin ajuste. */
    static Map<String, Double> holm(Map<String, Wilcoxon> crudo) {
        if (crudo.isEmpty()) return Map.of();

        List<String> orden = crudo.entrySet().stream()
                .sorted(Comparator.comparingDouble(e -> valorOrdenable(e.getValue().pValor())))
                .map(Map.Entry::getKey)
                .toList();

        int m = orden.size();
        Map<String, Double> ajustado = new LinkedHashMap<>();
        double maximoPrevio = 0.0;

        for (int i = 0; i < m; i++) {
            double p = crudo.get(orden.get(i)).pValor();
            double valor = Double.isNaN(p) ? Double.NaN : Math.min(1.0, p * (m - i));
            if (!Double.isNaN(valor)) maximoPrevio = Math.max(maximoPrevio, valor);
            ajustado.put(orden.get(i), Double.isNaN(valor) ? Double.NaN : maximoPrevio);
        }
        return ajustado;
    }

    private static double valorOrdenable(double p) {
        return Double.isNaN(p) ? Double.POSITIVE_INFINITY : p;
    }

    private static String veredicto(double p,
                                    int pares,
                                    double direccion,
                                    boolean maximizar,
                                    String a,
                                    String b) {
        if (pares < MINIMO_PARES) return MUESTRA_INSUFICIENTE;
        if (Double.isNaN(p) || p >= NIVEL_SIGNIFICANCIA || direccion == 0.0) return SIN_DIFERENCIA;

        boolean aMejor = maximizar ? direccion > 0 : direccion < 0;
        return "GANA_" + (aMejor ? a : b);
    }

    /** Mejor objetivo encontrado usando a lo sumo {@code presupuesto} evaluaciones. */
    static double mejorHasta(List<PuntoConvergencia> traza, int presupuesto) {
        if (traza.isEmpty()) return Double.NaN;
        double mejor = traza.get(0).mejorObjetivo();
        for (var punto : traza) {
            if (punto.evaluaciones() > presupuesto) break;
            mejor = punto.mejorObjetivo();
        }
        return mejor;
    }

    private static String clave(String variante,
                                String algoritmo,
                                String escenario,
                                int repeticion) {
        return variante + "|" + algoritmo + "|" + escenario + "|" + repeticion;
    }

    record Wilcoxon(double pValor, double z, int nNoCero) {}

    /** Wilcoxon pareado bilateral. */
    static Wilcoxon wilcoxon(double[] diferencias) {
        double[] d = Arrays.stream(diferencias)
                .filter(Double::isFinite)
                .filter(x -> x != 0.0)
                .toArray();
        int n = d.length;
        if (n == 0) return new Wilcoxon(Double.NaN, 0.0, 0);

        Integer[] orden = new Integer[n];
        for (int i = 0; i < n; i++) orden[i] = i;
        Arrays.sort(orden, Comparator.comparingDouble(i -> Math.abs(d[i])));

        double[] rango = new double[n];
        double correccionEmpates = 0.0;
        boolean hayEmpates = false;

        for (int i = 0; i < n; ) {
            int j = i;
            while (j + 1 < n && Math.abs(d[orden[j + 1]]) == Math.abs(d[orden[i]])) j++;
            double promedio = (i + j) / 2.0 + 1.0;
            for (int k = i; k <= j; k++) rango[orden[k]] = promedio;
            double t = j - i + 1;
            if (t > 1) hayEmpates = true;
            correccionEmpates += t * t * t - t;
            i = j + 1;
        }

        double wPositiva = 0.0;
        for (int i = 0; i < n; i++) if (d[i] > 0) wPositiva += rango[i];

        double media = n * (n + 1) / 4.0;
        double varianza = n * (n + 1.0) * (2 * n + 1) / 24.0 - correccionEmpates / 48.0;
        double z = varianza <= 0 ? 0.0 : (wPositiva - media) / Math.sqrt(varianza);

        double p = (!hayEmpates && n <= 50)
                ? wilcoxonExacto(n, (int) Math.round(wPositiva))
                : (varianza <= 0
                    ? 1.0
                    : Math.min(1.0, erfc(Math.abs(z) / Math.sqrt(2.0))));

        return new Wilcoxon(p, z, n);
    }

    private static double wilcoxonExacto(int n, int wPositiva) {
        int total = n * (n + 1) / 2;
        double[] formas = new double[total + 1];
        formas[0] = 1.0;
        for (int rango = 1; rango <= n; rango++) {
            for (int suma = total; suma >= rango; suma--) {
                formas[suma] += formas[suma - rango];
            }
        }
        int extremo = Math.min(wPositiva, total - wPositiva);
        double acumulado = 0.0;
        for (int suma = 0; suma <= extremo; suma++) acumulado += formas[suma];
        return Math.min(1.0, 2.0 * acumulado / Math.pow(2.0, n));
    }

    private static double erfc(double x) {
        double z = Math.abs(x);
        double t = 1.0 / (1.0 + 0.5 * z);
        double r = t * Math.exp(-z * z - 1.26551223
                + t * (1.00002368
                + t * (0.37409196
                + t * (0.09678418
                + t * (-0.18628806
                + t * (0.27886807
                + t * (-1.13520398
                + t * (1.48851587
                + t * (-0.82215223
                + t * 0.17087277)))))))));
        return x >= 0 ? r : 2.0 - r;
    }
}