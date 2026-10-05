package pe.edu.pucp.sisrap.control.dominio;

import java.time.Instant;
import java.util.UUID;

public record ControlSimulacion(
        UUID sesionControladoraId,
        Instant adquiridoEn
) {

    public ControlSimulacion {
        if (sesionControladoraId == null) {
            throw new IllegalArgumentException(
                    "La sesión controladora es obligatoria"
            );
        }
        if (adquiridoEn == null) {
            throw new IllegalArgumentException(
                    "La fecha de adquisición es obligatoria"
            );
        }
    }

    public boolean perteneceA(UUID sesionId) {
        return sesionControladoraId.equals(sesionId);
    }
}