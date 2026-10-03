package pe.edu.pucp.sisrap.pedido.dominio;

import java.util.List;

public record PaginaPedidos(
        List<PedidoOperativo> contenido,
        long total,
        int pagina,
        int tamanio
) {

    public PaginaPedidos {
        contenido = List.copyOf(contenido);
    }
}