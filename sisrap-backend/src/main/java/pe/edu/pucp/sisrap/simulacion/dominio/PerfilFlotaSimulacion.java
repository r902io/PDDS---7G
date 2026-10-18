package pe.edu.pucp.sisrap.simulacion.dominio;

public record PerfilFlotaSimulacion(String idVehiculo, String tipo, double costoPorKm) {
    public PerfilFlotaSimulacion {
        if (idVehiculo == null || idVehiculo.isBlank() || tipo == null || tipo.isBlank()
                || !Double.isFinite(costoPorKm) || costoPorKm < 0) {
            throw new IllegalArgumentException("Datos de vehículo inválidos para indicadores");
        }
    }
}
