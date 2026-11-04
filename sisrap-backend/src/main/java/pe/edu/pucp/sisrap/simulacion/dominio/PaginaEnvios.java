package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Resultado de GET /api/simulacion/envios.
 *
 * @param totalPedidos pedidos que cumplen el filtro, en todas las páginas
 * @param totalProductos en una ventana, solo los productos de sus asignaciones;
 *                       sin ventana, todos los productos de los pedidos
 */
public record PaginaEnvios(
        Long idSimulacion,
        LocalDateTime relojSimulado,
        VentanaEnvios ventana,
        int horas,
        int totalPedidos,
        int totalProductos,
        int pagina,
        int tamanio,
        List<EnvioSimulacion> envios) {

    public PaginaEnvios {
        envios = envios == null ? List.of() : List.copyOf(envios);
    }
}
