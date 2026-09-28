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
 *
 * Los pedidos simulados se generan con una Poisson(lambda) para la cantidad diaria y,
 * para cada pedido, se toma con reemplazo una plantilla histórica del periodo base.
 * Se conservan cantidad, prioridad, plazo, cliente, ubicación y hora del día.
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
                          int incrementoColapso,
                          List<PedidoImportado> plantillas) {
        public Resumen {
            if (desde == null || hasta == null || hasta.isBefore(desde))
                throw new IllegalArgumentException("Periodo histórico inválido");
            if (dias < 1 || p50 < 1 || p75 < 1 || p90 < 1 || incrementoColapso < 1)
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
        int incremento = Math.max(1, p75 - p50);

        return new Resumen(desde, hasta, dias, p50, p75, p90, incremento, plantillas);
    }

    /** Genera un día usando el lambda asociado al perfil. */
    public static List<Pedido> generarDia(Resumen resumen,
                                          PerfilPresion perfil,
                                          LocalDate fecha,
                                          Random random,
                                          long idInicial) {
        return generarDia(resumen, resumen.lambda(perfil), fecha, random, idInicial);
    }

    /** Genera un día con lambda explícito; se usa para la presión creciente de colapso. */
    public static List<Pedido> generarDia(Resumen resumen,
                                          double lambda,
                                          LocalDate fecha,
                                          Random random,
                                          long idInicial) {
        if (lambda <= 0) throw new IllegalArgumentException("Lambda debe ser > 0");
        if (fecha == null || random == null) throw new IllegalArgumentException("Generación de demanda incompleta");

        int cantidad = Math.max(1, poisson(random, lambda));
        List<Pedido> salida = new ArrayList<>(cantidad);

        for (int i = 0; i < cantidad; i++) {
            PedidoImportado plantilla = resumen.plantillas().get(random.nextInt(resumen.plantillas().size()));
            LocalDateTime llegada = LocalDateTime.of(fecha, plantilla.llegada().toLocalTime());
            salida.add(new Pedido(
                    Math.addExact(idInicial, i),
                    plantilla.cliente(),
                    plantilla.cantidad(),
                    plantilla.prioridad(),
                    new Nodo(plantilla.x(), plantilla.y()),
                    llegada,
                    plantilla.horas()));
        }

        salida.sort(Comparator.comparing(Pedido::getFechaLlegada).thenComparing(Pedido::getIdPedido));
        return List.copyOf(salida);
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

    /**
     * Poisson exacta por suma de Poisson independientes pequeñas.
     * Evita problemas numéricos de Knuth cuando lambda es grande.
     */
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