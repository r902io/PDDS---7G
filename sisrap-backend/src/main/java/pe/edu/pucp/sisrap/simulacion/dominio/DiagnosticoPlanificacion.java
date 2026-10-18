package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.LocalDateTime;

/** Motivo comprobable de que un pedido no obtuvo nueva ruta en una iteración. */
public record DiagnosticoPlanificacion(LocalDateTime instante, long idPedido, String motivo) {
    public DiagnosticoPlanificacion {
        if (instante == null || idPedido <= 0 || motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Diagnóstico incompleto");
        }
    }
}
