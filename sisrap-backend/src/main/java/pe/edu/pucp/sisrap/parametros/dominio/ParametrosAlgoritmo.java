package pe.edu.pucp.sisrap.parametros.dominio;
import java.util.List;

public final class ParametrosAlgoritmo {
    public final int tamanioPoblacion, numGeneraciones, elitismo, tamanioTorneo;
    public final double probCruzamiento, probMutacion, proporcionVoraz;
    public final double temperaturaInicial, temperaturaFinal, factorEnfriamiento, factorRecalentamiento;
    public final int iteracionesPorTemperatura, muestrasTemperatura, maxNivelesTemperatura;
    public final double aceptacionObjetivo;
    public final boolean calibrarTemperatura;
    public final List<String> movimientos;
    public final Long semillaAleatoria;
    public ParametrosAlgoritmo(Configuracion c, long semilla) {
        tamanioPoblacion=c.entero("ga.poblacion"); numGeneraciones=c.entero("ga.generaciones");
        elitismo=c.entero("ga.elitismo"); tamanioTorneo=c.entero("ga.torneo");
        probCruzamiento=c.numero("ga.cruzamiento"); probMutacion=c.numero("ga.mutacion");
        proporcionVoraz=c.numero("ga.proporcionVoraz");
        temperaturaInicial=c.numero("sa.temperaturaInicial"); temperaturaFinal=c.numero("sa.temperaturaFinal");
        factorEnfriamiento=c.numero("sa.enfriamiento"); factorRecalentamiento=c.numero("sa.recalentamiento");
        iteracionesPorTemperatura=c.entero("sa.iteraciones"); muestrasTemperatura=c.entero("sa.muestras");
        maxNivelesTemperatura=c.entero("sa.maxNiveles"); aceptacionObjetivo=c.numero("sa.aceptacion");
        calibrarTemperatura=c.booleano("sa.calibrar");
        movimientos=List.of(c.texto("busqueda.movimientos").split(","));
        semillaAleatoria=semilla;
        if(tamanioPoblacion<2 || numGeneraciones<1 || elitismo<1 || elitismo>=tamanioPoblacion
            || tamanioTorneo<2 || tamanioTorneo>tamanioPoblacion) throw new IllegalArgumentException("Parámetros GA inválidos");
        for(double p : new double[]{probCruzamiento,probMutacion,proporcionVoraz})
            if(p<0 || p>1) throw new IllegalArgumentException("Probabilidad fuera de [0,1]");
        if(temperaturaFinal<=0 || temperaturaInicial<=temperaturaFinal || factorEnfriamiento<=0 || factorEnfriamiento>=1
            || factorRecalentamiento<=0 || aceptacionObjetivo<=0 || aceptacionObjetivo>=1
            || iteracionesPorTemperatura<1 || muestrasTemperatura<1 || maxNivelesTemperatura<1)
            throw new IllegalArgumentException("Parámetros SA inválidos");
        if(movimientos.isEmpty() || !List.of("DOS_OPT","SWAP","RELOCATE","CROSS_ROUTE","VEHICLE_CHANGE").containsAll(movimientos))
            throw new IllegalArgumentException("Vecindad no reconocida");
    }
}