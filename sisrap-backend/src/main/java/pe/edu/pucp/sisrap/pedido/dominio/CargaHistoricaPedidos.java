package pe.edu.pucp.sisrap.pedido.dominio;

import java.time.LocalDateTime;

public record CargaHistoricaPedidos(
        String huella,
        int anio,
        int mes,
        int filas,
        LocalDateTime fechaCarga
) {
}