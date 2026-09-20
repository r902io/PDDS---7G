package pe.edu.pucp.sisrap.pedido.dominio;

/** Los nombres conservan compatibilidad con el esquema inicial.
 * El plazo efectivo se lee de pedido.horas_limite, no del nombre de la categoría. */
public enum TipoPrioridad {
    REGULAR_36H, PRIORIZADO_18H, PRIORIZADO_12H, PRIORIZADO_8H, PRIORIZADO_4H;

    public boolean esPriorizado() {
        return this != REGULAR_36H;
    }
}
