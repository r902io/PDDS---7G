package pe.edu.pucp.sisrap.pedido.dominio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import pe.edu.pucp.sisrap.simulacion.dominio.EscenarioSimulacion;

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

    List<CargaHistoricaPedidos> listarCargasHistoricas(EscenarioSimulacion escenario);

    void eliminarCargaHistorica(EscenarioSimulacion escenario, String huella);

    boolean existeCargaPeriodo(
            EscenarioSimulacion escenario,
            int anio,
            int mes
    );

    /**
     * Guarda todos los archivos de una carga múltiple en una sola transacción.
     * Si uno falla, no se persiste ninguno.
     */
    List<CargaHistoricaPedidos> guardarCargasHistoricas(
            EscenarioSimulacion escenario,
            List<CargaHistoricaPreparada> cargas
    );
}
