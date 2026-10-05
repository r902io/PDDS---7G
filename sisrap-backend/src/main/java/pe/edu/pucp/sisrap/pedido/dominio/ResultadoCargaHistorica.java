package pe.edu.pucp.sisrap.pedido.dominio;

import java.util.List;

public record ResultadoCargaHistorica(
        int archivosProcesados,
        int pedidosInsertados,
        List<CargaHistoricaPedidos> cargas
) {

    public ResultadoCargaHistorica {
        cargas = List.copyOf(cargas);
    }
}
