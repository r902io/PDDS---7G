package pe.edu.pucp.sisrap.experimentacion.infraestructura;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Lee el .env de la raíz del backend (mismo criterio que Db.java); las variables del sistema tienen prioridad. */
public final class VariablesEntorno {
    private final Map<String, String> valores = new HashMap<>();

    public VariablesEntorno(Path archivoEnv) {
        if (Files.exists(archivoEnv)) {
            try {
                for (String linea : Files.readAllLines(archivoEnv, StandardCharsets.UTF_8)) {
                    linea = linea.trim();
                    int i = linea.indexOf('=');
                    if (linea.isEmpty() || linea.startsWith("#") || i < 0) continue;
                    String valor = linea.substring(i + 1).trim();
                    if (valor.length() >= 2 && (valor.startsWith("\"") || valor.startsWith("'")) && valor.endsWith(valor.substring(0, 1)))
                        valor = valor.substring(1, valor.length() - 1);
                    valores.put(linea.substring(0, i).trim(), valor);
                }
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo leer " + archivoEnv, e);
            }
        }
        valores.putAll(System.getenv());
    }

    public String requerida(String nombre) {
        String valor = valores.get(nombre);
        if (valor == null || valor.isBlank())
            throw new IllegalArgumentException("Falta " + nombre + " en el .env (raíz del backend) o en las variables de entorno");
        return valor;
    }
}