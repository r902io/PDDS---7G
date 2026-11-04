package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.LocalDateTime;
import java.util.List;

/** Copia inmutable de los envíos: se consulta sin esperar al motor de simulación. */
public record VistaEnvios(
        Long idSimulacion,
        LocalDateTime relojSimulado,
        List<EnvioSimulacion> envios) {

    public VistaEnvios {
        envios = envios == null ? List.of() : List.copyOf(envios);
    }
}
