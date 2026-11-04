package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.LocalDateTime;

import pe.edu.pucp.sisrap.pedido.dominio.EstadoPedido;

/** Pedido ya registrado en la corrida, tal como lo mantiene la simulación en memoria. */
public record PedidoCorrida(
        long idPedido,
        String idCliente,
        int cantidad,
        int horasLimite,
        LocalDateTime fechaRegistro,
        LocalDateTime fechaLimite,
        int x,
        int y,
        EstadoPedido estado,
        LocalDateTime fechaEntrega) {
}
