package pe.edu.pucp.sisrap.experimentacion.dominio;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

/**
 * Diseño reproducible del experimento numérico:
 * variantes x escenarios x perfiles x instancias x repeticiones x algoritmos.
 *
 * Operación diaria y cinco días usan P50/P75/P90.
 * En colapso, los tres perfiles parten de P50 y difieren por la tasa de crecimiento:
 * NORMAL=g, ALTA=2g y CRITICA=3g.
 */
public record PlanExperimento(String nombre,
                              String perfil,
                              List<String> algoritmos,
                              List<EscenarioOperativo> escenariosOperativos,
                              List<PerfilPresion> perfiles,
                              int demandaP50Diaria,
                              int demandaP75Diaria,
                              int demandaP90Diaria,
                              double crecimientoColapsoBase,
                              int instancias,
                              int repeticiones,
                              long semillaBase,
                              LocalDate desde,
                              boolean aplicarMantenimiento,
                              boolean calentamiento,
                              List<Variante> variantes) {

    public PlanExperimento {
        if (nombre == null || !nombre.matches("[A-Za-z0-9_.-]{1,80}"))
            throw new IllegalArgumentException("Nombre de experimento inválido");
        if (perfil == null || !perfil.matches("[A-Za-z0-9_-]{1,60}"))
            throw new IllegalArgumentException("Perfil inválido");

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
        if (demandaP50Diaria < 1 || demandaP75Diaria < demandaP50Diaria || demandaP90Diaria < demandaP75Diaria)
            throw new IllegalArgumentException("Percentiles de demanda inválidos");
        if (!Double.isFinite(crecimientoColapsoBase)
                || crecimientoColapsoBase <= 0.0
                || crecimientoColapsoBase * 3.0 >= 1.0) {
            throw new IllegalArgumentException(
                    "Crecimiento base de colapso inválido; se requiere 0 < g y 3g < 1");
        }
        if (instancias < 1 || repeticiones < 1)
            throw new IllegalArgumentException("Instancias y repeticiones deben ser >= 1");
        if (desde == null)
            throw new IllegalArgumentException("Falta la fecha de inicio del periodo histórico");
        if (variantes.isEmpty() || variantes.stream().map(Variante::nombre).distinct().count() != variantes.size())
            throw new IllegalArgumentException("Variantes vacías o con nombre repetido");

        long desplazamientoMaximo = Math.multiplyExact((long) instancias, repeticiones);
        Math.addExact(semillaBase, desplazamientoMaximo - 1L);
    }

    public int lambdaPedidos(PerfilPresion perfilPresion) {
        return switch (perfilPresion) {
            case NORMAL -> demandaP50Diaria;
            case ALTA -> demandaP75Diaria;
            case CRITICA -> demandaP90Diaria;
        };
    }

    public double crecimientoColapso(PerfilPresion perfilPresion) {
        return crecimientoColapsoBase * switch (perfilPresion) {
            case NORMAL -> 1.0;
            case ALTA -> 2.0;
            case CRITICA -> 3.0;
        };
    }

    public double lambdaColapso(PerfilPresion perfilPresion, int dia) {
        if (dia < 0) throw new IllegalArgumentException("El día de colapso no puede ser negativo");
        return demandaP50Diaria
                * Math.pow(1.0 + crecimientoColapso(perfilPresion), dia);
    }

    public double lambdaColapsoMaxima(int dia) {
        return lambdaColapso(PerfilPresion.CRITICA, dia);
    }

    public boolean incluyeColapso() {
        return escenariosOperativos.contains(EscenarioOperativo.COLAPSO_LOGISTICO);
    }

    public long totalCorridas() {
        return (long) variantes.size()
                * escenariosOperativos.size()
                * perfiles.size()
                * instancias
                * repeticiones
                * algoritmos.size();
    }
}