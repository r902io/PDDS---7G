package pe.edu.pucp.sisrap.planificador.modelo;

import java.util.List;

import pe.edu.pucp.sisrap.dominio.Almacen;
import pe.edu.pucp.sisrap.dominio.Pedido;
import pe.edu.pucp.sisrap.dominio.Vehiculo;

public class ContextoPlanificacion {
    private final List<Pedido> pedidos;
    private final List<Vehiculo> vehiculos;
    private final List<Almacen> almacenes;

    public ContextoPlanificacion(List<Pedido> pedidos, List<Vehiculo> vehiculos, List<Almacen> almacenes) {
        this.pedidos = pedidos;
        this.vehiculos = vehiculos;
        this.almacenes = almacenes;
    }

    public List<Pedido> getPedidos() { return pedidos; }
    public List<Vehiculo> getVehiculos() { return vehiculos; }
    public List<Almacen> getAlmacenes() { return almacenes; }
}
