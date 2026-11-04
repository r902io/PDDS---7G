package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.LocalDateTime;
import java.util.List;

import pe.edu.pucp.sisrap.pedido.dominio.EstadoPedido;

/**
 * Un pedido de la corrida y las asignaciones que lo llevan a su destino.
 * Un pedido fraccionado o trasvasado tiene una asignación por cada unidad.
 */
public record EnvioSimulacion(
        long idPedido,
        String idCliente,
        PuntoSimulacion destino,
        int horasLimite,
        LocalDateTime fechaRegistro,
        LocalDateTime fechaLimite,
        EstadoPedido estado,
        int cantidad,
        int cantidadEntregada,
        boolean entregaParcial,
        List<Asignacion> asignaciones) {

    public EnvioSimulacion {
        asignaciones = asignaciones == null ? List.of() : List.copyOf(asignaciones);
    }

    public enum EstadoAsignacion {
        /** Paquetes del pedido que todavía no tienen unidad. */
        SIN_ASIGNAR,
        /** La unidad tiene el plan, pero aún no sale del almacén. */
        PROGRAMADA,
        EN_RUTA,
        /** La unidad llegó al cliente y está descargando. */
        EN_ATENCION,
        ENTREGADA,
        /** El plan se canceló y los paquetes volvieron a la cola. */
        REASIGNADA,
        /** Los paquetes pasaron a otra unidad. */
        TRASVASADA
    }

    /**
     * La ruta tiene tres partes y solo sus esquinas, siempre ortogonales:
     * lo recorrido (real), el tramo en curso (calculado por el motor) y las
     * paradas siguientes (estimadas por distancia Manhattan).
     *
     * @param productos identificadores estables con formato idPedido-n
     * @param llegadaReal true si horaLlegada ya ocurrió; false si es una estimación
     */
    public record Asignacion(
            String idVehiculo,
            EstadoAsignacion estado,
            int cantidad,
            List<String> productos,
            List<PuntoSimulacion> rutaRecorrida,
            List<PuntoSimulacion> rutaPendiente,
            List<PuntoSimulacion> rutaEstimada,
            LocalDateTime horaSalida,
            LocalDateTime horaLlegada,
            boolean llegadaReal,
            LocalDateTime horaEntrega) {

        public Asignacion {
            productos = productos == null ? List.of() : List.copyOf(productos);
            rutaRecorrida = rutaRecorrida == null ? List.of() : List.copyOf(rutaRecorrida);
            rutaPendiente = rutaPendiente == null ? List.of() : List.copyOf(rutaPendiente);
            rutaEstimada = rutaEstimada == null ? List.of() : List.copyOf(rutaEstimada);
        }
    }
}
