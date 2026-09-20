import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Db {

    static final Path RAIZ = Paths.get("").toAbsolutePath();
    static final Path CARPETA = RAIZ.resolve("src/main/resources/db/migration");

    static final String AYUDA = """
        Uso:
          java Db.java nueva <descripcion>   crea V<fecha>__<descripcion>.sql
          java Db.java deploy                aplica las migraciones pendientes en la base del .env
          java Db.java info                  muestra el estado de las migraciones
          java Db.java validate              valida los checksums
        """;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println(AYUDA);
            return;
        }
        if (!Files.exists(RAIZ.resolve("pom.xml"))) {
            salir("Ejecuta este comando desde la raiz del backend (donde esta el pom.xml)");
        }
        switch (args[0]) {
            case "nueva" -> crear(args.length > 1 ? args[1] : "");
            case "deploy" -> flyway("migrate");
            case "info", "validate" -> flyway(args[0]);
            default -> salir("Comando desconocido: " + args[0] + "\n" + AYUDA);
        }
    }

    static void crear(String nombre) throws IOException {
        if (!nombre.matches("[a-z0-9_]+")) {
            salir("Nombre invalido: usa solo minusculas, numeros y guion bajo");
        }
        Files.createDirectories(CARPETA);
        String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        Path archivo = CARPETA.resolve("V" + fecha + "__" + nombre + ".sql");
        if (Files.exists(archivo)) {
            salir("Ya existe: " + archivo);
        }
        Files.writeString(archivo, "-- " + nombre + "\n", StandardCharsets.UTF_8);
        System.out.println("Creado: " + archivo);
    }

    static Map<String, String> leerEnv() throws IOException {
        Path ruta = RAIZ.resolve(".env");
        if (!Files.exists(ruta)) {
            salir("Falta el archivo .env en la raiz del backend");
        }
        Map<String, String> valores = new HashMap<>();
        for (String linea : Files.readAllLines(ruta, StandardCharsets.UTF_8)) {
            linea = linea.trim();
            int i = linea.indexOf('=');
            if (linea.isEmpty() || linea.startsWith("#") || i < 0) continue;
            String valor = linea.substring(i + 1).trim();
            if (valor.length() >= 2 && (valor.startsWith("\"") || valor.startsWith("'"))
                    && valor.endsWith(valor.substring(0, 1))) {
                valor = valor.substring(1, valor.length() - 1);
            }
            valores.put(linea.substring(0, i).trim(), valor);
        }
        for (String k : new String[] {"DB_URL", "DB_USERNAME", "DB_PASSWORD"}) {
            if (!valores.containsKey(k) || valores.get(k).isEmpty()) {
                salir("Falta " + k + " en el .env");
            }
        }
        return valores;
    }

    static void flyway(String objetivo) throws Exception {
        Map<String, String> env = leerEnv();
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        boolean hayWrapper = Files.exists(RAIZ.resolve(win ? "mvnw.cmd" : "mvnw"));

        List<String> cmd = new ArrayList<>();
        if (win) {
            cmd.add("cmd");
            cmd.add("/c");
            cmd.add(hayWrapper ? ".\\mvnw.cmd" : "mvn");
        } else if (hayWrapper) {
            cmd.add("sh");
            cmd.add("mvnw");
        } else {
            cmd.add("mvn");
        }
        cmd.add("flyway:" + objetivo);

        ProcessBuilder pb = new ProcessBuilder(cmd).directory(RAIZ.toFile()).inheritIO();
        pb.environment().put("FLYWAY_URL", env.get("DB_URL"));
        pb.environment().put("FLYWAY_USER", env.get("DB_USERNAME"));
        pb.environment().put("FLYWAY_PASSWORD", env.get("DB_PASSWORD"));
        System.exit(pb.start().waitFor());
    }

    static void salir(String msg) {
        System.err.println(msg);
        System.exit(1);
    }
}
