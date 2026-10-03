package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.Duration;
import java.time.LocalDateTime;

public record ConfiguracionSimulacion(
        LocalDateTime fechaHoraInicio,
        LocalDateTime fechaHoraFin,
        double velocidad,
        long semilla) {

    public ConfiguracionSimulacion {
        if (fechaHoraInicio == null || fechaHoraFin == null) {
            throw new IllegalArgumentException("Debe indicar inicio y fin de la simulación");
        }
        if (!fechaHoraFin.isAfter(fechaHoraInicio)) {
            throw new IllegalArgumentException("La fecha fin debe ser posterior a la fecha inicio");
        }
        if (Duration.between(fechaHoraInicio, fechaHoraFin).toDays() > 31) {
            throw new IllegalArgumentException("La simulación operativa no puede superar 31 días por ejecución");
        }
        if (!Double.isFinite(velocidad) || velocidad < 1.0 || velocidad > 600.0) {
            throw new IllegalArgumentException("La velocidad debe estar entre 1 y 600 segundos simulados por segundo real");
        }
    }
}
