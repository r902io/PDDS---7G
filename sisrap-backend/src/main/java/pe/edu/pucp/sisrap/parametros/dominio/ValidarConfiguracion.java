package pe.edu.pucp.sisrap.parametros.dominio;
import java.util.*;
import pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad;
public final class ValidarConfiguracion {
    private ValidarConfiguracion(){}
    public static void ejecutar(Configuracion c){
        new ParametrosAlgoritmo(c,c.largo("experimento.semillaBase"));
        ReglasPlanificacion.desde(c);
        for(String clave:List.of("objetivo.beta1","objetivo.beta2","objetivo.beta3"))
            if(c.numero(clave)<=0)throw new IllegalArgumentException("Peso inválido: "+clave);
        int rep=c.entero("experimento.repeticiones");
        if(rep<1)throw new IllegalArgumentException("Repeticiones inválidas");
        try{Math.addExact(c.largo("experimento.semillaBase"),rep-1L);}
        catch(ArithmeticException e){throw new IllegalArgumentException("Rango de semillas fuera de long",e);}
        Set<Integer> tamanios=new HashSet<>(),plazos=new HashSet<>();
        for(String n:c.texto("experimento.tamanios").split(",")){
            int valor=Integer.parseInt(n.trim());
            if(valor<1 || !tamanios.add(valor))throw new IllegalArgumentException("Tamaños inválidos");
        }
        for(var prioridad:TipoPrioridad.values()){
            int plazo=c.entero("prioridad."+prioridad.name());
            if(plazo<=0 || !plazos.add(plazo))throw new IllegalArgumentException("Plazos duplicados o inválidos");
        }
    }
}
