package pe.edu.pucp.sisrap.pedido.dominio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RepositorioPedidos {

    List<PedidoOperativo> listar(
            LocalDateTime desde,
            LocalDateTime hasta,
            EstadoPedido estado,
            int offset,
            int limite
    );

    long contar(
            LocalDateTime desde,
            LocalDateTime hasta,
            EstadoPedido estado
    );

    Optional<PedidoOperativo> buscar(
            long idPedido
    );

    List<CargaHistoricaPedidos> listarCargasHistoricas();

    boolean existeCargaPeriodo(
            int anio,
            int mes
    );

    CargaHistoricaPedidos guardarCargaHistorica(
            String huella,
            int anio,
            int mes,
            List<PedidoHistoricoImportado> pedidos
    );
}