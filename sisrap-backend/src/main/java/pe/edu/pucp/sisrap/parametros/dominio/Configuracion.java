package pe.edu.pucp.sisrap.parametros.dominio;
import java.util.Map;
import java.util.TreeMap;

public record Configuracion(Map<String, String> valores) {
    public Configuracion { valores = Map.copyOf(new TreeMap<>(valores)); }
    public String texto(String clave) {
        String valor = valores.get(clave);
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException("Falta parámetro: " + clave);
        return valor;
    }
    public int entero(String clave) { return Integer.parseInt(texto(clave)); }
    public long largo(String clave) { return Long.parseLong(texto(clave)); }
    public double numero(String clave) {
        double valor = Double.parseDouble(texto(clave));
        if (!Double.isFinite(valor)) throw new IllegalArgumentException("Parámetro no finito: " + clave);
        return valor;
    }
    public boolean booleano(String clave) {
        String valor = texto(clave);
        if (!valor.equals("true") && !valor.equals("false")) throw new IllegalArgumentException("Booleano inválido: " + clave);
        return Boolean.parseBoolean(valor);
    }
}
