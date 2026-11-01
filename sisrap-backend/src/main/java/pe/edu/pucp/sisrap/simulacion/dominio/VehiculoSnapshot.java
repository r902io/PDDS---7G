package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.LocalDateTime;
import java.util.List;

public record VehiculoSnapshot(
        String idVehiculo,
        String estado,
        int x,
        int y,
        Long pedidoObjetivo,
        List<PuntoSimulacion> caminoActual,
        int capacidadPaquetes,
        int cargaActualPaquetes,
        int cargaPorRecogerPaquetes,
        int entregasPendientes,
        double xVisual,
        double yVisual,
        long secuenciaMovimiento,
        List<PasoVisual> movimientosRecientes,
        /* Avance de la ruta; opcionales: en null si la unidad no tiene plan o aún no sale. */
        LocalDateTime horaSalida,
        LocalDateTime horaLlegadaEstimada,
        Double distanciaRecorridaKm,
        Double distanciaPorRecorrerKm) {

    /** Nodos realmente recorridos, numerados dentro de una corrida. */
    public record PasoVisual(long secuencia, int x, int y) { }

    public VehiculoSnapshot(String idVehiculo, String estado, int x, int y,
                           Long pedidoObjetivo, List<PuntoSimulacion> caminoActual,
                           int capacidadPaquetes, int cargaActualPaquetes,
                           int cargaPorRecogerPaquetes, int entregasPendientes,
                           double xVisual, double yVisual, long secuenciaMovimiento,
                           List<PasoVisual> movimientosRecientes) {
        this(idVehiculo, estado, x, y, pedidoObjetivo, caminoActual, capacidadPaquetes,
                cargaActualPaquetes, cargaPorRecogerPaquetes, entregasPendientes,
                xVisual, yVisual, secuenciaMovimiento, movimientosRecientes,
                null, null, null, null);
    }

    public VehiculoSnapshot(String idVehiculo, String estado, int x, int y,
                           Long pedidoObjetivo, List<PuntoSimulacion> caminoActual,
                           int capacidadPaquetes, int cargaActualPaquetes,
                           int cargaPorRecogerPaquetes, int entregasPendientes) {
        this(idVehiculo, estado, x, y, pedidoObjetivo, caminoActual, capacidadPaquetes,
                cargaActualPaquetes, cargaPorRecogerPaquetes, entregasPendientes,
                x, y, 0, List.of());
    }

    public VehiculoSnapshot {
        caminoActual = caminoActual == null ? List.of() : List.copyOf(caminoActual);
        movimientosRecientes = movimientosRecientes == null ? List.of() : List.copyOf(movimientosRecientes);
        if (!Double.isFinite(xVisual) || !Double.isFinite(yVisual) || secuenciaMovimiento < 0)
            throw new IllegalArgumentException("Posición visual inválida");
        if (capacidadPaquetes < 0 || cargaActualPaquetes < 0 || cargaPorRecogerPaquetes < 0
                || entregasPendientes < 0) {
            throw new IllegalArgumentException("Carga o capacidad inválida");
        }
    }
}
