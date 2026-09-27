package pe.edu.pucp.sisrap.experimentacion.dominio;

/**
 * Perfiles de presión logística usados en la experimentación.
 *
 * La presión se construye únicamente con variables para las que existe una base
 * reproducible en los datos entregados:
 * - demanda: NORMAL=P50, ALTA=P75, CRITICA=P90 de pedidos/día;
 * - bloqueos: NORMAL=P50, ALTA=P75, CRITICA=P90 de bloqueos/día.
 */
public enum PerfilPresion {
    NORMAL(0.50, 0, 0, 0.00),
    ALTA(0.75, 0, 0, 0.00),
    CRITICA(0.90, 0, 0, 0.00);

    public final double percentilDemanda;

    /** Reservado para compatibilidad con el código existente; no se usa en esta versión. */
    public final int vehiculosBajaBase;

    /** Averías deshabilitadas hasta definir su mecanismo de generación. */
    public final int vehiculosAveriaIncidente;

    /** Incidencias artificiales sobre pedidos deshabilitadas. */
    public final double proporcionPedidosAfectados;

    PerfilPresion(double percentilDemanda,
                  int vehiculosBajaBase,
                  int vehiculosAveriaIncidente,
                  double proporcionPedidosAfectados) {
        this.percentilDemanda = percentilDemanda;
        this.vehiculosBajaBase = vehiculosBajaBase;
        this.vehiculosAveriaIncidente = vehiculosAveriaIncidente;
        this.proporcionPedidosAfectados = proporcionPedidosAfectados;
    }

    public boolean simulaIncidencia() {
        return false;
    }
}