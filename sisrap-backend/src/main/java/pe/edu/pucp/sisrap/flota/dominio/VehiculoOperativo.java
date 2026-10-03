package pe.edu.pucp.sisrap.flota.dominio;

public record VehiculoOperativo(
        String idVehiculo,
        String tipo,
        int capacidadPaquetes,
        double velocidadKmh,
        double costoPorKm,
        EstadoVehiculo estado,
        int posicionX,
        int posicionY,
        String idConductorActual
) {
}