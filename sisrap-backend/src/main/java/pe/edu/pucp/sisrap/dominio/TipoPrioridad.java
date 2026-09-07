package pe.edu.pucp.sisrap.dominio;

public enum TipoPrioridad {
    REGULAR_36H(36),
    PRIORIZADO_18H(18),
    PRIORIZADO_12H(12),
    PRIORIZADO_8H(8),
    PRIORIZADO_4H(4);

    private final int horasLimite;

    TipoPrioridad(int horasLimite) {
        this.horasLimite = horasLimite;
    }

    public int getHorasLimite() { return horasLimite; }
    public boolean esPriorizado() { return this != REGULAR_36H; }
}
