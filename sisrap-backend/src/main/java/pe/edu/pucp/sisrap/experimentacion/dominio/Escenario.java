package pe.edu.pucp.sisrap.experimentacion.dominio;

import java.time.LocalDateTime;
import java.util.List;

import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;

/**
 * Una instancia del problema: un bloque de pedidos reales planificados en el instante en que llegó el último.
 * Las reglas de planificación dependen de la variante, por eso el contexto se arma bajo demanda.
 *
 * @param vehiculosEnMantenimiento ids marcados como no disponibles por el mantenimiento preventivo del día
 * @param bloqueosActivos          bloqueos vigentes en el instante; se reportan, el modelo estático no los usa
 */
public record Escenario(String id, int tamanio, int instancia, LocalDateTime instante, List<Pedido> pedidos,
                        List<Vehiculo> vehiculos, List<String> vehiculosEnMantenimiento, List<Bloqueo> bloqueosActivos) {
    public Escenario {
        pedidos = List.copyOf(pedidos);
        vehiculos = List.copyOf(vehiculos);
        vehiculosEnMantenimiento = List.copyOf(vehiculosEnMantenimiento);
        bloqueosActivos = List.copyOf(bloqueosActivos);
    }

    public ContextoPlanificacion contexto(BaseOperativa base, ReglasPlanificacion reglas) {
        java.util.Set<String> bloqueados = new java.util.HashSet<>();
        for (var b : bloqueosActivos) for (var n : b.vertices()) bloqueados.add(n.getX() + "," + n.getY());
        return new ContextoPlanificacion(pedidos, vehiculos, base.almacenes(), instante, reglas, bloqueados);
    }

    public int cantidadTotalQq() {
        return pedidos.stream().mapToInt(Pedido::getCantidadQq).sum();
    }

    public int capacidadDisponibleQq() {
        return vehiculos.stream().filter(Vehiculo::isDisponible).mapToInt(Vehiculo::getCapacidadPaquetes).sum();
    }
}