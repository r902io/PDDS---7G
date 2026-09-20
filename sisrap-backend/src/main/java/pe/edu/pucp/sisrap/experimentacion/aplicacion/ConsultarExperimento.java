package pe.edu.pucp.sisrap.experimentacion.aplicacion;
import pe.edu.pucp.sisrap.experimentacion.dominio.*;
public final class ConsultarExperimento {
    private final RepositorioExperimento repositorio;
    public ConsultarExperimento(RepositorioExperimento r){repositorio=r;}
    public InformeExperimento obtener(String id){return repositorio.obtener(id);}
    public String csv(String id){
        StringBuilder out=new StringBuilder("algoritmo,tamanio,semilla,tiempo_ms,F,T,R,N,V,factible,cumplimiento_pct,prioritarios_pct,costo,distancia_km,utilizacion_pct,temperatura_inicial\n");
        for(var r:obtener(id).corridas()){
            out.append(r.algoritmo()).append(',').append(r.tamanio()).append(',').append(r.semilla()).append(',')
                .append(r.tiempoMs()).append(',').append(r.objetivo()).append(',').append(r.t()).append(',')
                .append(r.r()).append(',').append(r.n()).append(',').append(r.v()).append(',').append(r.factible()).append(',')
                .append(r.cumplimiento()).append(',').append(r.cumplimientoPrioritarios()==null?"":r.cumplimientoPrioritarios()).append(',')
                .append(r.costo()).append(',').append(r.distancia()).append(',').append(r.utilizacion()).append(',')
                .append(r.temperaturaInicial()==null?"":r.temperaturaInicial()).append('\n');
        }
        return out.toString();
    }
}
