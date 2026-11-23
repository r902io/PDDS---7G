package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.Duration;
import java.time.LocalDateTime;

/** Escenarios disponibles para la simulación operativa. */
public enum EscenarioSimulacion {

    OPERACION_DIARIA(
            "OPERACION_DIARIA",
            1,
            1,
            Duration.ofMinutes(15)
    ),

    SIMULACION_CINCO_DIAS(
            "SIMULACION_5D",
            5,
            5,
            Duration.ofMinutes(40)
    ),

    // Referencia temporal: 5 días simulados por 20 minutos reales.
    // El horizonte máximo de 365 días se conserva.
    COLAPSO_LOGISTICO(
            "COLAPSO_LOGISTICO",
            365,
            5,
            Duration.ofMinutes(20)
    );

    private final String valorBaseDatos;
    private final int horizonteMaximoDias;
    private final int diasReferenciaTemporal;
    private final Duration duracionRealObjetivo;

    EscenarioSimulacion(
            String valorBaseDatos,
            int horizonteMaximoDias,
            int diasReferenciaTemporal,
            Duration duracionRealObjetivo
    ) {
        this.valorBaseDatos = valorBaseDatos;
        this.horizonteMaximoDias = horizonteMaximoDias;
        this.diasReferenciaTemporal = diasReferenciaTemporal;
        this.duracionRealObjetivo = duracionRealObjetivo;
    }

    public String valorBaseDatos() {
        return valorBaseDatos;
    }

    public int horizonteMaximoDias() {
        return horizonteMaximoDias;
    }

    public int diasReferenciaTemporal() {
        return diasReferenciaTemporal;
    }

    public Duration duracionRealObjetivo() {
        return duracionRealObjetivo;
    }

    public Duration duracionSimuladaReferencia() {
        return Duration.ofDays(diasReferenciaTemporal);
    }

    public LocalDateTime calcularFechaFin(LocalDateTime inicio) {
        if (inicio == null) {
            throw new IllegalArgumentException("La fecha/hora de inicio es obligatoria");
        }
        return inicio.plusDays(horizonteMaximoDias);
    }
}
