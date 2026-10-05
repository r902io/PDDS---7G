package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import pe.edu.pucp.sisrap.carga.dominio.PedidoImportado;
import pe.edu.pucp.sisrap.experimentacion.dominio.DatosArchivos;
import pe.edu.pucp.sisrap.experimentacion.dominio.PerfilPresion;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;

/**
 * Modelo simple de demanda para la experimentación.
 *
 * Usa los primeros 90 días disponibles desde --desde como periodo histórico de calibración.
 * La cantidad diaria observada se resume mediante P50, P75 y P90:
 * NORMAL=P50, ALTA=P75 y CRITICA=P90.
 */
public final class ModeloDemandaHistorica {
    public static final int DIAS_BASE = 90;

    private ModeloDemandaHistorica() {}

    public record Resumen(LocalDate desde,
                          LocalDate hasta,
                          int dias,
                          int p50,
                          int p75,
                          int p90,
                          List<PedidoImportado> plantillas) {
        public Resumen {
            if (desde == null || hasta == null || hasta.isBefore(desde))
                throw new IllegalArgumentException("Periodo histórico inválido");
            if (dias < 1 || p50 < 1 || p75 < p50 || p90 < p75)
                throw new IllegalArgumentException("Estadísticas de demanda inválidas");
            plantillas = List.copyOf(plantillas);
            if (plantillas.isEmpty())
                throw new IllegalArgumentException("No hay pedidos históricos para generar demanda");
        }

        public int lambda(PerfilPresion perfil) {
            return switch (perfil) {
                case NORMAL -> p50;
                case ALTA -> p75;
                case CRITICA -> p90;
            };
        }
    }

    public static Resumen analizar(DatosArchivos datos, LocalDate desde) {
        if (datos == null || datos.pedidos().isEmpty())
            throw new IllegalArgumentException("No existen pedidos históricos para estimar la demanda");
        if (desde == null) throw new IllegalArgumentException("Falta fecha inicial para estimar demanda");

        LocalDate ultimaFecha = datos.pedidos().stream()
                .map(p -> p.llegada().toLocalDate())
                .max(Comparator.naturalOrder())
                .orElseThrow();

        if (ultimaFecha.isBefore(desde))
            throw new IllegalArgumentException("No existen pedidos desde " + desde);

        LocalDate hasta = desde.plusDays(DIAS_BASE - 1L);
        if (hasta.isAfter(ultimaFecha)) hasta = ultimaFecha;

        int dias = Math.toIntExact(ChronoUnit.DAYS.between(desde, hasta) + 1L);
        if (dias < 30) {
            throw new IllegalArgumentException(
                    "Se requieren al menos 30 días históricos para estimar la presión logística; solo hay " + dias);
        }

        Map<LocalDate, Integer> cantidadPorDia = new HashMap<>();
        List<PedidoImportado> plantillas = new ArrayList<>();

        for (PedidoImportado p : datos.pedidos()) {
            LocalDate fecha = p.llegada().toLocalDate();
            if (fecha.isBefore(desde) || fecha.isAfter(hasta)) continue;
            cantidadPorDia.merge(fecha, 1, Integer::sum);
            plantillas.add(p);
        }

        List<Integer> cantidades = new ArrayList<>(dias);
        for (int i = 0; i < dias; i++) {
            cantidades.add(cantidadPorDia.getOrDefault(desde.plusDays(i), 0));
        }
        cantidades.sort(Integer::compareTo);

        int p50 = Math.max(1, (int) Math.round(percentil(cantidades, 0.50)));
        int p75 = Math.max(p50, (int) Math.round(percentil(cantidades, 0.75)));
        int p90 = Math.max(p75, (int) Math.round(percentil(cantidades, 0.90)));

        return new Resumen(desde, hasta, dias, p50, p75, p90, plantillas);
    }

    /** Generación independiente conservada para usos auxiliares. */
    public static List<Pedido> generarDia(Resumen resumen,
                                          PerfilPresion perfil,
                                          LocalDate fecha,
                                          Random random,
                                          long idInicial) {
        return generarDia(resumen, resumen.lambda(perfil), fecha, random, idInicial);
    }

    /** Generación independiente con lambda explícito. */
    public static List<Pedido> generarDia(Resumen resumen,
                                          double lambda,
                                          LocalDate fecha,
                                          Random random,
                                          long idInicial) {
        if (resumen == null || fecha == null || random == null)
            throw new IllegalArgumentException("Generación de demanda incompleta");
        if (lambda <= 0) throw new IllegalArgumentException("Lambda debe ser > 0");

        int cantidad = Math.max(1, poisson(random, lambda));
        List<Pedido> salida = new ArrayList<>(cantidad);

        for (int i = 0; i < cantidad; i++) {
            PedidoImportado plantilla = resumen.plantillas().get(random.nextInt(resumen.plantillas().size()));
            salida.add(desdePlantilla(plantilla, fecha, Math.addExact(idInicial, i)));
        }

        salida.sort(Comparator.comparing(Pedido::getFechaLlegada).thenComparing(Pedido::getIdPedido));
        return List.copyOf(salida);
    }

    /**
     * Genera una jornada pareada NORMAL/ALTA/CRITICA.
     *
     * Con la misma semilla, la generación máxima siempre usa P90 y cada pedido candidato
     * recibe una marca uniforme. Así:
     * NORMAL ⊆ ALTA ⊆ CRITICA.
     */
    public static List<Pedido> generarDiaPareado(Resumen resumen,
                                                 PerfilPresion perfil,
                                                 LocalDate fecha,
                                                 Random random,
                                                 long idInicial) {
        return generarDiaAcoplado(
                resumen,
                resumen.lambda(perfil),
                resumen.p90(),
                fecha,
                random,
                idInicial);
    }

    /**
     * Genera demanda acoplada para un lambda objetivo respecto de un lambda máximo.
     * Es la base del escenario de colapso, donde cada perfil comparte el mismo mundo
     * externo pero posee distinta velocidad de crecimiento.
     */
    public static List<Pedido> generarDiaAcoplado(Resumen resumen,
                                                  double lambdaObjetivo,
                                                  double lambdaMaxima,
                                                  LocalDate fecha,
                                                  Random random,
                                                  long idInicial) {
        if (resumen == null || fecha == null || random == null)
            throw new IllegalArgumentException("Generación acoplada de demanda incompleta");
        if (!Double.isFinite(lambdaObjetivo) || !Double.isFinite(lambdaMaxima)
                || lambdaObjetivo <= 0 || lambdaMaxima <= 0 || lambdaObjetivo > lambdaMaxima) {
            throw new IllegalArgumentException("Lambdas inválidos para generación acoplada");
        }

        int cantidadMaxima = Math.max(1, poisson(random, lambdaMaxima));
        double probabilidad = lambdaObjetivo / lambdaMaxima;
        List<Pedido> candidatos = new ArrayList<>(cantidadMaxima);
        List<Double> marcas = new ArrayList<>(cantidadMaxima);

        for (int i = 0; i < cantidadMaxima; i++) {
            PedidoImportado plantilla = resumen.plantillas().get(random.nextInt(resumen.plantillas().size()));
            candidatos.add(desdePlantilla(plantilla, fecha, Math.addExact(idInicial, i)));
            marcas.add(random.nextDouble());
        }

        List<Pedido> salida = new ArrayList<>();
        for (int i = 0; i < candidatos.size(); i++) {
            if (marcas.get(i) <= probabilidad) {
                salida.add(candidatos.get(i));
            }
        }

        // Con lambdas del orden de 30-50 este caso es prácticamente imposible,
        // pero se evita construir una instancia vacía por robustez.
        if (salida.isEmpty()) salida.add(candidatos.get(0));

        salida.sort(Comparator.comparing(Pedido::getFechaLlegada).thenComparing(Pedido::getIdPedido));
        return List.copyOf(salida);
    }

    private static Pedido desdePlantilla(PedidoImportado plantilla,
                                         LocalDate fecha,
                                         long id) {
        LocalDateTime llegada = LocalDateTime.of(fecha, plantilla.llegada().toLocalTime());
        return new Pedido(
                id,
                plantilla.cliente(),
                plantilla.cantidad(),
                plantilla.prioridad(),
                new Nodo(plantilla.x(), plantilla.y()),
                llegada,
                plantilla.horas());
    }

    private static double percentil(List<Integer> ordenados, double p) {
        if (ordenados.isEmpty()) throw new IllegalArgumentException("No hay observaciones para calcular percentiles");
        if (ordenados.size() == 1) return ordenados.get(0);

        double posicion = (ordenados.size() - 1) * p;
        int inferior = (int) Math.floor(posicion);
        int superior = (int) Math.ceil(posicion);
        if (inferior == superior) return ordenados.get(inferior);

        double fraccion = posicion - inferior;
        return ordenados.get(inferior)
                + fraccion * (ordenados.get(superior) - ordenados.get(inferior));
    }

    /** Poisson exacta por suma de Poisson independientes pequeñas. */
    private static int poisson(Random random, double lambda) {
        int partes = Math.max(1, (int) Math.ceil(lambda / 30.0));
        double lambdaParte = lambda / partes;
        int total = 0;
        for (int i = 0; i < partes; i++) total += poissonKnuth(random, lambdaParte);
        return total;
    }

    private static int poissonKnuth(Random random, double lambda) {
        double limite = Math.exp(-lambda);
        int k = 0;
        double producto = 1.0;
        do {
            k++;
            producto *= random.nextDouble();
        } while (producto > limite);
        return k - 1;
    }
}