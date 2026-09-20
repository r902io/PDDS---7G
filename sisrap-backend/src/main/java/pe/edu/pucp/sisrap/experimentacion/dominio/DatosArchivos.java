package pe.edu.pucp.sisrap.experimentacion.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pe.edu.pucp.sisrap.carga.dominio.PedidoImportado;

/**
 * Información recopilada de los .txt: ventas (pedidos), bloqueos y mantenimiento preventivo.
 *
 * @param archivosLeidos rutas de los archivos que realmente se consumieron (trazabilidad)
 * @param mantenimiento  fecha -> ids de vehículos que ese día están en mantenimiento preventivo
 */
public record DatosArchivos(List<PedidoImportado> pedidos, List<Bloqueo> bloqueos,
                            Map<LocalDate, Set<String>> mantenimiento, List<String> archivosLeidos,
                            List<String> advertencias) {
    public DatosArchivos {
        pedidos = List.copyOf(pedidos);
        bloqueos = List.copyOf(bloqueos);
        mantenimiento = Map.copyOf(mantenimiento);
        archivosLeidos = List.copyOf(archivosLeidos);
        advertencias = List.copyOf(advertencias);
    }
}