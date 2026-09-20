package pe.edu.pucp.sisrap.planificador.dominio.algoritmo;

import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;

public interface IAlgoritmoPlanificacion {
    java.util.List<pe.edu.pucp.sisrap.planificador.dominio.modelo.PuntoConvergencia> getConvergencia();
    Solucion planificar(ContextoPlanificacion contexto);

    Solucion replanificar(Solucion solucionActual, ContextoPlanificacion contextoActualizado);
}
