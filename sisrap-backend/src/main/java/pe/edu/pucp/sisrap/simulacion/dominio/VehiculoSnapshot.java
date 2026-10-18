package pe.edu.pucp.sisrap.simulacion.dominio;

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
        int entregasPendientes) {

    public VehiculoSnapshot {
        caminoActual = caminoActual == null ? List.of() : List.copyOf(caminoActual);
        if (capacidadPaquetes < 0 || cargaActualPaquetes < 0 || cargaPorRecogerPaquetes < 0
                || entregasPendientes < 0) {
            throw new IllegalArgumentException("Carga o capacidad inválida");
        }
    }
}
