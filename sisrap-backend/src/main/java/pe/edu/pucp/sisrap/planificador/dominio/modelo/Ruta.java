package pe.edu.pucp.sisrap.planificador.dominio.modelo;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
/** Una salida por vehículo, sin recargas intermedias. Modelo estático explícito. */
public class Ruta {
    private final Vehiculo vehiculo;
    private final Almacen almacenOrigen;
    private final LocalDateTime inicio;
    private final ReglasPlanificacion reglas;
    private final List<Pedido> secuenciaPedidos=new ArrayList<>();
    private double distanciaTotalKm, costoTotal, tiempoTotalHoras;
    private java.util.Set<String> nodosBloqueados=java.util.Set.of();
    public Ruta(Vehiculo vehiculo, Almacen origen, LocalDateTime inicio, ReglasPlanificacion reglas) {
        this.vehiculo=vehiculo; this.almacenOrigen=origen; this.inicio=inicio; this.reglas=reglas;
    }
    public void setNodosBloqueados(java.util.Set<String> n){this.nodosBloqueados=n==null?java.util.Set.of():java.util.Set.copyOf(n);}
    public java.util.Set<String> getNodosBloqueados(){return nodosBloqueados;}
    // ponytail: O(segmentos x bloqueos) naive; vuelta en U = +2km si el rectángulo Manhattan contiene un nodo bloqueado.
    static int extraPorBloqueo(pe.edu.pucp.sisrap.geografia.dominio.Nodo a, pe.edu.pucp.sisrap.geografia.dominio.Nodo b, java.util.Set<String> bloqueados){
        if(bloqueados.isEmpty()) return 0;
        int x1=Math.min(a.getX(),b.getX()), x2=Math.max(a.getX(),b.getX());
        int y1=Math.min(a.getY(),b.getY()), y2=Math.max(a.getY(),b.getY());
        for(var s:bloqueados){
            int c=s.indexOf(','); int x=Integer.parseInt(s.substring(0,c)), y=Integer.parseInt(s.substring(c+1));
            if(x>=x1 && x<=x2 && y>=y1 && y<=y2) return 2;
        }
        return 0;
    }
    public Ruta copiar() {
        Ruta copia=new Ruta(vehiculo,almacenOrigen,inicio,reglas);
        copia.nodosBloqueados=nodosBloqueados;
        copia.secuenciaPedidos.addAll(secuenciaPedidos); copia.recalcular(); return copia;
    }
    public int cargaTotal(){return secuenciaPedidos.stream().mapToInt(Pedido::getCantidadQq).sum();}
    public void recalcular() {
        double distancia=0;
        var posicion=almacenOrigen.getUbicacion();
        for(var p:secuenciaPedidos){distancia+=posicion.distanciaManhattan(p.getUbicacion())+extraPorBloqueo(posicion,p.getUbicacion(),nodosBloqueados); posicion=p.getUbicacion();}
        if(reglas.incluirRetorno() && !secuenciaPedidos.isEmpty()) distancia+=posicion.distanciaManhattan(almacenOrigen.getUbicacion())+extraPorBloqueo(posicion,almacenOrigen.getUbicacion(),nodosBloqueados);
        distanciaTotalKm=distancia*reglas.distanciaNodoKm();
        costoTotal=distanciaTotalKm*vehiculo.getCostoPorKm();
        tiempoTotalHoras=distanciaTotalKm/vehiculo.getVelocidadKmh()+secuenciaPedidos.size()*reglas.servicioHoras();
    }
    public double horaLlegadaDe(int indice) {
        double distancia=0;
        var posicion=almacenOrigen.getUbicacion();
        for(int i=0;i<=indice;i++){
            var p=secuenciaPedidos.get(i); distancia+=posicion.distanciaManhattan(p.getUbicacion())+extraPorBloqueo(posicion,p.getUbicacion(),nodosBloqueados); posicion=p.getUbicacion();
        }
        return distancia*reglas.distanciaNodoKm()/vehiculo.getVelocidadKmh()+indice*reglas.servicioHoras();
    }
    public double horaEntregaDe(int i){return horaLlegadaDe(i)+reglas.servicioHoras();}
    public double tiempoAtencionDe(int i){
        return Duration.between(secuenciaPedidos.get(i).getFechaLlegada(),inicio).toNanos()/3_600_000_000_000.0+horaEntregaDe(i);
    }
    public double retrasoDe(int i){
        var p=secuenciaPedidos.get(i);
        double disponible=Duration.between(inicio,p.getFechaLimite()).toNanos()/3_600_000_000_000.0;
        return Math.max(0,(reglas.servicioDentroPlazo()?horaEntregaDe(i):horaLlegadaDe(i))-disponible);
    }
    public Vehiculo getVehiculo(){return vehiculo;}
    public Almacen getAlmacenOrigen(){return almacenOrigen;}
    public List<Pedido> getSecuenciaPedidos(){return secuenciaPedidos;}
    public double getDistanciaTotalKm(){return distanciaTotalKm;}
    public double getCostoTotal(){return costoTotal;}
    public double getTiempoTotalHoras(){return tiempoTotalHoras;}
}
