package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;


public record ConfiguracionSimulacion(
        EscenarioSimulacion escenario,
        LocalDate fechaInicio,
        long semilla
) {

    public ConfiguracionSimulacion {
        if (escenario == null) {
            throw new IllegalArgumentException("El escenario es obligatorio");
        }
        if (fechaInicio == null) {
            throw new IllegalArgumentException("La fecha de inicio es obligatoria");
        }
    }

    public LocalDateTime fechaHoraInicio() {
        return fechaInicio.atStartOfDay();
    }

    public LocalDateTime fechaHoraFin() {
        return escenario.calcularFechaFin(fechaHoraInicio());
    }

    public Duration duracionSimuladaMaxima() {
        return Duration.between(fechaHoraInicio(), fechaHoraFin());
    }

    public Duration duracionRealObjetivo() {
        return escenario.duracionRealObjetivo();
    }

    public double factorTemporalInterno() {
        double nanosSimuladosReferencia =
                escenario.duracionSimuladaReferencia().toNanos();
        double nanosReales = duracionRealObjetivo().toNanos();

        if (nanosReales <= 0.0) {
            throw new IllegalStateException(
                    "La duración real objetivo debe ser mayor que cero"
            );
        }

        return nanosSimuladosReferencia / nanosReales;
    }
}
