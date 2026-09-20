package pe.edu.pucp.sisrap.planificador.dominio.modelo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashSet;
import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion;
public record ContextoPlanificacion(List<Pedido> pedidos, List<Vehiculo> vehiculos,
        List<Almacen> almacenes, LocalDateTime instante, ReglasPlanificacion reglas) {
    public ContextoPlanificacion {
        pedidos=List.copyOf(pedidos); vehiculos=List.copyOf(vehiculos); almacenes=List.copyOf(almacenes);
        if(almacenes.isEmpty() || instante==null || reglas==null) throw new IllegalArgumentException("Contexto incompleto");
        var ids=new HashSet<Long>();
        for(var p:pedidos) {
            if(!ids.add(p.getIdPedido())) throw new IllegalArgumentException("Pedido duplicado");
            if(p.getFechaLlegada().isAfter(instante)) throw new IllegalArgumentException("No se pueden anticipar pedidos futuros");
        }
        if(vehiculos.stream().map(Vehiculo::getIdVehiculo).distinct().count()!=vehiculos.size())
            throw new IllegalArgumentException("Vehículo duplicado");
    }
    public List<Pedido> getPedidos(){return pedidos;}
    public List<Vehiculo> getVehiculos(){return vehiculos;}
    public List<Almacen> getAlmacenes(){return almacenes;}
}
