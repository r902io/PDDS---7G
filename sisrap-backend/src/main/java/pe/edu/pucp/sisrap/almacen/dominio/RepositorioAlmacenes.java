package pe.edu.pucp.sisrap.almacen.dominio;

import java.util.List;
import java.util.Optional;

public interface RepositorioAlmacenes {

    List<AlmacenOperativo> listar();

    Optional<AlmacenOperativo> buscar(String idAlmacen);

    AlmacenOperativo crear(
            String idAlmacen,
            String nombre,
            TipoAlmacen tipo,
            int x,
            int y,
            Integer capacidad
    );

    AlmacenOperativo actualizar(
            String idAlmacen,
            String nombre,
            int x,
            int y,
            Integer capacidad
    );

    void eliminar(String idAlmacen);
}