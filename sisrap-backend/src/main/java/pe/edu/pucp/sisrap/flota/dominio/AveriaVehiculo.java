package pe.edu.pucp.sisrap.flota.dominio;

import java.time.LocalDateTime;

public record AveriaVehiculo(
        long idIncidencia,
        String idVehiculo,
        TipoAveria tipoAveria,
        int ubicacionX,
        int ubicacionY,
        LocalDateTime fechaOcurrencia,
        LocalDateTime horaRetornoEstimada,
        boolean activa
) {
}