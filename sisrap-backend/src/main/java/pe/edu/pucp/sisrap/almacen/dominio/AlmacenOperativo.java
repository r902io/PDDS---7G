package pe.edu.pucp.sisrap.almacen.dominio;

import java.time.LocalTime;

public record AlmacenOperativo(
        String idAlmacen,
        String nombre,
        TipoAlmacen tipo,
        int ubicacionX,
        int ubicacionY,
        Integer capacidadMaxima,
        Integer stockActual,
        LocalTime horaRecarga
) {
}