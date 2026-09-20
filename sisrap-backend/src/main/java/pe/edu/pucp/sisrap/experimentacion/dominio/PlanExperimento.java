package pe.edu.pucp.sisrap.experimentacion.dominio;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

/**
 * Definición completa (y reproducible) de un experimento numérico.
 * La malla de corridas es: variantes x tamaños x instancias x repeticiones x algoritmos.
 *
 * @param instancias cantidad de bloques de pedidos DISTINTOS por cada tamaño
 * @param desde      primer día de ventas a considerar
 */
public record PlanExperimento(String nombre, String perfil, List<String> algoritmos, List<Integer> tamanios,
                              int instancias, int repeticiones, long semillaBase, LocalDate desde,
                              boolean aplicarMantenimiento, boolean calentamiento, List<Variante> variantes) {
    public PlanExperimento {
        if (nombre == null || !nombre.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("Nombre de experimento inválido");
        if (perfil == null || !perfil.matches("[A-Za-z0-9_-]{1,60}")) throw new IllegalArgumentException("Perfil inválido");
        algoritmos = List.copyOf(algoritmos);
        tamanios = List.copyOf(tamanios);
        variantes = List.copyOf(variantes);
        if (algoritmos.isEmpty() || new HashSet<>(algoritmos).size() != algoritmos.size())
            throw new IllegalArgumentException("Algoritmos vacíos o repetidos");
        if (tamanios.isEmpty() || new HashSet<>(tamanios).size() != tamanios.size() || tamanios.stream().anyMatch(n -> n < 1))
            throw new IllegalArgumentException("Tamaños vacíos, repetidos o menores que 1");
        if (instancias < 1 || repeticiones < 1) throw new IllegalArgumentException("Instancias y repeticiones deben ser >= 1");
        if (desde == null) throw new IllegalArgumentException("Falta la fecha de inicio de ventas");
        if (variantes.isEmpty() || variantes.stream().map(Variante::nombre).distinct().count() != variantes.size())
            throw new IllegalArgumentException("Variantes vacías o con nombre repetido");
        Math.addExact(semillaBase, repeticiones - 1L);
    }

    public int maxTamanio() {
        return tamanios.stream().mapToInt(Integer::intValue).max().orElseThrow();
    }

    public int pedidosNecesarios() {
        return instancias * maxTamanio();
    }

    public long totalCorridas() {
        return (long) variantes.size() * tamanios.size() * instancias * repeticiones * algoritmos.size();
    }
}