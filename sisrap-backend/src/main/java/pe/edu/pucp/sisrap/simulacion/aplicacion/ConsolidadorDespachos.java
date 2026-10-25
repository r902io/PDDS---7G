package pe.edu.pucp.sisrap.simulacion.aplicacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Ruta;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;

/** Despacha sin esperar acumulación y conserva las entregas seleccionadas por GA. */
public final class ConsolidadorDespachos {
    private ConsolidadorDespachos() { }
    public static boolean convieneEsperar(List<Pedido> pedidos, ContextoPlanificacion contexto,
                                          LocalDateTime fin) { return false; }
    public static void consolidar(Solucion solucion, ContextoPlanificacion contexto,
                                   Map<String, Integer> reservas) {
        for (Ruta ruta : solucion.getRutas()) {
            if (ruta.getSecuenciaPedidos().isEmpty()) continue;
            ruta.recalcular();
            var mejor = ruta.getAlmacenRetorno();
            double tiempo = ruta.isIntransitable() ? Double.POSITIVE_INFINITY : ruta.getTiempoTotalHoras();
            for (var almacen : contexto.getAlmacenes()) {
                if (!almacen.tieneStockDisponible()) continue;
                int consumo=solucion.getRutas().stream()
                        .filter(r -> r.getAlmacenOrigen().getIdAlmacen().equals(almacen.getIdAlmacen()))
                        .mapToInt(Ruta::cargaTotal).sum();
                if (almacen.getCapacidadMaxima()!=null && almacen.getStockActual()<=consumo) continue;
                Ruta candidata = ruta.copiar(); candidata.setAlmacenRetorno(almacen); candidata.recalcular();
                if (!candidata.isIntransitable() && candidata.getTiempoTotalHoras() < tiempo) {
                    mejor = almacen; tiempo = candidata.getTiempoTotalHoras();
                }
            }
            ruta.setAlmacenRetorno(mejor); ruta.recalcular();
        }
    }
}
