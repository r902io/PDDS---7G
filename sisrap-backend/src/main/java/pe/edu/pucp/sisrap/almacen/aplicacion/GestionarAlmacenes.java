package pe.edu.pucp.sisrap.almacen.aplicacion;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import pe.edu.pucp.sisrap.almacen.dominio.AlmacenOperativo;
import pe.edu.pucp.sisrap.almacen.dominio.RepositorioAlmacenes;
import pe.edu.pucp.sisrap.almacen.dominio.TipoAlmacen;
import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion;

@Service
public final class GestionarAlmacenes {

    private final RepositorioAlmacenes repositorio;
    private final GestionarControlSimulacion control;

    public GestionarAlmacenes(
            RepositorioAlmacenes repositorio,
            GestionarControlSimulacion control
    ) {
        this.repositorio = repositorio;
        this.control = control;
    }

    public List<AlmacenOperativo> listar() {
        return repositorio.listar();
    }

    public AlmacenOperativo buscar(
            String idAlmacen
    ) {

        return repositorio.buscar(idAlmacen)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "No existe el almacén "
                                        + idAlmacen
                        )
                );
    }

    public AlmacenOperativo crear(
            String token,
            String idAlmacen,
            String nombre,
            TipoAlmacen tipo,
            int x,
            int y,
            Integer capacidad
    ) {

        control.verificarControlador(token);

        validarId(idAlmacen);
        validarNombre(nombre);

        if (tipo == null) {
            throw new IllegalArgumentException(
                    "El tipo de almacén es obligatorio"
            );
        }

        if (tipo == TipoAlmacen.CENTRAL) {

            if (capacidad != null) {
                throw new IllegalArgumentException(
                        "El almacén central tiene "
                                + "capacidad ilimitada"
                );
            }

        } else {

            if (capacidad == null
                    || capacidad <= 0) {

                throw new IllegalArgumentException(
                        "La capacidad debe ser mayor que cero"
                );
            }
        }

        return repositorio.crear(
                idAlmacen,
                nombre.trim(),
                tipo,
                x,
                y,
                capacidad
        );
    }

    public AlmacenOperativo actualizar(
            String token,
            String idAlmacen,
            String nombre,
            int x,
            int y,
            Integer capacidad
    ) {

        control.verificarControlador(token);

        validarNombre(nombre);

        return repositorio.actualizar(
                idAlmacen,
                nombre.trim(),
                x,
                y,
                capacidad
        );
    }

    public void eliminar(
            String token,
            String idAlmacen
    ) {

        control.verificarControlador(token);

        repositorio.eliminar(idAlmacen);
    }

    private void validarId(String id) {

        if (id == null
                || id.isBlank()
                || id.length() > 20) {

            throw new IllegalArgumentException(
                    "Identificador de almacén inválido"
            );
        }
    }

    private void validarNombre(String nombre) {

        if (nombre == null
                || nombre.isBlank()
                || nombre.length() > 50) {

            throw new IllegalArgumentException(
                    "Nombre de almacén inválido"
            );
        }
    }
}