package pe.edu.pucp.sisrap.simulacion.dominio;

/** Filtro por tiempo de la lista de envíos, medido con el reloj simulado. */
public enum VentanaEnvios {
    TODOS,
    /** Con al menos una asignación entregada en las últimas x horas. */
    ENTREGADOS,
    /** Con llegada estimada, o plazo si no tienen unidad, en las siguientes x horas. */
    POR_ENTREGAR
}
