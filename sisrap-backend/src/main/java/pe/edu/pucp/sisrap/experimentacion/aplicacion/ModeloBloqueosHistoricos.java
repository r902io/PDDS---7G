package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo;
import pe.edu.pucp.sisrap.experimentacion.dominio.DatosArchivos;
import pe.edu.pucp.sisrap.experimentacion.dominio.PerfilPresion;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

/**
 * Modelo simple para generar bloqueos sintéticos a partir del histórico entregado.
 *
 * La cantidad diaria se calibra con la misma ventana histórica usada para la demanda:
 * NORMAL=P50, ALTA=P75 y CRITICA=P90 de bloqueos que comienzan por día.
 *
 * Cada bloqueo sintético toma una plantilla histórica y conserva:
 * - hora de inicio dentro del día;
 * - duración;
 * - forma de la poligonal abierta.
 *
 * La poligonal se traslada a una ubicación aleatoria válida dentro de la retícula de
 * PaqRap. De esta forma no se reutilizan necesariamente las mismas calles históricas,
 * pero sí se conserva el patrón geométrico observado.
 */
public final class ModeloBloqueosHistoricos {
    private ModeloBloqueosHistoricos() {}

    private record PuntoRelativo(int dx, int dy) {}

    private record Plantilla(int minutoInicio,
                             long duracionMinutos,
                             List<PuntoRelativo> verticesRelativos) {
        private Plantilla {
            if (minutoInicio < 0 || minutoInicio >= 24 * 60)
                throw new IllegalArgumentException("Hora de inicio de bloqueo inválida");
            if (duracionMinutos <= 0)
                throw new IllegalArgumentException("Duración de bloqueo inválida");
            verticesRelativos = List.copyOf(verticesRelativos);
            if (verticesRelativos.size() < 2)
                throw new IllegalArgumentException("Plantilla de bloqueo sin tramos");
        }
    }

    public record Resumen(LocalDate desde,
                          LocalDate hasta,
                          int dias,
                          int p50,
                          int p75,
                          int p90,
                          int plantillasDisponibles,
                          List<Plantilla> plantillas) {
        public Resumen {
            if (desde == null || hasta == null || hasta.isBefore(desde))
                throw new IllegalArgumentException("Periodo histórico de bloqueos inválido");
            if (dias < 1 || p50 < 0 || p75 < p50 || p90 < p75)
                throw new IllegalArgumentException("Estadísticas históricas de bloqueos inválidas");
            plantillas = List.copyOf(plantillas);
            if (plantillas.isEmpty())
                throw new IllegalArgumentException("No existen bloqueos históricos para construir plantillas");
            if (plantillasDisponibles != plantillas.size())
                throw new IllegalArgumentException("Cantidad de plantillas inconsistente");
        }

        public int lambda(PerfilPresion perfil) {
            return switch (perfil) {
                case NORMAL -> p50;
                case ALTA -> p75;
                case CRITICA -> p90;
            };
        }
    }

    public static Resumen analizar(DatosArchivos datos,
                                   LocalDate desde,
                                   LocalDate hasta) {
        if (datos == null || datos.bloqueos().isEmpty())
            throw new IllegalArgumentException("No existen bloqueos históricos para estimar la presión vial");
        if (desde == null || hasta == null || hasta.isBefore(desde))
            throw new IllegalArgumentException("Periodo histórico de bloqueos inválido");

        int dias = Math.toIntExact(ChronoUnit.DAYS.between(desde, hasta) + 1L);
        Map<LocalDate, Integer> cantidadPorDia = new HashMap<>();
        List<Plantilla> plantillas = new ArrayList<>();

        for (Bloqueo bloqueo : datos.bloqueos()) {
            LocalDate fecha = bloqueo.inicio().toLocalDate();
            if (fecha.isBefore(desde) || fecha.isAfter(hasta)) continue;

            cantidadPorDia.merge(fecha, 1, Integer::sum);
            plantillas.add(aPlantilla(bloqueo));
        }

        if (plantillas.isEmpty()) {
            throw new IllegalArgumentException(
                    "No existen bloqueos dentro del periodo histórico " + desde + " a " + hasta);
        }

        List<Integer> cantidades = new ArrayList<>(dias);
        for (int i = 0; i < dias; i++) {
            cantidades.add(cantidadPorDia.getOrDefault(desde.plusDays(i), 0));
        }
        cantidades.sort(Integer::compareTo);

        int p50 = Math.max(0, (int) Math.round(percentil(cantidades, 0.50)));
        int p75 = Math.max(p50, (int) Math.round(percentil(cantidades, 0.75)));
        int p90 = Math.max(p75, (int) Math.round(percentil(cantidades, 0.90)));

        return new Resumen(
                desde,
                hasta,
                dias,
                p50,
                p75,
                p90,
                plantillas.size(),
                plantillas);
    }

    public static List<Bloqueo> generarDia(Resumen resumen,
                                            PerfilPresion perfil,
                                            LocalDate fecha,
                                            Random random,
                                            int anchoKm,
                                            int altoKm) {
        return generarDia(
                resumen,
                resumen.lambda(perfil),
                fecha,
                random,
                anchoKm,
                altoKm);
    }

    public static List<Bloqueo> generarDia(Resumen resumen,
                                            double lambda,
                                            LocalDate fecha,
                                            Random random,
                                            int anchoKm,
                                            int altoKm) {
        if (resumen == null || fecha == null || random == null)
            throw new IllegalArgumentException("Generación de bloqueos incompleta");
        if (lambda < 0)
            throw new IllegalArgumentException("Lambda de bloqueos no puede ser negativo");
        if (anchoKm < 1 || altoKm < 1)
            throw new IllegalArgumentException("Dimensiones de ciudad inválidas");

        int cantidad = poisson(random, lambda);
        List<Bloqueo> salida = new ArrayList<>(cantidad);

        for (int i = 0; i < cantidad; i++) {
            Plantilla plantilla = resumen.plantillas().get(
                    random.nextInt(resumen.plantillas().size()));

            LocalDateTime inicio = fecha.atStartOfDay().plusMinutes(plantilla.minutoInicio());
            LocalDateTime fin = inicio.plusMinutes(plantilla.duracionMinutos());
            List<Nodo> vertices = trasladar(
                    plantilla.verticesRelativos(),
                    random,
                    anchoKm,
                    altoKm);

            salida.add(new Bloqueo(inicio, fin, vertices));
        }

        salida.sort(Comparator.comparing(Bloqueo::inicio).thenComparing(Bloqueo::fin));
        return List.copyOf(salida);
    }

    private static Plantilla aPlantilla(Bloqueo bloqueo) {
        Nodo origen = bloqueo.vertices().get(0);
        List<PuntoRelativo> relativos = bloqueo.vertices().stream()
                .map(n -> new PuntoRelativo(
                        n.getX() - origen.getX(),
                        n.getY() - origen.getY()))
                .toList();

        int minutoInicio = bloqueo.inicio().getHour() * 60 + bloqueo.inicio().getMinute();
        long duracion = Duration.between(bloqueo.inicio(), bloqueo.fin()).toMinutes();
        return new Plantilla(minutoInicio, duracion, relativos);
    }

    private static List<Nodo> trasladar(List<PuntoRelativo> relativos,
                                        Random random,
                                        int anchoKm,
                                        int altoKm) {
        int minDx = relativos.stream().mapToInt(PuntoRelativo::dx).min().orElseThrow();
        int maxDx = relativos.stream().mapToInt(PuntoRelativo::dx).max().orElseThrow();
        int minDy = relativos.stream().mapToInt(PuntoRelativo::dy).min().orElseThrow();
        int maxDy = relativos.stream().mapToInt(PuntoRelativo::dy).max().orElseThrow();

        int minimoXOrigen = -minDx;
        int maximoXOrigen = anchoKm - maxDx;
        int minimoYOrigen = -minDy;
        int maximoYOrigen = altoKm - maxDy;

        if (minimoXOrigen > maximoXOrigen || minimoYOrigen > maximoYOrigen) {
            throw new IllegalArgumentException(
                    "Una plantilla histórica de bloqueo no cabe dentro de la ciudad "
                            + anchoKm + "x" + altoKm);
        }

        int origenX = aleatorioInclusivo(random, minimoXOrigen, maximoXOrigen);
        int origenY = aleatorioInclusivo(random, minimoYOrigen, maximoYOrigen);

        return relativos.stream()
                .map(p -> new Nodo(origenX + p.dx(), origenY + p.dy()))
                .toList();
    }

    private static int aleatorioInclusivo(Random random, int minimo, int maximo) {
        if (minimo == maximo) return minimo;
        return minimo + random.nextInt(maximo - minimo + 1);
    }

    private static double percentil(List<Integer> ordenados, double p) {
        if (ordenados.isEmpty())
            throw new IllegalArgumentException("No hay observaciones para calcular percentiles de bloqueos");
        if (ordenados.size() == 1) return ordenados.get(0);

        double posicion = (ordenados.size() - 1) * p;
        int inferior = (int) Math.floor(posicion);
        int superior = (int) Math.ceil(posicion);
        if (inferior == superior) return ordenados.get(inferior);

        double fraccion = posicion - inferior;
        return ordenados.get(inferior)
                + fraccion * (ordenados.get(superior) - ordenados.get(inferior));
    }

    private static int poisson(Random random, double lambda) {
        if (lambda == 0.0) return 0;

        int partes = Math.max(1, (int) Math.ceil(lambda / 30.0));
        double lambdaParte = lambda / partes;
        int total = 0;
        for (int i = 0; i < partes; i++) {
            total += poissonKnuth(random, lambdaParte);
        }
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