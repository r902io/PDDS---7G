package pe.edu.pucp.sisrap.flota.dominio;

import java.time.LocalDateTime;

public record MantenimientoVehiculo(

        long idMantenimiento,

        String idVehiculo,

        TipoMantenimiento tipo,

        LocalDateTime fechaInicio,

        LocalDateTime fechaFin,

        boolean activo

) {

    public MantenimientoVehiculo {

        if (idVehiculo == null
                || idVehiculo.isBlank()) {

            throw new IllegalArgumentException(
                    "El vehículo es obligatorio"
            );
        }

        if (tipo == null) {

            throw new IllegalArgumentException(
                    "El tipo de mantenimiento es obligatorio"
            );
        }

        if (fechaInicio == null
                || fechaFin == null) {

            throw new IllegalArgumentException(
                    "Las fechas son obligatorias"
            );
        }

        if (!fechaFin.isAfter(fechaInicio)) {

            throw new IllegalArgumentException(
                    "La fecha de fin debe ser posterior "
                            + "a la fecha de inicio"
            );
        }
    }

    public boolean correspondeA(
            LocalDateTime instante
    ) {

        if (!activo) {
            return false;
        }

        return !instante.isBefore(fechaInicio)
                && instante.isBefore(fechaFin);
    }
}