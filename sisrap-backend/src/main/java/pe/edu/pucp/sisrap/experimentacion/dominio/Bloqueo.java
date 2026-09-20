package pe.edu.pucp.sisrap.experimentacion.dominio;

import java.time.LocalDateTime;
import java.util.List;

import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

public record Bloqueo(LocalDateTime inicio, LocalDateTime fin, List<Nodo> vertices) {
    public Bloqueo {
        if (inicio == null || fin == null || fin.isBefore(inicio) || vertices == null || vertices.size() < 2)
            throw new IllegalArgumentException("Bloqueo inválido");
        vertices = List.copyOf(vertices);
    }

    public boolean activoEn(LocalDateTime instante) {
        return !instante.isBefore(inicio) && instante.isBefore(fin);
    }
}