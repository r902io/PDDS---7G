package pe.edu.pucp.sisrap.experimentacion.dominio;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;

/**
 * Un tratamiento del experimento: el perfil de la BD con algunos parámetros sobrescritos.
 * Permite probar, por ejemplo, ga.poblacion=100 sin tocar la base de datos.
 */
public record Variante(String nombre, Map<String, String> sobrescrituras) {
    public static final String BASE = "BASE";

    public Variante {
        if (nombre == null || !nombre.matches("[A-Za-z0-9_-]{1,40}"))
            throw new IllegalArgumentException("Nombre de variante inválido: " + nombre);
        if (sobrescrituras.keySet().stream().anyMatch(k -> k.startsWith("experimento.")))
            throw new IllegalArgumentException("Variante " + nombre + ": experimento.* se controla con --tamanios, --repeticiones y --semilla");
        sobrescrituras = Map.copyOf(new TreeMap<>(sobrescrituras));
    }

    public static Variante base() {
        return new Variante(BASE, Map.of());
    }

    /** Formato: NOMBRE:clave=valor;clave=valor */
    public static Variante parsear(String texto) {
        int corte = texto.indexOf(':');
        if (corte < 1) throw new IllegalArgumentException("Variante inválida (use NOMBRE:clave=valor;clave=valor): " + texto);
        Map<String, String> cambios = new LinkedHashMap<>();
        for (String par : texto.substring(corte + 1).split(";")) {
            int igual = par.indexOf('=');
            if (igual < 1) throw new IllegalArgumentException("Par inválido en variante: " + par);
            cambios.put(par.substring(0, igual).trim(), par.substring(igual + 1).trim());
        }
        return new Variante(texto.substring(0, corte).trim(), cambios);
    }

    public boolean modificaFuncionObjetivo() {
        return sobrescrituras.keySet().stream().anyMatch(k -> k.startsWith("objetivo."));
    }

    public Configuracion aplicar(Configuracion base) {
        for (String clave : sobrescrituras.keySet())
            if (!base.valores().containsKey(clave))
                throw new IllegalArgumentException("Variante " + nombre + ": parámetro desconocido " + clave);
        Map<String, String> combinado = new TreeMap<>(base.valores());
        combinado.putAll(sobrescrituras);
        return new Configuracion(combinado);
    }
}