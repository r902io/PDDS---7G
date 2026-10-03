package pe.edu.pucp.sisrap.simulacion.dominio;

import java.util.List;

public record VehiculoSnapshot(
        String idVehiculo,
        String estado,
        int x,
        int y,
        Long pedidoObjetivo,
        List<PuntoSimulacion> caminoActual) {

    public VehiculoSnapshot {
        caminoActual = caminoActual == null ? List.of() : List.copyOf(caminoActual);
    }
}
