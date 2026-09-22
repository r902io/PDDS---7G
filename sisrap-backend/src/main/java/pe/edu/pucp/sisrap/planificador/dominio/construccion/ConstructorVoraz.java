package pe.edu.pucp.sisrap.planificador.dominio.construccion;
import java.util.*;
import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.*;
public final class ConstructorVoraz {
    private ConstructorVoraz(){}
    public static Solucion construir(ContextoPlanificacion c,Random random,boolean voraz){
        Solucion s=new Solucion(); s.setRutas(crearRutasVacias(c));
        insertarPedidos(s.getRutas(),ordenarPedidos(c.getPedidos(),random,voraz),s.getPedidosNoAsignados());
        s.getRutas().forEach(Ruta::recalcular); return s;
    }
    public static List<Ruta> crearRutasVacias(ContextoPlanificacion c){
        Almacen central=c.getAlmacenes().stream().filter(a->a.getCapacidadMaxima()==null).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Se requiere almacén central"));
        List<Ruta> rutas=new ArrayList<>();
        for(var v:c.getVehiculos()){var r=new Ruta(v,central,c.instante(),c.reglas()); r.setNodosBloqueados(c.nodosBloqueados()); rutas.add(r);}
        return rutas;
    }
    public static List<Pedido> ordenarPedidos(List<Pedido> pedidos,Random random,boolean voraz){
        var copia=new ArrayList<>(pedidos); Collections.shuffle(copia,random);
        if(voraz) copia.sort(Comparator.comparing(Pedido::getFechaLimite));
        return copia;
    }
    public static void insertarPedidos(List<Ruta> rutas,List<Pedido> pedidos,List<Pedido> noAsignados){
        for(var pedido:pedidos){
            Ruta mejor=null; double menor=Double.POSITIVE_INFINITY;
            for(var ruta:rutas){
                if(!ruta.getVehiculo().isDisponible() || ruta.cargaTotal()+pedido.getCantidadQq()>ruta.getVehiculo().getCapacidadPaquetes()) continue;
                var a=ruta.getAlmacenOrigen();
                int consumo=rutas.stream().filter(x->x.getAlmacenOrigen().getIdAlmacen().equals(a.getIdAlmacen())).mapToInt(Ruta::cargaTotal).sum();
                if(a.getCapacidadMaxima()!=null && consumo+pedido.getCantidadQq()>a.getStockActual()) continue;
                ruta.getSecuenciaPedidos().add(pedido);
                double tiempo=ruta.horaLlegadaDe(ruta.getSecuenciaPedidos().size()-1);
                ruta.getSecuenciaPedidos().remove(ruta.getSecuenciaPedidos().size()-1);
                if(tiempo<menor){menor=tiempo;mejor=ruta;}
            }
            if(mejor==null)noAsignados.add(pedido); else mejor.getSecuenciaPedidos().add(pedido);
        }
    }
    /** Reutiliza el orden anterior, pero siempre reconstruye con vehículos/pedidos actuales. */
    public static Solucion reparar(Solucion anterior,ContextoPlanificacion c,Random random){
        var porId=new HashMap<Long,Pedido>();
        c.getPedidos().forEach(p->porId.put(p.getIdPedido(),p));
        var orden=new ArrayList<Pedido>();
        for(var r:anterior.getRutas()) for(var p:r.getSecuenciaPedidos()){
            var vigente=porId.remove(p.getIdPedido()); if(vigente!=null) orden.add(vigente);
        }
        orden.addAll(ordenarPedidos(new ArrayList<>(porId.values()),random,true));
        Solucion s=new Solucion(); s.setRutas(crearRutasVacias(c));
        insertarPedidos(s.getRutas(),orden,s.getPedidosNoAsignados());
        return s;
    }
}
