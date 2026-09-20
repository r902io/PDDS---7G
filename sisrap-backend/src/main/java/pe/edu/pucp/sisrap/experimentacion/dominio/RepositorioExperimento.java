package pe.edu.pucp.sisrap.experimentacion.dominio;
public interface RepositorioExperimento {
    DatosExperimento cargar(SolicitudExperimento solicitud);
    void guardar(InformeExperimento informe);
    InformeExperimento obtener(String id);
}
