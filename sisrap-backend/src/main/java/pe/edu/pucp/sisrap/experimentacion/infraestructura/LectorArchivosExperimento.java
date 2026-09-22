package pe.edu.pucp.sisrap.experimentacion.infraestructura;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
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

import pe.edu.pucp.sisrap.carga.aplicacion.ImportarPedidos;
import pe.edu.pucp.sisrap.carga.dominio.PedidoImportado;
import pe.edu.pucp.sisrap.carga.dominio.ReglasCarga;
import pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo;
import pe.edu.pucp.sisrap.experimentacion.dominio.DatosArchivos;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

/**
 * Recopila los .txt de la carpeta de datos:
 * <ul>
 *   <li>ventas: <code>ventas.AAAAMM.txt</code> (cualquier subcarpeta; el parseo es el mismo de ImportarPedidos)</li>
 *   <li>bloqueos: <code>bloqueo.AAMM.txt</code></li>
 *   <li>mantenimiento preventivo: <code>mant*.txt</code>, líneas <code>AAAAMMDD:ID_VEHICULO</code></li>
 * </ul>
 */
public final class LectorArchivosExperimento {
    private static final Pattern VENTAS = Pattern.compile("ventas\\.(\\d{4})(\\d{2})\\.txt");
    private static final Pattern BLOQUEOS = Pattern.compile("bloqueo\\.(\\d{2})(\\d{2})\\.txt");
    private static final Pattern MANTENIMIENTO = Pattern.compile("mant.*\\.txt");
    private static final Pattern LINEA_BLOQUEO =
            Pattern.compile("(\\d{2})d(\\d{2})h(\\d{2})m-(\\d{2})d(\\d{2})h(\\d{2})m:(\\d+(?:,\\d+)*)");
    private static final Pattern LINEA_MANTENIMIENTO = Pattern.compile("(\\d{4})(\\d{2})(\\d{2}):([A-Za-z0-9_-]+)");

    private final Path carpeta;
    private final ReglasCarga reglas;
    private final List<String> advertencias = new ArrayList<>();
    private final List<String> archivosLeidos = new ArrayList<>();

    public LectorArchivosExperimento(Path carpeta, ReglasCarga reglas) {
        this.carpeta = carpeta;
        this.reglas = reglas;
    }

    /** Primer día del mes más antiguo que tenga archivo de ventas. */
    public LocalDate primerDiaDisponible() {
        return buscar(VENTAS).stream().map(p -> {
            Matcher m = VENTAS.matcher(p.getFileName().toString());
            m.matches();
            return YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))).atDay(1);
        }).min(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalArgumentException("No hay archivos ventas.AAAAMM.txt bajo " + carpeta.toAbsolutePath()));
    }

    /**
     * Lee ventas desde {@code desde} y avanza mes a mes hasta reunir {@code pedidosNecesarios}.
     * Los bloqueos se leen para los mismos meses; el mantenimiento se lee completo.
     */
    public DatosArchivos leer(LocalDate desde, int pedidosNecesarios) {
        Map<YearMonth, Path> ventas = indexar(VENTAS, m -> YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        Map<YearMonth, Path> bloqueos = indexar(BLOQUEOS, m -> YearMonth.of(2000 + Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        if (ventas.isEmpty()) throw new IllegalArgumentException("No hay archivos ventas.AAAAMM.txt bajo " + carpeta.toAbsolutePath());
        YearMonth mes = YearMonth.from(desde);
        if (!ventas.containsKey(mes))
            throw new IllegalArgumentException("No hay archivo de ventas para " + mes + ". Disponibles: "
                    + ventas.keySet().stream().min(Comparator.naturalOrder()).orElseThrow() + " a "
                    + ventas.keySet().stream().max(Comparator.naturalOrder()).orElseThrow());

        List<PedidoImportado> pedidos = new ArrayList<>();
        List<Bloqueo> listaBloqueos = new ArrayList<>();
        while (pedidos.size() < pedidosNecesarios && ventas.containsKey(mes)) {
            for (PedidoImportado p : ImportarPedidos.parsear(mes, leerTexto(ventas.get(mes)), reglas))
                if (!p.llegada().isBefore(desde.atStartOfDay())) pedidos.add(p);
            if (bloqueos.containsKey(mes)) listaBloqueos.addAll(parsearBloqueos(mes, leerTexto(bloqueos.get(mes))));
            else advertencias.add("No hay archivo de bloqueos para " + mes);
            mes = mes.plusMonths(1);
        }
        pedidos.sort(Comparator.comparing(PedidoImportado::llegada));
        Map<LocalDate, Set<String>> mantenimiento = leerMantenimiento();
        return new DatosArchivos(pedidos, listaBloqueos, mantenimiento, archivosLeidos, advertencias);
    }

    private Map<LocalDate, Set<String>> leerMantenimiento() {
        Map<LocalDate, Set<String>> mantenimiento = new HashMap<>();
        for (Path archivo : buscar(MANTENIMIENTO)) {
            int numero = 0;
            for (String linea : leerTexto(archivo).split("\\R")) {
                numero++;
                if (linea.isBlank()) continue;
                Matcher m = LINEA_MANTENIMIENTO.matcher(linea.strip());
                if (!m.matches()) throw new IllegalArgumentException("Mantenimiento inválido en " + archivo.getFileName() + ":" + numero);
                LocalDate fecha = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                mantenimiento.computeIfAbsent(fecha, f -> new HashSet<>()).add(m.group(4));
            }
        }
        // ponytail: autos 2 días (TA fecha+1), motos/bicis 1 día (bici 1 turno ≈ 1 día declarado).
        // Bimensual se repite generando más archivos mant*.txt, no en código.
        Map<LocalDate, Set<String>> extra = new HashMap<>();
        mantenimiento.forEach((fecha, ids) -> {
            for (String id : ids)
                if (id.startsWith("TA"))
                    extra.computeIfAbsent(fecha.plusDays(1), f -> new HashSet<>()).add(id);
        });
        extra.forEach((fecha, ids) -> mantenimiento.computeIfAbsent(fecha, f -> new HashSet<>()).addAll(ids));
        if (mantenimiento.isEmpty()) advertencias.add("No se encontró archivo de mantenimiento preventivo (mant*.txt)");
        return mantenimiento;
    }

    public static List<Bloqueo> parsearBloqueos(YearMonth mes, String texto) {
        List<Bloqueo> salida = new ArrayList<>();
        int numero = 0;
        for (String linea : texto.split("\\R")) {
            numero++;
            if (linea.isBlank()) continue;
            Matcher m = LINEA_BLOQUEO.matcher(linea.strip());
            if (!m.matches()) throw new IllegalArgumentException("Bloqueo inválido en línea " + numero);
            try {
                String[] coordenadas = m.group(7).split(",");
                if (coordenadas.length % 2 != 0) throw new IllegalArgumentException("Cantidad impar de coordenadas");
                List<Nodo> vertices = new ArrayList<>();
                for (int i = 0; i < coordenadas.length; i += 2)
                    vertices.add(new Nodo(Integer.parseInt(coordenadas[i]), Integer.parseInt(coordenadas[i + 1])));
                LocalDateTime inicio = mes.atDay(Integer.parseInt(m.group(1))).atTime(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                LocalDateTime fin = mes.atDay(Integer.parseInt(m.group(4))).atTime(Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)));
                salida.add(new Bloqueo(inicio, fin, vertices));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Bloqueo inválido en línea " + numero + ": " + e.getMessage(), e);
            }
        }
        return salida;
    }

    private String leerTexto(Path archivo) {
        try {
            archivosLeidos.add(archivo.toString());
            return Files.readString(archivo, StandardCharsets.UTF_8).replace("\uFEFF", "").replace("\r\n", "\n").strip();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + archivo, e);
        }
    }

    /** Si un mismo mes aparece en varias carpetas (p. ej. versiones), gana la de nombre mayor y se advierte. */
    private <K> Map<K, Path> indexar(Pattern patron, java.util.function.Function<Matcher, K> clave) {
        Map<K, Path> indice = new HashMap<>();
        for (Path archivo : buscar(patron)) {
            Matcher m = patron.matcher(archivo.getFileName().toString());
            m.matches();
            K llave = clave.apply(m);
            Path previo = indice.put(llave, archivo);
            if (previo != null) advertencias.add("Archivo duplicado para " + llave + ": se usa " + archivo + " (ignorado " + previo + ")");
        }
        return indice;
    }

    private List<Path> buscar(Pattern patron) {
        try (Stream<Path> arbol = Files.walk(carpeta, FileVisitOption.FOLLOW_LINKS)) {
            return arbol.filter(Files::isRegularFile)
                    .filter(p -> patron.matcher(p.getFileName().toString()).matches())
                    .sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo recorrer la carpeta de datos " + carpeta, e);
        }
    }
}