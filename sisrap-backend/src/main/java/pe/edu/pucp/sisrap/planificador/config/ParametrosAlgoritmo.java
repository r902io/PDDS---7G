package pe.edu.pucp.sisrap.planificador.config;

public class ParametrosAlgoritmo {
    // Genético
    public int tamanioPoblacion = 60;
    public int numGeneraciones = 150;
    public double probCruzamiento = 0.85;
    public double probMutacion = 0.15;
    public int elitismo = 3;
    public int tamanioTorneo = 3;

    // Recocido Simulado
    public double temperaturaInicial = 1000.0;
    public double temperaturaFinal = 1.0;
    public double factorEnfriamiento = 0.95;
    public int iteracionesPorTemperatura = 40;

    // Reproducibilidad de experimentos (sección 6.1 del documento de algoritmos)
    public Long semillaAleatoria;
}
