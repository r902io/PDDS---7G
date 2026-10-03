package pe.edu.pucp.sisrap.simulacion.dominio;

import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;

public record ContextoOperativo(
        Configuracion configuracion,
        ContextoPlanificacion contexto) {
}
