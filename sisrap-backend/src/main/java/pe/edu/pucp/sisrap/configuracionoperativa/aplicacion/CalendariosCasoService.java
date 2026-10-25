package pe.edu.pucp.sisrap.configuracionoperativa.aplicacion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import pe.edu.pucp.sisrap.simulacion.dominio.EscenarioSimulacion;
import pe.edu.pucp.sisrap.configuracionoperativa.infraestructura.JdbcConfiguracionEscenario.Plantilla;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import pe.edu.pucp.sisrap.experimentacion.infraestructura.LectorArchivosExperimento;
import pe.edu.pucp.sisrap.simulacion.infraestructura.ProgramadorIncidenciasHistoricas;

@Service
public class CalendariosCasoService {
    private final Path directorio;

    public record ArchivoCalendario(String nombre, String tipo, int registros,
                                    LocalDate desde, LocalDate hasta, long bytes) { }

    public CalendariosCasoService(@Value("${sisrap.simulacion.archivos-dir:${SISRAP_DATOS_DIR:datos-caso}}")
                                 String directorio) {
        this.directorio = Path.of(directorio == null || directorio.isBlank() ? "datos-caso" : directorio.trim())
                .toAbsolutePath().normalize();
    }

    public static synchronized Path directorioEscenario(String base, EscenarioSimulacion escenario) {
        Path raiz = Path.of(base == null || base.isBlank() ? "datos-caso" : base.trim()).toAbsolutePath().normalize();
        Path contenedor = raiz.resolve("escenarios");
        Path destino = contenedor.resolve(escenario.name());
        if (Files.isDirectory(destino)) return destino;
        Path temporal = null;
        try {
            Files.createDirectories(contenedor);
            temporal = Files.createTempDirectory(contenedor, ".inicial-");
            List<Path> anteriores;
            try (Stream<Path> arbol = Files.walk(raiz)) {
                anteriores = arbol.filter(Files::isRegularFile)
                        .filter(a -> !a.startsWith(contenedor))
                        .filter(a -> tipo(a.getFileName().toString()) != null).toList();
            }
            for (Path archivo : anteriores) {
                Path copia = temporal.resolve(raiz.relativize(archivo));
                Files.createDirectories(copia.getParent());
                Files.copy(archivo, copia);
            }
            Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
            return destino;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo preparar el calendario del escenario " + escenario, e);
        } finally {
            if (temporal != null && Files.exists(temporal)) {
                try (Stream<Path> arbol = Files.walk(temporal)) {
                    for (Path archivo : arbol.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(archivo);
                } catch (IOException ignored) { }
            }
        }
    }

    private CalendariosCasoService para(EscenarioSimulacion escenario) {
        return new CalendariosCasoService(directorioEscenario(directorio.toString(), escenario).toString());
    }

    public synchronized List<ArchivoCalendario> listar(EscenarioSimulacion escenario) { return para(escenario).listar(); }
    public synchronized Prevision prevision(EscenarioSimulacion escenario, LocalDate desde, LocalDate hasta) {
        return para(escenario).prevision(desde, hasta);
    }
    public synchronized void eliminar(EscenarioSimulacion escenario, String nombre) { para(escenario).eliminar(nombre); }

    public synchronized void validarCompatibilidad(Plantilla p) {
        validarCompatibilidad(para(p.escenario()).directorio, p);
    }

    private static void validarCompatibilidad(Path ruta, Plantilla p) {
        var calendario = ProgramadorIncidenciasHistoricas.cargar(ruta);
        calendario.validarMapa(p.anchoKm(), p.altoKm(), LocalDate.MIN, LocalDate.MAX);
        Set<String> vehiculos = new HashSet<>();
        for (var f : p.flota()) {
            String prefijo = switch (f.tipo()) { case "AUTO" -> "TA"; case "MOTO" -> "TM"; case "BICICLETA" -> "TB";
                default -> throw new IllegalArgumentException("Tipo de vehículo inválido"); };
            for (int n = 1; n <= f.cantidad(); n++) vehiculos.add(prefijo + String.format(java.util.Locale.ROOT, "%02d", n));
        }
        calendario.validarVehiculos(vehiculos, LocalDate.MIN, LocalDate.MAX);
    }

    public synchronized List<ArchivoCalendario> subir(EscenarioSimulacion escenario,
            List<MultipartFile> entradas, Plantilla plantilla) {
        CalendariosCasoService destino = para(escenario);
        Path temporal = null;
        try {
            temporal = Files.createTempDirectory("sisrap-calendario-");
            for (Path anterior : destino.archivos()) {
                Path copia = temporal.resolve(destino.directorio.relativize(anterior));
                Files.createDirectories(copia.getParent());
                Files.copy(anterior, copia);
            }
            new CalendariosCasoService(temporal.toString()).subir(entradas);
            validarCompatibilidad(temporal, plantilla);
            return destino.subir(entradas);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo validar el calendario", e);
        } finally {
            if (temporal != null) {
                try (Stream<Path> arbol = Files.walk(temporal)) {
                    for (Path archivo : arbol.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(archivo);
                } catch (IOException ignored) { }
            }
        }
    }

    public synchronized List<ArchivoCalendario> listar() {
        List<ArchivoCalendario> resultado = new ArrayList<>();
        for (Path archivo : archivos()) {
            if (tipo(archivo.getFileName().toString()) == null) continue;
            resultado.add(validar(archivo.getFileName().toString(),
                    ProgramadorIncidenciasHistoricas.leer(archivo), FilesExist.size(archivo)));
        }
        return resultado.stream().sorted((a,b) -> a.nombre().compareTo(b.nombre())).toList();
    }

    public record BloqueoPrevisto(java.time.LocalDateTime inicio,
                                 java.time.LocalDateTime fin, int tramos) { }
    public record MantenimientoPrevisto(String idVehiculo,
                                         java.time.LocalDateTime inicio,
                                         java.time.LocalDateTime fin) { }
    public record Prevision(List<BloqueoPrevisto> bloqueos,
                            List<MantenimientoPrevisto> mantenimientos) { }

    public synchronized Prevision prevision(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)
                || java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) > 30) {
            throw new IllegalArgumentException("Consulta un periodo de entre uno y 31 días");
        }
        var programador = ProgramadorIncidenciasHistoricas.cargar(directorio);
        List<BloqueoPrevisto> bloqueos = new ArrayList<>();
        List<MantenimientoPrevisto> mantenimientos = new ArrayList<>();
        var inicio = desde.atStartOfDay();
        var fin = hasta.plusDays(1).atStartOfDay();
        for (LocalDate dia = desde.minusDays(1); !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            var p = programador.generarDia(desde, dia, 0L, 0, 0, Set.of());
            for (var b : p.bloqueos()) {
                if (b.inicio().isBefore(fin) && b.fin().isAfter(inicio))
                    bloqueos.add(new BloqueoPrevisto(b.inicio(), b.fin(), b.vertices().size() - 1));
            }
            for (var m : p.mantenimientos())
                mantenimientos.add(new MantenimientoPrevisto(m.idVehiculo(), m.inicio(), m.fin()));
        }
        bloqueos.sort(java.util.Comparator.comparing(BloqueoPrevisto::inicio));
        mantenimientos.sort(java.util.Comparator.comparing(MantenimientoPrevisto::inicio)
                .thenComparing(MantenimientoPrevisto::idVehiculo));
        return new Prevision(List.copyOf(bloqueos), List.copyOf(mantenimientos));
    }

    public synchronized List<ArchivoCalendario> subir(List<MultipartFile> entradas) {
        if (entradas == null || entradas.isEmpty() || entradas.size() > 60) {
            throw new IllegalArgumentException("Selecciona entre 1 y 60 archivos de incidencias");
        }
        List<ArchivoPendiente> pendientes = new ArrayList<>();
        Set<String> nombres = new HashSet<>();
        Set<String> meses = new HashSet<>();
        for (Path existente : archivos()) {
            String nombre = existente.getFileName().toString();
            if (tipo(nombre) == null) continue;
            nombres.add(nombre);
            Matcher m = ProgramadorIncidenciasHistoricas.ARCHIVO_BLOQUEO.matcher(nombre);
            if (m.matches()) meses.add(m.group(1) + m.group(2));
        }
        for (MultipartFile entrada : entradas) {
            String nombre = entrada.getOriginalFilename();
            if (nombre == null || !nombre.equals(Path.of(nombre).getFileName().toString())
                    || tipo(nombre) == null) {
                throw new IllegalArgumentException("Nombre inválido. Usa bloqueo.AAMM.txt o mant*.txt");
            }
            if (!nombres.add(nombre)) {
                throw new IllegalArgumentException("Ya existe un archivo llamado " + nombre + ". Elimínalo antes de reemplazarlo");
            }
            Matcher m = ProgramadorIncidenciasHistoricas.ARCHIVO_BLOQUEO.matcher(nombre);
            if (m.matches() && !meses.add(m.group(1) + m.group(2))) {
                throw new IllegalArgumentException("Ya existe un archivo de bloqueos para ese mes");
            }
            if (entrada.isEmpty() || entrada.getSize() > 10L * 1024 * 1024) {
                throw new IllegalArgumentException("Cada archivo debe contener datos y pesar como máximo 10 MB");
            }
            byte[] contenido;
            try { contenido = entrada.getBytes(); }
            catch (IOException e) { throw new IllegalStateException("No se pudo recibir " + nombre, e); }
            String texto = new String(contenido, java.nio.charset.StandardCharsets.UTF_8).replace("\uFEFF", "");
            ArchivoCalendario info = validar(nombre, texto, contenido.length);
            pendientes.add(new ArchivoPendiente(nombre, contenido, info));
        }
        List<Path> creados = new ArrayList<>();
        try {
            Files.createDirectories(directorio);
            List<ArchivoCalendario> respuesta = new ArrayList<>();
            for (ArchivoPendiente pendiente : pendientes) {
                Path destino = directorio.resolve(pendiente.nombre());
                // CREATE_NEW evita sustituir otro archivo si hubo una carga concurrente.
                Files.write(destino, pendiente.contenido(), java.nio.file.StandardOpenOption.CREATE_NEW);
                creados.add(destino);
                respuesta.add(pendiente.info());
            }
            return List.copyOf(respuesta);
        } catch (IOException e) {
            for (Path creado : creados) {
                try { Files.deleteIfExists(creado); } catch (IOException ignored) { }
            }
            throw new IllegalStateException("No se pudieron guardar los calendarios en " + directorio, e);
        }
    }

    public synchronized void eliminar(String nombre) {
        if (nombre == null || !nombre.equals(Path.of(nombre).getFileName().toString())
                || tipo(nombre) == null) throw new IllegalArgumentException("Nombre de calendario inválido");
        List<Path> encontrados = archivos().stream()
                .filter(a -> a.getFileName().toString().equals(nombre)).toList();
        if (encontrados.isEmpty()) throw new NoSuchElementException("No existe el archivo " + nombre);
        if (encontrados.size() != 1) throw new IllegalStateException("Hay archivos duplicados: " + nombre);
        try { Files.delete(encontrados.getFirst()); }
        catch (IOException e) { throw new IllegalStateException("No se pudo eliminar " + nombre, e); }
    }

    private record ArchivoPendiente(String nombre, byte[] contenido, ArchivoCalendario info) { }

    private static String tipo(String nombre) {
        if (ProgramadorIncidenciasHistoricas.ARCHIVO_BLOQUEO.matcher(nombre).matches()) return "BLOQUEO";
        if (ProgramadorIncidenciasHistoricas.ARCHIVO_MANTENIMIENTO.matcher(nombre).matches()) return "MANTENIMIENTO";
        return null;
    }

    private static ArchivoCalendario validar(String nombre, String contenido, long bytes) {
        Matcher m = ProgramadorIncidenciasHistoricas.ARCHIVO_BLOQUEO.matcher(nombre);
        if (m.matches()) {
            YearMonth mes = YearMonth.of(2000 + Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            var eventos = LectorArchivosExperimento.parsearBloqueos(mes, contenido);
            if (eventos.isEmpty()) throw new IllegalArgumentException("El archivo " + nombre + " no contiene bloqueos");
            LocalDate inicio = eventos.stream().map(b -> b.inicio().toLocalDate()).min(LocalDate::compareTo).orElseThrow();
            LocalDate fin = eventos.stream().map(b -> b.fin().toLocalDate()).max(LocalDate::compareTo).orElseThrow();
            return new ArchivoCalendario(nombre, "BLOQUEO", eventos.size(), inicio, fin, bytes);
        }
        if (!ProgramadorIncidenciasHistoricas.ARCHIVO_MANTENIMIENTO.matcher(nombre).matches()) {
            throw new IllegalArgumentException("El nombre de archivo no corresponde a un calendario válido");
        }
        int cantidad = 0;
        LocalDate minimo = null, maximo = null;
        for (String linea : contenido.split("\\R")) {
            if (linea.isBlank()) continue;
            Matcher fila = ProgramadorIncidenciasHistoricas.LINEA_MANTENIMIENTO.matcher(linea.strip());
            if (!fila.matches() || !fila.group(4).toUpperCase(java.util.Locale.ROOT).matches("(TA|TB|TM)\\d+")) {
                throw new IllegalArgumentException("Registro de mantenimiento inválido en " + nombre + ": " + linea);
            }
            LocalDate fecha = LocalDate.of(Integer.parseInt(fila.group(1)),
                    Integer.parseInt(fila.group(2)), Integer.parseInt(fila.group(3)));
            minimo = minimo == null || fecha.isBefore(minimo) ? fecha : minimo;
            maximo = maximo == null || fecha.isAfter(maximo) ? fecha : maximo;
            cantidad++;
        }
        if (cantidad == 0) throw new IllegalArgumentException("El archivo " + nombre + " no contiene mantenimientos");
        return new ArchivoCalendario(nombre, "MANTENIMIENTO", cantidad, minimo, maximo, bytes);
    }

    private List<Path> archivos() {
        if (!Files.isDirectory(directorio)) return List.of();
        try (Stream<Path> arbol = Files.walk(directorio)) {
            return arbol.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el directorio " + directorio, e);
        }
    }

    private static final class FilesExist {
        static long size(Path archivo) {
            try { return Files.size(archivo); }
            catch (IOException e) { throw new IllegalStateException("No se pudo leer el archivo " + archivo, e); }
        }
    }
}
