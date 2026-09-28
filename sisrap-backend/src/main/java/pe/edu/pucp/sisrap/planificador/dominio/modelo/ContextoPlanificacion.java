package pe.edu.pucp.sisrap.planificador.dominio.modelo;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;

public record ContextoPlanificacion(
        List<Pedido> pedidos,
        List<Vehiculo> vehiculos,
        List<Almacen> almacenes,
        LocalDateTime instante,
        ReglasPlanificacion reglas,
        Set<String> nodosBloqueados) {

    public ContextoPlanificacion {
        pedidos = List.copyOf(pedidos);
        vehiculos = List.copyOf(vehiculos);
        almacenes = List.copyOf(almacenes);

        if (almacenes.isEmpty() || instante == null || reglas == null) {
            throw new IllegalArgumentException("Contexto incompleto");
        }

        nodosBloqueados = nodosBloqueados == null
                ? Set.of()
                : Set.copyOf(nodosBloqueados);

        var ids = new HashSet<Long>();
        for (var p : pedidos) {
            if (!ids.add(p.getIdPedido())) {
                throw new IllegalArgumentException("Pedido duplicado");
            }

            /*
             * Se valida cuándo el planificador conoce el pedido, no la hora real
             * de llegada. En producción ambas coinciden. En la simulación batch
             * diaria el conjunto sintético del día puede conocerse desde las 00:00,
             * conservando la hora real para espera, SLA y deadline.
             */
            if (p.getFechaDisponiblePlanificacion().isAfter(instante)) {
                throw new IllegalArgumentException("No se pueden anticipar pedidos futuros");
            }
        }

        if (vehiculos.stream()
                .map(Vehiculo::getIdVehiculo)
                .distinct()
                .count() != vehiculos.size()) {
            throw new IllegalArgumentException("Vehículo duplicado");
        }
    }

    public ContextoPlanificacion(
            List<Pedido> pedidos,
            List<Vehiculo> vehiculos,
            List<Almacen> almacenes,
            LocalDateTime instante,
            ReglasPlanificacion reglas) {
        this(pedidos, vehiculos, almacenes, instante, reglas, Set.of());
    }

    public List<Pedido> getPedidos() { return pedidos; }
    public List<Vehiculo> getVehiculos() { return vehiculos; }
    public List<Almacen> getAlmacenes() { return almacenes; }
}