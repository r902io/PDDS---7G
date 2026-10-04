package pe.edu.pucp.sisrap.pedido.dominio;

import java.util.List;

/**
 * Lote mensual ya validado y listo para persistirse.
 */
public record CargaHistoricaPreparada(
        String nombreArchivo,
        String huella,
        int anio,
        int mes,
        List<PedidoHistoricoImportado> pedidos
) {

    public CargaHistoricaPreparada {

        if (nombreArchivo == null || nombreArchivo.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre del archivo es obligatorio"
            );
        }

        if (huella == null || huella.isBlank()) {
            throw new IllegalArgumentException(
                    "La huella del archivo es obligatoria"
            );
        }

        if (mes < 1 || mes > 12) {
            throw new IllegalArgumentException(
                    "Mes inválido"
            );
        }

        if (pedidos == null || pedidos.isEmpty()) {
            throw new IllegalArgumentException(
                    "La carga debe contener pedidos"
            );
        }

        pedidos = List.copyOf(pedidos);
    }
}
