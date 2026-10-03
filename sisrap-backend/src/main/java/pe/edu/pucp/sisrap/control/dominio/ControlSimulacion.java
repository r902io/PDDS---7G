package pe.edu.pucp.sisrap.control.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record ControlSimulacion(
        UUID sesionControladoraId,
        Instant adquiridoEn,
        Instant renovadoEn,
        Instant expiraEn
) {

    public boolean expirado(Instant ahora) {
        return !ahora.isBefore(expiraEn);
    }

    public boolean perteneceA(UUID sesionId) {
        return sesionControladoraId.equals(sesionId);
    }

    public ControlSimulacion renovar(
            Instant ahora,
            Duration duracion
    ) {
        return new ControlSimulacion(
                sesionControladoraId,
                adquiridoEn,
                ahora,
                ahora.plus(duracion)
        );
    }
}