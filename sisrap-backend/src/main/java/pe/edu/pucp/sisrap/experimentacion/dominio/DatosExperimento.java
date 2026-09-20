package pe.edu.pucp.sisrap.experimentacion.dominio;
import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
public record DatosExperimento(Configuracion configuracion,ContextoPlanificacion contexto,int anchoKm,int altoKm) {}
