package pe.edu.pucp.sisrap.planificador.dominio.modelo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

public final class BloqueosReticula {

    private BloqueosReticula() {
    }

    public static Set<String> cerrarTramos(List<Nodo> vertices) {
        if (vertices == null || vertices.size() < 2) {
            throw new IllegalArgumentException("Un bloqueo requiere al menos dos vértices");
        }

        Set<String> cerrados = new HashSet<>();
        for (int i = 0; i < vertices.size() - 1; i++) {
            Nodo origen = vertices.get(i);
            Nodo destino = vertices.get(i + 1);
            int dx = Integer.compare(destino.getX(), origen.getX());
            int dy = Integer.compare(destino.getY(), origen.getY());

            if ((dx != 0 && dy != 0) || (dx == 0 && dy == 0)) {
                throw new IllegalArgumentException("Los bloqueos deben seguir calles horizontales o verticales");
            }

            int x = origen.getX();
            int y = origen.getY();
            cerrados.add(x + "," + y);

            while (x != destino.getX() || y != destino.getY()) {
                int siguienteX = x + dx;
                int siguienteY = y + dy;
                cerrados.add(ArcoReticula.clave(x, y, siguienteX, siguienteY));
                cerrados.add(siguienteX + "," + siguienteY);
                x = siguienteX;
                y = siguienteY;
            }
        }

        return Set.copyOf(cerrados);
    }
}
