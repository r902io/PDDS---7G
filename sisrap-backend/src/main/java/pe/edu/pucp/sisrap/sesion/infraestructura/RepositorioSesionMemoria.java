package pe.edu.pucp.sisrap.sesion.infraestructura;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Repository;

import pe.edu.pucp.sisrap.sesion.dominio.RepositorioSesion;
import pe.edu.pucp.sisrap.sesion.dominio.Sesion;

@Repository
public class RepositorioSesionMemoria implements RepositorioSesion {

    private final ConcurrentMap<String, Sesion> sesiones =
            new ConcurrentHashMap<>();

    @Override
    public void guardar(String hashToken, Sesion sesion) {
        sesiones.put(hashToken, sesion);
    }

    @Override
    public Optional<Sesion> buscar(String hashToken) {
        return Optional.ofNullable(sesiones.get(hashToken));
    }

    @Override
    public void eliminar(String hashToken) {
        sesiones.remove(hashToken);
    }

    @Override
    public void limpiarExpiradas(Instant ahora) {
        sesiones.forEach((hash, sesion) -> {
            if (sesion.expirada(ahora)) {
                sesiones.remove(hash, sesion);
            }
        });
    }
}