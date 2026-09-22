package pe.edu.pucp.sisrap.experimentacion.aplicacion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

import pe.edu.pucp.sisrap.experimentacion.dominio.DatosExperimento;
import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento;
import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Corrida;
import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Estadistica;
import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Instantanea;
import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Resumen;
import pe.edu.pucp.sisrap.experimentacion.dominio.RepositorioExperimento;
import pe.edu.pucp.sisrap.experimentacion.dominio.SolicitudExperimento;
import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
import pe.edu.pucp.sisrap.parametros.dominio.ParametrosAlgoritmo;
import pe.edu.pucp.sisrap.parametros.dominio.ValidarConfiguracion;
import pe.edu.pucp.sisrap.planificador.dominio.algoritmo.AlgoritmoGenetico;
import pe.edu.pucp.sisrap.planificador.dominio.algoritmo.IAlgoritmoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.algoritmo.RecocidoSimulado;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;
import pe.edu.pucp.sisrap.planificador.dominio.objetivo.FuncionObjetivo;
public final class EjecutarExperimento {
    private final RepositorioExperimento repositorio;
    private final int maxRepeticiones,maxPedidos,maxCorridas;
    public EjecutarExperimento(RepositorioExperimento r,int maxRepeticiones,int maxPedidos,int maxCorridas){
        repositorio=r;this.maxRepeticiones=maxRepeticiones;this.maxPedidos=maxPedidos;this.maxCorridas=maxCorridas;
    }
    public InformeExperimento ejecutar(SolicitudExperimento solicitud){
        DatosExperimento datos=repositorio.cargar(solicitud);
        Configuracion c=datos.configuracion();
        ValidarConfiguracion.ejecutar(c);
        int rep=c.entero("experimento.repeticiones");
        long semilla=c.largo("experimento.semillaBase");
        int[] tamanios=Arrays.stream(c.texto("experimento.tamanios").split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
        if(rep<1 || rep>maxRepeticiones || (long)rep*tamanios.length*2>maxCorridas)
            throw new IllegalArgumentException("Cantidad de corridas fuera del límite del servidor");
        if(Arrays.stream(tamanios).distinct().count()!=tamanios.length)throw new IllegalArgumentException("Tamaños duplicados");
        new ParametrosAlgoritmo(c,semilla);
        for(int n:tamanios)if(n<1 || n>maxPedidos || n>datos.contexto().getPedidos().size())
            throw new IllegalArgumentException("No hay suficientes pedidos o el tamaño excede el límite: "+n);
        Math.addExact(semilla,rep-1L);
        List<Corrida> corridas=new ArrayList<>();
        for(int n:tamanios){
            var base=datos.contexto();
            var contexto=new ContextoPlanificacion(base.getPedidos().subList(0,n),base.getVehiculos(),
                base.getAlmacenes(),base.instante(),base.reglas());
            for(int repeticion=0;repeticion<rep;repeticion++){
                long semillaCorrida=semilla+repeticion;
                // Alternar el orden disminuye el sesgo de calentamiento entre algoritmos.
                for(String algoritmo:repeticion%2==0?List.of("GENETICO","RECOCIDO_SIMULADO"):List.of("RECOCIDO_SIMULADO","GENETICO")){
                    var p=new ParametrosAlgoritmo(c,semillaCorrida);
                    var objetivo=new FuncionObjetivo(c,contexto);
                    IAlgoritmoPlanificacion motor=algoritmo.equals("GENETICO")?new AlgoritmoGenetico(p,objetivo):new RecocidoSimulado(p,objetivo);
                    long inicio=System.nanoTime();
                    Solucion s=motor.planificar(contexto);
                    double ms=(System.nanoTime()-inicio)/1_000_000.0;
                    corridas.add(MedicionCorridas.medir(algoritmo,n,semillaCorrida,ms,s,contexto,motor));
                }
            }
        }
        var informe=new InformeExperimento(UUID.randomUUID().toString(),"ESTATICO_UN_VIAJE_SIN_INCIDENCIAS","0.1.0",
            solicitud,Instantanea.desde(datos),List.copyOf(corridas),resumir(corridas),
            List.of("No equivale a operación diaria, simulación 5D ni colapso logístico.",
                "Una salida por vehículo desde el central; pedidos indivisibles; sin recargas, turnos, alimentación ni averías. Bloqueos Q7: +2km por tramo afectado, V si entrega en nodo bloqueado.",
                "Los datos de flota y almacenes se leen de la BD. Las posiciones iniciales se normalizan al central.",
                "F usa los pesos del perfil; no se garantiza dominancia lexicográfica sin calibrar cotas.",
                "T incluye servicio; el SLA usa llegada si operacion.servicioDentroPlazo=false.",
                "Stock y estados operativos no son modificados. Las tasas son predicciones de planes estáticos.",
                "Comparación con presupuestos propios GA/SA; consultar evaluaciones y tiempo, no asumir igual esfuerzo.",
                "La desviación es muestral; null significa menos de dos corridas. Prioritarios sin población: null."));
        repositorio.guardar(informe);return informe;
    }
    private List<Resumen> resumir(List<Corrida> corridas){
        Map<String,List<Corrida>> grupos=new LinkedHashMap<>();
        for(var r:corridas)grupos.computeIfAbsent(r.algoritmo()+":"+r.tamanio(),k->new ArrayList<>()).add(r);
        List<Resumen> salida=new ArrayList<>();
        for(var grupo:grupos.values()){
            Map<String,Estadistica> m=new LinkedHashMap<>();
            m.put("objetivo",estadistica(grupo,Corrida::objetivo,false));
            m.put("tiempoMs",estadistica(grupo,Corrida::tiempoMs,false));
            m.put("cumplimiento",estadistica(grupo,Corrida::cumplimiento,true));
            m.put("retrasoHoras",estadistica(grupo,Corrida::r,false));
            m.put("noAsignados",estadistica(grupo,Corrida::n,false));
            m.put("costo",estadistica(grupo,Corrida::costo,false));
            m.put("distanciaKm",estadistica(grupo,Corrida::distancia,false));
            salida.add(new Resumen(grupo.get(0).algoritmo(),grupo.get(0).tamanio(),Map.copyOf(m)));
        }
        return List.copyOf(salida);
    }
    private Estadistica estadistica(List<Corrida> filas,ToDoubleFunction<Corrida> f,boolean maximizar){
        return MedicionCorridas.estadistica(filas.stream().mapToDouble(f).toArray(),maximizar);
    }
}