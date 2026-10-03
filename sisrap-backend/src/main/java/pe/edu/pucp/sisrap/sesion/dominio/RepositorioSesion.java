package pe.edu.pucp.sisrap.sesion.dominio;

import java.time.Instant;
import java.util.Optional;

public interface RepositorioSesion {

    void guardar(String hashToken, Sesion sesion);

    Optional<Sesion> buscar(String hashToken);

    void eliminar(String hashToken);

    void limpiarExpiradas(Instant ahora);
}