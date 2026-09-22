package pe.edu.pucp.sisrap.experimentacion.dominio;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.sisrap.planificador.dominio.modelo.PuntoConvergencia;
public record InformeExperimento(String id,String modelo,String versionImplementacion,SolicitudExperimento solicitud,
        Instantanea instantanea,List<Corrida> corridas,List<Resumen> resumenes,List<String> advertencias) {
    public record PedidoDato(long id,String cliente,int cantidad,String prioridad,int horas,
            java.time.LocalDateTime registro,int x,int y) {}
    public record VehiculoDato(String id,int capacidad,double velocidad,double costo,boolean disponible,int x,int y) {}
    public record AlmacenDato(String id,int x,int y,Integer capacidad,Integer stock) {}
    public record Instantanea(pe.edu.pucp.sisrap.parametros.dominio.Configuracion configuracion,
            java.time.LocalDateTime instante,pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion reglas,
            int anchoKm,int altoKm,List<PedidoDato> pedidos,List<VehiculoDato> vehiculos,List<AlmacenDato> almacenes) {
        public static Instantanea desde(DatosExperimento datos){
            var c=datos.contexto();
            return new Instantanea(datos.configuracion(),c.instante(),c.reglas(),datos.anchoKm(),datos.altoKm(),
                c.getPedidos().stream().map(p->new PedidoDato(p.getIdPedido(),p.getIdCliente(),p.getCantidadQq(),
                    p.getPrioridad().name(),p.getHorasLimite(),p.getFechaLlegada(),p.getUbicacion().getX(),p.getUbicacion().getY())).toList(),
                c.getVehiculos().stream().map(v->new VehiculoDato(v.getIdVehiculo(),v.getCapacidadPaquetes(),
                    v.getVelocidadKmh(),v.getCostoPorKm(),v.isDisponible(),v.getPosicionActual().getX(),v.getPosicionActual().getY())).toList(),
                c.getAlmacenes().stream().map(a->new AlmacenDato(a.getIdAlmacen(),a.getUbicacion().getX(),
                    a.getUbicacion().getY(),a.getCapacidadMaxima(),a.getStockActual())).toList());
        }
    }
    public record Asignacion(String vehiculo,String almacen,List<Long> pedidos) {}
    /**
     * Los campos de incidencia (pedidosAfectadosIncidencia, vehiculosEnAveriaIncidente, tiempoReplanificacionMs,
     * replanificacionExitosa) solo se llenan para el escenario OPERACION_DIARIA con perfil ALTA o CRITICA
     * (ver SimuladorIncidencias); en el resto quedan null.
     */
    public record Corrida(String algoritmo,int tamanio,long semilla,double tiempoMs,double objetivo,double t,
        double r,int n,double v,boolean factible,double cumplimiento,Double cumplimientoPrioritarios,
        double costo,double distancia,double utilizacion,Double temperaturaInicial,
        List<PuntoConvergencia> convergencia,List<Asignacion> rutas,List<Long> noAsignados,
        String escenarioOperativo,String perfilPresion,Integer pedidosAfectadosIncidencia,
        Integer vehiculosEnAveriaIncidente,Double tiempoReplanificacionMs,Boolean replanificacionExitosa) {}
    public record Estadistica(double media,double mediana,Double desviacionMuestral,double mejor,double peor) {}
    public record Resumen(String algoritmo,int tamanio,Map<String,Estadistica> metricas) {}
}