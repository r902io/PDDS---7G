package pe.edu.pucp.sisrap.experimentacion.dominio;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

/**
 * Bloqueo planificado de una poligonal abierta de la retícula vial.
 */
public record Bloqueo(LocalDateTime inicio, LocalDateTime fin, List<Nodo> vertices) {
    public Bloqueo {
        if (inicio == null || fin == null || !fin.isAfter(inicio)
                || vertices == null || vertices.size() < 2) {
            throw new IllegalArgumentException("Bloqueo inválido");
        }

        vertices = List.copyOf(vertices);
        for (int i = 0; i < vertices.size() - 1; i++) {
            Nodo a = vertices.get(i);
            Nodo b = vertices.get(i + 1);
            if (a.getX() != b.getX() && a.getY() != b.getY()) {
                throw new IllegalArgumentException(
                        "Los tramos del bloqueo deben seguir calles horizontales o verticales: ("
                                + a.getX() + "," + a.getY() + ") -> ("
                                + b.getX() + "," + b.getY() + ")");
            }
            if (a.getX() == b.getX() && a.getY() == b.getY()) {
                throw new IllegalArgumentException("El bloqueo contiene un tramo de longitud cero");
            }
        }
    }

    public boolean activoEn(LocalDateTime instante) {
        return instante != null
                && !instante.isBefore(inicio)
                && instante.isBefore(fin);
    }

    public boolean intersecta(LocalDateTime desde, LocalDateTime hasta) {
        if (desde == null || hasta == null || !hasta.isAfter(desde)) return false;
        return inicio.isBefore(hasta) && fin.isAfter(desde);
    }

    public long duracionMinutos() {
        return Duration.between(inicio, fin).toMinutes();
    }
}