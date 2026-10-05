package pe.edu.pucp.sisrap.bloqueo.aplicacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import pe.edu.pucp.sisrap.bloqueo.dominio.BloqueoOperativo;
import pe.edu.pucp.sisrap.bloqueo.dominio.RepositorioBloqueos;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.sesion.aplicacion.GestionarSesiones;

@Service
public final class GestionarBloqueos {

    private final RepositorioBloqueos repositorio;
    private final GestionarSesiones sesiones;

    public GestionarBloqueos(
            RepositorioBloqueos repositorio,
            GestionarSesiones sesiones
    ) {
        this.repositorio = repositorio;
        this.sesiones = sesiones;
    }

    public List<BloqueoOperativo> listar() {
        return repositorio.listar();
    }

    public BloqueoOperativo buscar(
            long idIncidencia
    ) {

        return repositorio.buscar(idIncidencia)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "No existe el bloqueo "
                                        + idIncidencia
                        )
                );
    }

    public BloqueoOperativo crear(
            String tokenSesion,
            LocalDateTime inicio,
            LocalDateTime fin,
            List<Nodo> vertices
    ) {

        validarSesion(tokenSesion);

        return repositorio.crear(
                inicio,
                fin,
                vertices
        );
    }

    public void cancelar(
            String tokenSesion,
            long idIncidencia
    ) {

        validarSesion(tokenSesion);

        if (idIncidencia <= 0) {
            throw new IllegalArgumentException(
                    "Id de bloqueo inválido"
            );
        }

        if (!repositorio.cancelar(idIncidencia)) {

            throw new NoSuchElementException(
                    "No existe el bloqueo "
                            + idIncidencia
            );
        }
    }

    private void validarSesion(String token) {

        try {
            sesiones.validar(token);
        } catch (IllegalArgumentException e) {

            throw new SesionNoAutorizadaException(
                    e.getMessage()
            );
        }
    }

    public static final class SesionNoAutorizadaException
            extends RuntimeException {

        public SesionNoAutorizadaException(
                String mensaje
        ) {
            super(mensaje);
        }
    }
}