package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/** Remuestreo semanal de demanda con presión estocástica separada del histórico. */
public final class ModeloDemandaColapso {
    public record Dia(LocalDate fechaHistorica, int pedidos, double factorPresion) { }

    private final Map<LocalDate, Integer> cantidades;
    private final List<LocalDate> fechas;
    private final List<LocalDate> iniciosBloque;
    private final List<Double> tasas;
    private final List<Double> factores = new ArrayList<>(List.of(1.0));
    private final LocalDate inicio;
    private final long semilla;
    private final double intensidad;
    private final boolean usaRespaldo;
    private final double tasaRespaldo;
    private final double probabilidadRespaldo;
    private final double factorObjetivo;
    private final double limiteAumentoDiario;
    private final boolean objetivoAlcanzable;

    public ModeloDemandaColapso(Map<LocalDate, Integer> cantidades,
            Map<LocalDate, Long> paquetes, LocalDate inicio, long semilla,
            double capacidadDiariaEstimada, int diasReferencia,
            double tasaRespaldo, double probabilidadRespaldo) {
        if (cantidades == null || cantidades.isEmpty() || inicio == null
                || paquetes == null || diasReferencia < 1
                || !Double.isFinite(capacidadDiariaEstimada) || capacidadDiariaEstimada < 0
                || !Double.isFinite(tasaRespaldo) || tasaRespaldo <= 0 || tasaRespaldo > 1
                || !Double.isFinite(probabilidadRespaldo)
                || probabilidadRespaldo <= 0 || probabilidadRespaldo > 1) {
            throw new IllegalArgumentException("Parámetros inválidos del modelo de colapso");
        }
        this.cantidades = new TreeMap<>(cantidades);
        if (this.cantidades.values().stream().anyMatch(n -> n == null || n < 0)) {
            throw new IllegalArgumentException("El histórico no admite conteos negativos");
        }
        this.fechas = List.copyOf(this.cantidades.keySet());
        this.inicio = inicio;
        this.semilla = semilla;
        this.tasaRespaldo = tasaRespaldo;
        this.probabilidadRespaldo = probabilidadRespaldo;
        List<LocalDate> bloques = new ArrayList<>();
        List<Double> cambios = new ArrayList<>();
        for (LocalDate fecha : fechas) {
            if (fecha.getDayOfWeek() == inicio.getDayOfWeek()) {
                boolean completo = true;
                for (int d = 0; d < 7; d++) {
                    if (!this.cantidades.containsKey(fecha.plusDays(d))) completo = false;
                }
                if (completo) bloques.add(fecha);
            }
            Integer anterior = this.cantidades.get(fecha.minusDays(7));
            if (anterior != null && anterior > 0) {
                // Cambio semanal convertido a tasa diaria equivalente. Los descensos
                // y ceros siguen presentes como resultados sin aumento de presión.
                double razon = (double) this.cantidades.get(fecha) / anterior;
                cambios.add(Math.max(0.0, Math.expm1(Math.log(Math.max(Double.MIN_NORMAL, razon)) / 7)));
            }
        }
        this.iniciosBloque = List.copyOf(bloques);
        this.tasas = List.copyOf(cambios);
        this.usaRespaldo = cambios.stream().noneMatch(r -> r > 0);

        List<Long> cargas = fechas.stream().map(f -> paquetes.getOrDefault(f, 0L)).sorted().toList();
        if (cargas.stream().anyMatch(q -> q < 0)) throw new IllegalArgumentException("Carga histórica negativa");
        int n = cargas.size();
        double cargaBase = n % 2 == 0
                ? cargas.get(n / 2 - 1) / 2.0 + cargas.get(n / 2) / 2.0 : cargas.get(n / 2);
        if (cargaBase == 0) cargaBase = cargas.stream().mapToDouble(Long::doubleValue).average().orElse(0);
        if (cargaBase <= 0) throw new IllegalArgumentException("El histórico requiere pedidos con carga positiva");

        // En el día de referencia se busca añadir, EN ESPERANZA LOGARÍTMICA,
        // una carga equivalente a la capacidad estimada a la carga base.
        // Es calibración analítica de estrés, no una predicción del primer SLA vencido.
        factorObjetivo = 1.0 + capacidadDiariaEstimada / cargaBase;
        // Un evento de presión no puede multiplicar la carga más que añadir
        // una capacidad diaria estimada a la base. Evita saltos sin límite
        // cuando los aumentos históricos son muy infrecuentes.
        limiteAumentoDiario = factorObjetivo - 1;
        double solicitado = Math.log(factorObjetivo) / diasReferencia;
        double maximo = probabilidadAumento() * Math.log1p(limiteAumentoDiario);
        objetivoAlcanzable = solicitado <= maximo + 1e-12;
        double objetivoLogDiario = Math.min(solicitado, maximo);
        double menorTasa = usaRespaldo ? tasaRespaldo
                : tasas.stream().filter(r -> r > 0).mapToDouble(Double::doubleValue).min().orElseThrow();
        double inferior = 0, superior = limiteAumentoDiario / menorTasa;
        if (!Double.isFinite(superior)) throw new IllegalArgumentException("No se pudo calibrar la presión");
        for (int i = 0; i < 80; i++) {
            double medio = inferior + (superior - inferior) / 2;
            if (mediaLogCrecimiento(medio) < objetivoLogDiario) inferior = medio;
            else superior = medio;
        }
        intensidad = inferior + (superior - inferior) / 2;
    }

    private double mediaLogCrecimiento(double alpha) {
        if (usaRespaldo) return probabilidadRespaldo * Math.log1p(Math.min(limiteAumentoDiario, alpha * tasaRespaldo));
        return tasas.stream().mapToDouble(r -> Math.log1p(Math.min(limiteAumentoDiario, alpha * r))).average().orElseThrow();
    }

    /** Permite consultas repetidas o fuera de orden sin cambiar la secuencia. */
    public synchronized Dia dia(int indice) {
        if (indice < 0) throw new IllegalArgumentException("Índice de día negativo");
        while (factores.size() <= indice) {
            int d = factores.size();
            Random random = new Random(mezclar(semilla ^ 0x51A7E55L, inicio.plusDays(d).toEpochDay()));
            double tasa = usaRespaldo
                    ? (random.nextDouble() < probabilidadRespaldo ? tasaRespaldo : 0)
                    : tasas.get(random.nextInt(tasas.size()));
            double factor = factores.get(d - 1) * (1 + Math.min(limiteAumentoDiario, intensidad * tasa));
            if (!Double.isFinite(factor)) throw new IllegalStateException("La presión excede el rango numérico admitido");
            factores.add(factor);
        }
        LocalDate historica;
        if (!iniciosBloque.isEmpty()) {
            Random seleccion = new Random(mezclar(semilla ^ 0xB10C7L, indice / 7));
            historica = iniciosBloque.get(seleccion.nextInt(iniciosBloque.size())).plusDays(indice % 7);
        } else {
            // Sin bloques completos, solo se usan fechas realmente cargadas;
            // los periodos ausentes no se inventan como días con cero ventas.
            var compatibles = fechas.stream().filter(f -> f.getDayOfWeek()
                    == inicio.plusDays(indice).getDayOfWeek()).toList();
            var candidatas = compatibles.isEmpty() ? fechas : compatibles;
            Random seleccion = new Random(mezclar(semilla ^ 0xB10C7L, indice));
            historica = candidatas.get(seleccion.nextInt(candidatas.size()));
        }
        double objetivo = cantidades.get(historica) * factores.get(indice);
        if (!Double.isFinite(objetivo) || objetivo > Integer.MAX_VALUE) {
            throw new IllegalStateException("Demanda sintética excede el máximo admitido");
        }
        return new Dia(historica, (int) Math.round(objetivo), factores.get(indice));
    }

    public boolean usaRespaldo() { return usaRespaldo; }
    public boolean usaBloquesCompletos() { return !iniciosBloque.isEmpty(); }
    public double intensidad() { return intensidad; }
    public double factorObjetivo() { return factorObjetivo; }
    public boolean objetivoAlcanzable() { return objetivoAlcanzable; }
    public double probabilidadAumento() {
        return usaRespaldo ? probabilidadRespaldo
                : tasas.stream().filter(r -> r > 0).count() / (double) tasas.size();
    }

    private static long mezclar(long semilla, long valor) {
        long x = semilla ^ (valor + 0x9E3779B97F4A7C15L);
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }
}
