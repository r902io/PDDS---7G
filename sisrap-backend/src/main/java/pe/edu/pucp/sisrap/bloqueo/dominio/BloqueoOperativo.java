package pe.edu.pucp.sisrap.bloqueo.dominio;

import java.time.LocalDateTime;
import java.util.List;

import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

public record BloqueoOperativo(
        long idIncidencia,
        LocalDateTime inicio,
        LocalDateTime fin,
        List<Nodo> vertices,
        boolean habilitado
) {

    public BloqueoOperativo {

        if (inicio == null
                || fin == null
                || !fin.isAfter(inicio)) {

            throw new IllegalArgumentException(
                    "Intervalo de bloqueo inválido"
            );
        }

        if (vertices == null
                || vertices.size() < 2) {

            throw new IllegalArgumentException(
                    "El bloqueo requiere al menos dos puntos"
            );
        }

        vertices = List.copyOf(vertices);

        for (int i = 0;
             i < vertices.size() - 1;
             i++) {

            Nodo a = vertices.get(i);
            Nodo b = vertices.get(i + 1);

            boolean mismoX =
                    a.getX() == b.getX();

            boolean mismoY =
                    a.getY() == b.getY();

            if (!mismoX && !mismoY) {
                throw new IllegalArgumentException(
                        "Los bloqueos deben seguir "
                                + "calles horizontales o verticales"
                );
            }

            if (mismoX && mismoY) {
                throw new IllegalArgumentException(
                        "El bloqueo contiene "
                                + "un tramo de longitud cero"
                );
            }
        }
    }

    public EstadoBloqueo estadoEn(
            LocalDateTime instante
    ) {

        if (!habilitado) {
            return EstadoBloqueo.CANCELADO;
        }

        if (instante.isBefore(inicio)) {
            return EstadoBloqueo.PROGRAMADO;
        }

        if (!instante.isBefore(fin)) {
            return EstadoBloqueo.FINALIZADO;
        }

        return EstadoBloqueo.ACTIVO;
    }
}