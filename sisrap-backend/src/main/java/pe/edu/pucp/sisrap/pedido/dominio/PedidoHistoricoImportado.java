package pe.edu.pucp.sisrap.pedido.dominio;

import java.time.LocalDateTime;

public record PedidoHistoricoImportado(
        String cliente,
        int cantidad,
        TipoPrioridad prioridad,
        int horasLimite,
        LocalDateTime fechaLlegada,
        int x,
        int y
) {
}