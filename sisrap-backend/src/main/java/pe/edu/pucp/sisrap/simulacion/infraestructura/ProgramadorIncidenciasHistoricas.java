package pe.edu.pucp.sisrap.simulacion.infraestructura;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo;
import pe.edu.pucp.sisrap.experimentacion.infraestructura.LectorArchivosExperimento;

/** Calendario exacto de incidencias: no se generan ni trasladan fechas históricas. */
public final class ProgramadorIncidenciasHistoricas {
    public static final Pattern ARCHIVO_BLOQUEO = Pattern.compile("bloqueo\\.(\\d{2})(\\d{2})\\.txt");
    public static final Pattern ARCHIVO_MANTENIMIENTO = Pattern.compile("mant.*\\.txt");
    public static final Pattern LINEA_MANTENIMIENTO =
            Pattern.compile("(\\d{4})(\\d{2})(\\d{2}):([A-Za-z0-9_-]+)");

    public record MantenimientoProgramado(String idVehiculo, LocalDateTime inicio, LocalDateTime fin) { }
    public record Programacion(List<Bloqueo> bloqueos, List<MantenimientoProgramado> mantenimientos) {
        public Programacion {
            bloqueos = List.copyOf(bloqueos);
            mantenimientos = List.copyOf(mantenimientos);
        }
    }

    private final Map<LocalDate, List<Bloqueo>> bloqueosPorFecha;
    private final long diasAntelacionBloqueos;
    private final Map<LocalDate, List<MantenimientoProgramado>> mantenimientosPorFecha;

    private ProgramadorIncidenciasHistoricas(Map<LocalDate, List<Bloqueo>> bloqueos,
                                             Map<LocalDate, List<MantenimientoProgramado>> mantenimientos) {
        this.bloqueosPorFecha = Map.copyOf(bloqueos);
        this.diasAntelacionBloqueos = Math.max(1L, bloqueos.values().stream().flatMap(List::stream)
                .mapToLong(b -> java.time.temporal.ChronoUnit.DAYS.between(
                        b.inicio().toLocalDate(), b.fin().toLocalDate()) + 1).max().orElse(1));
        this.mantenimientosPorFecha = Map.copyOf(mantenimientos);
    }

    public long diasAntelacionBloqueos() { return diasAntelacionBloqueos; }

    public static ProgramadorIncidenciasHistoricas cargar(Path directorio) {
        if (directorio == null || !Files.isDirectory(directorio)) {
            return new ProgramadorIncidenciasHistoricas(Map.of(), Map.of());
        }
        List<Path> archivos;
        try (Stream<Path> recorrido = Files.walk(directorio)) {
            archivos = recorrido.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo consultar el calendario de incidencias", e);
        }
        Map<LocalDate, List<Bloqueo>> bloqueos = new HashMap<>();
        Map<LocalDate, List<MantenimientoProgramado>> mantenimientos = new HashMap<>();
        Set<String> meses = new HashSet<>();
        Set<String> mantenimientosUnicos = new HashSet<>();
        for (Path archivo : archivos) {
            String nombre = archivo.getFileName().toString();
            Matcher matcher = ARCHIVO_BLOQUEO.matcher(nombre);
            if (matcher.matches()) {
                YearMonth mes = YearMonth.of(2000 + Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)));
                if (!meses.add(mes.toString())) {
                    throw new IllegalArgumentException("Hay más de un archivo de bloqueos para " + mes);
                }
                for (Bloqueo bloqueo : LectorArchivosExperimento.parsearBloqueos(mes, leer(archivo))) {
                    bloqueos.computeIfAbsent(bloqueo.inicio().toLocalDate(), d -> new ArrayList<>())
                            .add(bloqueo);
                }
            } else if (ARCHIVO_MANTENIMIENTO.matcher(nombre).matches()) {
                int numero = 0;
                for (String linea : leer(archivo).split("\\R")) {
                    numero++;
                    if (linea.isBlank()) continue;
                    Matcher m = LINEA_MANTENIMIENTO.matcher(linea.strip());
                    if (!m.matches()) {
                        throw new IllegalArgumentException("Mantenimiento inválido en " + nombre + ":" + numero);
                    }
                    LocalDate fecha = LocalDate.of(Integer.parseInt(m.group(1)),
                            Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                    String id = m.group(4).toUpperCase(java.util.Locale.ROOT);
                    if (!id.startsWith("TA") && !id.startsWith("TB") && !id.startsWith("TM")) {
                        throw new IllegalArgumentException("Vehículo inválido en " + nombre + ":" + numero);
                    }
                    // El calendario indica indisponibilidad en el día indicado,
                    // desde las 00:00 hasta el comienzo del siguiente día.
                    if (!mantenimientosUnicos.add(fecha + ":" + id)) {
                        throw new IllegalArgumentException("Mantenimiento duplicado de " + id + " para " + fecha);
                    }
                    LocalDateTime inicio = fecha.atStartOfDay();
                    mantenimientos.computeIfAbsent(fecha, d -> new ArrayList<>())
                            .add(new MantenimientoProgramado(id, inicio, fecha.plusDays(1).atStartOfDay()));
                }
            }
        }
        return new ProgramadorIncidenciasHistoricas(bloqueos, mantenimientos);
    }

    public static String leer(Path archivo) {
        try {
            return Files.readString(archivo, StandardCharsets.UTF_8).replace("\uFEFF", "");
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + archivo, e);
        }
    }

    public void validarVehiculos(Set<String> idsVehiculos, LocalDate desde, LocalDate hasta) {
        mantenimientosPorFecha.forEach((fecha, lista) -> {
            if (fecha.isBefore(desde) || fecha.isAfter(hasta)) return;
            for (MantenimientoProgramado m : lista) {
                if (!idsVehiculos.contains(m.idVehiculo())) {
                    throw new IllegalArgumentException("El mantenimiento de " + m.idVehiculo()
                            + " del " + fecha + " no corresponde a la flota configurada");
                }
            }
        });
    }

    public void validarMapa(int ancho, int alto, LocalDate desde, LocalDate hasta) {
        bloqueosPorFecha.forEach((fecha, lista) -> {
            if (fecha.isBefore(desde) || fecha.isAfter(hasta)) return;
            for (Bloqueo b : lista) for (var nodo : b.vertices()) {
                if (nodo.getX() < 0 || nodo.getX() > ancho || nodo.getY() < 0 || nodo.getY() > alto) {
                    throw new IllegalArgumentException("Bloqueo del " + fecha + " fuera de la ciudad: ("
                            + nodo.getX() + "," + nodo.getY() + ")");
                }
            }
        });
    }

    public Programacion generarDia(LocalDate inicioSimulacion, LocalDate dia, long semilla,
                                   int ancho, int alto, Set<String> idsVehiculos) {
        return new Programacion(bloqueosPorFecha.getOrDefault(dia, List.of()),
                mantenimientosPorFecha.getOrDefault(dia, List.of()));
    }
}
