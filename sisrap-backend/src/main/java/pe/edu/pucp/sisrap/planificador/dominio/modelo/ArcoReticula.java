package pe.edu.pucp.sisrap.planificador.dominio.modelo;

import java.util.Set;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

public final class ArcoReticula {
    private ArcoReticula() { }

    public static String clave(int x1, int y1, int x2, int y2) {
        boolean primero = x1 < x2 || (x1 == x2 && y1 <= y2);
        return primero ? x1 + "," + y1 + "|" + x2 + "," + y2
                : x2 + "," + y2 + "|" + x1 + "," + y1;
    }

    public static boolean bloqueado(int x1, int y1, int x2, int y2, Set<String> bloqueos) {
        return bloqueos != null && (bloqueos.contains(clave(x1, y1, x2, y2))
                || bloqueos.contains(x2 + "," + y2)
                || bloqueos.contains(x1 + "," + y1));
    }

    /**
     * Un vértice de una poligonal de bloqueo puede ser el punto final de
     * una entrega o el punto desde el que se realiza el regreso, pero no un
     * nodo de tránsito. Ningún arco explícitamente cerrado se cruza.
     */
    public static boolean transitableComoExtremo(
            int x1, int y1, int x2, int y2, Set<String> bloqueos,
            boolean origenDelTramo, boolean destinoDelTramo) {
        if (bloqueos == null || bloqueos.isEmpty()) return true;
        if (bloqueos.contains(clave(x1, y1, x2, y2))) return false;
        if (bloqueos.contains(x1 + "," + y1) && !origenDelTramo) return false;
        if (bloqueos.contains(x2 + "," + y2) && !destinoDelTramo) return false;
        return true;
    }

    public static boolean caminoInterrumpido(Nodo origen, Iterable<Nodo> camino, Set<String> bloqueos) {
        Nodo previo = origen;
        for (Nodo siguiente : camino) {
            if (bloqueado(previo.getX(), previo.getY(), siguiente.getX(), siguiente.getY(), bloqueos)) {
                return true;
            }
            previo = siguiente;
        }
        return false;
    }
}
