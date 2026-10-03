package pe.edu.pucp.sisrap.pedido.dominio;

import java.time.LocalDateTime;

public record PedidoOperativo(
        long idPedido,
        String idCliente,
        int cantidadQq,
        TipoPrioridad prioridad,
        int horasLimite,
        LocalDateTime fechaLlegada,
        LocalDateTime fechaEntregaReal,
        EstadoPedido estado,
        int ubicacionX,
        int ubicacionY
) {
}