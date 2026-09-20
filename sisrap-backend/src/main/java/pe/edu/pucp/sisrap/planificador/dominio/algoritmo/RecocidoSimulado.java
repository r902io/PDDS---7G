package pe.edu.pucp.sisrap.planificador.dominio.algoritmo;
import java.util.*;
import pe.edu.pucp.sisrap.parametros.dominio.ParametrosAlgoritmo;
import pe.edu.pucp.sisrap.planificador.dominio.construccion.ConstructorVoraz;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.*;
import pe.edu.pucp.sisrap.planificador.dominio.objetivo.FuncionObjetivo;
public final class RecocidoSimulado implements IAlgoritmoPlanificacion {
    private final ParametrosAlgoritmo parametros;
    private final FuncionObjetivo objetivo;
    private final Random random;
    private final Movimientos movimientos;
    private final List<PuntoConvergencia> traza=new ArrayList<>();
    private int evaluaciones;
    private double temperaturaUsada;
    public RecocidoSimulado(ParametrosAlgoritmo p,FuncionObjetivo objetivo){
        parametros=p;this.objetivo=objetivo;random=new Random(p.semillaAleatoria);
        movimientos=new Movimientos(random,p.movimientos);
    }
    public Solucion planificar(ContextoPlanificacion c){
        return ejecutar(ConstructorVoraz.construir(c,random,true),1);
    }
    public Solucion replanificar(Solucion s,ContextoPlanificacion c){
        return ejecutar(ConstructorVoraz.reparar(s,c,random),parametros.factorRecalentamiento);
    }
    private double evaluar(Solucion s){evaluaciones++;return objetivo.calcular(s);}
    private Solucion ejecutar(Solucion actual,double factor){
        evaluaciones=0;traza.clear();evaluar(actual);
        temperaturaUsada=(parametros.calibrarTemperatura?calibrar(actual):parametros.temperaturaInicial)*factor;
        double temperatura=temperaturaUsada;
        Solucion mejor=actual.copiar();
        traza.add(new PuntoConvergencia(0,evaluaciones,mejor.getValorFuncionObjetivo()));
        for(int nivel=1;nivel<=parametros.maxNivelesTemperatura && temperatura>parametros.temperaturaFinal;nivel++){
            for(int i=0;i<parametros.iteracionesPorTemperatura;i++){
                Solucion vecino=movimientos.vecino(actual);evaluar(vecino);
                double delta=vecino.getValorFuncionObjetivo()-actual.getValorFuncionObjetivo();
                if(delta<=0 || random.nextDouble()<Math.exp(-delta/temperatura)){
                    actual=vecino;
                    if(actual.getValorFuncionObjetivo()<mejor.getValorFuncionObjetivo())mejor=actual.copiar();
                }
            }
            traza.add(new PuntoConvergencia(nivel,evaluaciones,mejor.getValorFuncionObjetivo()));
            temperatura*=parametros.factorEnfriamiento;
        }
        return mejor;
    }
    /** Piloto: resuelve promedio(exp(-delta/T)) = aceptación sobre deltas positivos.
     * Aproximación empírica inspirada en la sección 3.3; no implementación literal del paper. */
    private double calibrar(Solucion base){
        List<Double> deltas=new ArrayList<>();
        for(int i=0;i<parametros.muestrasTemperatura;i++){
            double delta=evaluar(movimientos.vecino(base))-base.getValorFuncionObjetivo();
            if(delta>0)deltas.add(delta);
        }
        if(deltas.isEmpty())return parametros.temperaturaInicial;
        double max=deltas.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        double inferior=0,superior=-max/Math.log(parametros.aceptacionObjetivo);
        for(int i=0;i<80;i++){
            double medio=(inferior+superior)/2;
            double tasa=deltas.stream().mapToDouble(d->Math.exp(-d/medio)).average().orElseThrow();
            if(tasa<parametros.aceptacionObjetivo)inferior=medio;else superior=medio;
        }
        return Math.max(Math.nextUp(parametros.temperaturaFinal),superior);
    }
    public List<PuntoConvergencia> getConvergencia(){return List.copyOf(traza);}
    public double getTemperaturaUsada(){return temperaturaUsada;}
}
