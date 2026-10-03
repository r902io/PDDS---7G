package pe.edu.pucp.sisrap.bloqueo.dominio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

public interface RepositorioBloqueos {

    List<BloqueoOperativo> listar();

    Optional<BloqueoOperativo> buscar(
            long idIncidencia
    );

    BloqueoOperativo crear(
            LocalDateTime inicio,
            LocalDateTime fin,
            List<Nodo> vertices
    );

    boolean cancelar(
            long idIncidencia
    );
}