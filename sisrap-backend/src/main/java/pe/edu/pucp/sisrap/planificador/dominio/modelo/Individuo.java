package pe.edu.pucp.sisrap.planificador.dominio.modelo;

public class Individuo {
    private Solucion cromosoma;
    private double fitness;

    public Individuo(Solucion cromosoma) {
        this.cromosoma = cromosoma;
    }

    public Individuo copiar() {
        Individuo copia = new Individuo(this.cromosoma.copiar());
        copia.fitness = this.fitness;
        return copia;
    }

    public Solucion getCromosoma() { return cromosoma; }
    public double getFitness() { return fitness; }
    public void setFitness(double f) { this.fitness = f; }
}
