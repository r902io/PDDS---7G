package pe.edu.pucp.sisrap.experimentacion.dominio;

/**
 * Los tres niveles de presión logística definidos en el informe "Diseño de Experimento" (sección 3.4):
 * normal, alta y crítica. Cada nivel escala el tamaño de la instancia y determina la severidad de las
 * incidencias simuladas (averías de vehículo y bloqueos) en el escenario de operación día a día.
 *
 * @param factorTamanio          multiplicador sobre el tamaño base de pedidos de la instancia
 * @param vehiculosBajaBase      vehículos que este perfil retira de la flota disponible de forma
 *                              permanente en la instancia (reducción de recursos, no incidencia puntual)
 * @param vehiculosAveriaIncidente vehículos adicionales que se marcan en avería al simular una incidencia
 * @param proporcionPedidosAfectados proporción de pedidos ya asignados que se consideran "aún no
 *                              atendidos" y deben replanificarse cuando ocurre un bloqueo
 */
public enum PerfilPresion {
    NORMAL(1.0, 0, 0, 0.00),
    ALTA(1.5, 0, 1, 0.15),
    CRITICA(2.2, 1, 2, 0.30);

    public final double factorTamanio;
    public final int vehiculosBajaBase;
    public final int vehiculosAveriaIncidente;
    public final double proporcionPedidosAfectados;

    PerfilPresion(double factorTamanio, int vehiculosBajaBase, int vehiculosAveriaIncidente, double proporcionPedidosAfectados) {
        this.factorTamanio = factorTamanio;
        this.vehiculosBajaBase = vehiculosBajaBase;
        this.vehiculosAveriaIncidente = vehiculosAveriaIncidente;
        this.proporcionPedidosAfectados = proporcionPedidosAfectados;
    }

    /** NORMAL no incorpora incidencias (sección 4.2 del informe de Selección de Algoritmos). */
    public boolean simulaIncidencia() {
        return vehiculosAveriaIncidente > 0 || proporcionPedidosAfectados > 0;
    }
}
