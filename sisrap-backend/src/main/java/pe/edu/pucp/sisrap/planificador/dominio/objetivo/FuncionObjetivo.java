package pe.edu.pucp.sisrap.planificador.dominio.objetivo;
import java.util.HashMap;
import java.util.HashSet;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.*;
import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
/** Informe v03: F = T + beta1 R + beta2 N + beta3 V. Costo monetario separado. */
public final class FuncionObjetivo {
    private final double beta1,beta2,beta3;
    private final ContextoPlanificacion contexto;
    public FuncionObjetivo(Configuracion c, ContextoPlanificacion contexto){
        beta1=c.numero("objetivo.beta1"); beta2=c.numero("objetivo.beta2"); beta3=c.numero("objetivo.beta3");
        if(beta1<=0 || beta2<=0 || beta3<=0) throw new IllegalArgumentException("Pesos deben ser positivos");
        this.contexto=contexto;
    }
    public double calcular(Solucion s){
        double t=0,r=0,v=0,costo=0;
        var vistos=new HashSet<Long>(); var vehiculos=new HashSet<String>();
        var consumo=new HashMap<String,Integer>();
        var universo=new HashSet<Long>();
        for(var p:contexto.getPedidos()) universo.add(p.getIdPedido());
        for(var ruta:s.getRutas()){
            ruta.recalcular(); costo+=ruta.getCostoTotal();
            if(ruta.getSecuenciaPedidos().isEmpty()) continue;
            if(!vehiculos.add(ruta.getVehiculo().getIdVehiculo())) v++;
            if(!ruta.getVehiculo().isDisponible()) v++;
            if(ruta.cargaTotal()>ruta.getVehiculo().getCapacidadPaquetes()) v++;
            consumo.merge(ruta.getAlmacenOrigen().getIdAlmacen(),ruta.cargaTotal(),Integer::sum);
            for(int i=0;i<ruta.getSecuenciaPedidos().size();i++){
                var p=ruta.getSecuenciaPedidos().get(i);
                if(!universo.contains(p.getIdPedido()) || !vistos.add(p.getIdPedido())) v++;
                t+=ruta.tiempoAtencionDe(i); r+=ruta.retrasoDe(i);
            }
        }
        for(var a:contexto.getAlmacenes()){
            if(a.getCapacidadMaxima()!=null && consumo.getOrDefault(a.getIdAlmacen(),0)>a.getStockActual()) v++;
        }
        var faltantes=contexto.getPedidos().stream().filter(p->!vistos.contains(p.getIdPedido())).toList();
        s.setPedidosNoAsignados(new java.util.ArrayList<>(faltantes));
        int n=faltantes.size();
        double f=t+beta1*r+beta2*n+beta3*v;
        s.setValorT(t); s.setValorR(r); s.setValorN(n); s.setValorV(v);
        s.setCostoTransporte(costo); s.setValorFuncionObjetivo(f); s.setEsFactible(v==0);
        return f;
    }
}
