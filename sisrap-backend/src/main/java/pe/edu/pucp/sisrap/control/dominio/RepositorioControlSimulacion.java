package pe.edu.pucp.sisrap.control.dominio;

import java.util.Optional;

public interface RepositorioControlSimulacion {

    Optional<ControlSimulacion> obtener();

    void guardar(ControlSimulacion control);

    void liberar();
}