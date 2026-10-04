package pe.edu.pucp.sisrap.pedido.dominio;

import java.time.LocalDateTime;

/**
 * Metadatos de un archivo mensual de pedidos históricos ya cargado.
 *
 * <p>El archivo físico no se conserva en el servidor. Solo se persisten
 * su huella, periodo, cantidad de filas y fecha de carga.</p>
 */
public record CargaHistoricaPedidos(
        String huella,
        int anio,
        int mes,
        int filas,
        LocalDateTime fechaCarga
) {

    public CargaHistoricaPedidos {

        if (huella == null || huella.isBlank()) {
            throw new IllegalArgumentException(
                    "La huella de la carga es obligatoria"
            );
        }

        if (anio <= 0) {
            throw new IllegalArgumentException(
                    "El año de la carga es inválido"
            );
        }

        if (mes < 1 || mes > 12) {
            throw new IllegalArgumentException(
                    "El mes de la carga es inválido"
            );
        }

        if (filas < 0) {
            throw new IllegalArgumentException(
                    "La cantidad de filas no puede ser negativa"
            );
        }

        if (fechaCarga == null) {
            throw new IllegalArgumentException(
                    "La fecha de carga es obligatoria"
            );
        }
    }
}
