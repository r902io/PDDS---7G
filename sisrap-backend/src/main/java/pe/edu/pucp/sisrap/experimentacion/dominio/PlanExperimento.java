package pe.edu.pucp.sisrap.experimentacion.dominio;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

/**
 * Definición completa (y reproducible) de un experimento numérico, alineada al informe "Diseño de
 * Experimento" (sección 3.4): la malla de corridas es escenarios operativos x perfiles de presión x
 * instancias x repeticiones x algoritmos. Cada (escenarioOperativo, perfilPresion) fija cómo se construye
 * la instancia (tamaño, flota disponible e incidencias); {@code variantes} sigue disponible para además
 * sobrescribir parámetros puntuales de la BD sin tocarla (por defecto solo BASE).
 *
 * @param instancias cantidad de bloques de pedidos DISTINTOS por cada combinación (escenario, perfil); en
 *                  COLAPSO_LOGISTICO además representa la cantidad de niveles de presión progresiva evaluados
 * @param tamanioBaseDiario tamaño de referencia (pedidos) de una instancia de operación día a día en perfil
 *                  NORMAL; los demás escenarios y perfiles lo escalan (ver {@link EscenarioOperativo} y
 *                  {@link PerfilPresion})
 * @param repeticiones repeticiones por combinación; el informe recomienda 30 (sección 3.4)
 * @param desde      primer día de ventas a considerar
 */
public record PlanExperimento(String nombre, String perfil, List<String> algoritmos,
                              List<EscenarioOperativo> escenariosOperativos, List<PerfilPresion> perfiles,
                              int tamanioBaseDiario, int instancias, int repeticiones, long semillaBase,
                              LocalDate desde, boolean aplicarMantenimiento, boolean calentamiento,
                              List<Variante> variantes) {
    public PlanExperimento {
        if (nombre == null || !nombre.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("Nombre de experimento inválido");
        if (perfil == null || !perfil.matches("[A-Za-z0-9_-]{1,60}")) throw new IllegalArgumentException("Perfil inválido");
        algoritmos = List.copyOf(algoritmos);
        escenariosOperativos = List.copyOf(escenariosOperativos);
        perfiles = List.copyOf(perfiles);
        variantes = List.copyOf(variantes);
        if (algoritmos.isEmpty() || new HashSet<>(algoritmos).size() != algoritmos.size())
            throw new IllegalArgumentException("Algoritmos vacíos o repetidos");
        if (escenariosOperativos.isEmpty() || new HashSet<>(escenariosOperativos).size() != escenariosOperativos.size())
            throw new IllegalArgumentException("Escenarios operativos vacíos o repetidos");
        if (perfiles.isEmpty() || new HashSet<>(perfiles).size() != perfiles.size())
            throw new IllegalArgumentException("Perfiles de presión vacíos o repetidos");
        if (tamanioBaseDiario < 1) throw new IllegalArgumentException("El tamaño base diario debe ser >= 1");
        if (instancias < 1 || repeticiones < 1) throw new IllegalArgumentException("Instancias y repeticiones deben ser >= 1");
        if (desde == null) throw new IllegalArgumentException("Falta la fecha de inicio de ventas");
        if (variantes.isEmpty() || variantes.stream().map(Variante::nombre).distinct().count() != variantes.size())
            throw new IllegalArgumentException("Variantes vacías o con nombre repetido");
        Math.addExact(semillaBase, repeticiones - 1L);
    }

    /** Tamaño máximo de instancia que puede pedirse en cualquier combinación (escenario, perfil, nivel). */
    public int maxTamanio() {
        double factorPerfil = perfiles.stream().mapToDouble(p -> p.factorTamanio).max().orElseThrow();
        double factorHorizonte = escenariosOperativos.stream().mapToDouble(e -> e.factorHorizonte).max().orElseThrow();
        double factorColapso = escenariosOperativos.contains(EscenarioOperativo.COLAPSO_LOGISTICO)
                ? 1.0 + 0.5 * (instancias - 1) : 1.0;
        return (int) Math.ceil(tamanioBaseDiario * factorPerfil * factorHorizonte * factorColapso);
    }

    /** Cota superior de pedidos que puede llegar a pedir el generador de escenarios (con margen por combinación). */
    public int pedidosNecesarios() {
        return maxTamanio();
    }

    public long totalCorridas() {
        return (long) variantes.size() * escenariosOperativos.size() * perfiles.size() * instancias * repeticiones * algoritmos.size();
    }
}