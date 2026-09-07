package pe.edu.pucp.sisrap.planificador.algoritmo;

import pe.edu.pucp.sisrap.planificador.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.modelo.Solucion;

public interface IAlgoritmoPlanificacion {
    Solucion planificar(ContextoPlanificacion contexto);

    Solucion replanificar(Solucion solucionActual, ContextoPlanificacion contextoActualizado);
}
