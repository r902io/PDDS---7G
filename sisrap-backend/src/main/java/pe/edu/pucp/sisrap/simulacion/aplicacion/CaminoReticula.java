package pe.edu.pucp.sisrap.simulacion.aplicacion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

/** BFS sobre la retícula. Devuelve el camino sin incluir el nodo de origen. */
public final class CaminoReticula {

    private static final int[][] DIRECCIONES = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private CaminoReticula() {
    }

    public static Optional<List<Nodo>> calcular(
            Nodo origen,
            Nodo destino,
            Set<String> bloqueados,
            int ancho,
            int alto) {

        if (origen == null || destino == null) {
            throw new IllegalArgumentException("Origen/destino requeridos");
        }
        if (ancho <= 0 || alto <= 0) {
            throw new IllegalArgumentException("Dimensiones inválidas");
        }
        if (fuera(origen, ancho, alto) || fuera(destino, ancho, alto)) {
            return Optional.empty();
        }
        if (origen.getX() == destino.getX() && origen.getY() == destino.getY()) {
            return Optional.of(List.of());
        }

        Set<String> cerrados = bloqueados == null ? Set.of() : bloqueados;
        String claveDestino = clave(destino.getX(), destino.getY());
        if (cerrados.contains(claveDestino)) {
            return Optional.empty();
        }

        int columnas = ancho + 1;
        int filas = alto + 1;
        int total = columnas * filas;
        int[] anterior = new int[total];
        Arrays.fill(anterior, -1);
        boolean[] visitado = new boolean[total];

        int inicio = indice(origen.getX(), origen.getY(), columnas);
        int fin = indice(destino.getX(), destino.getY(), columnas);

        ArrayDeque<Integer> cola = new ArrayDeque<>();
        cola.add(inicio);
        visitado[inicio] = true;

        while (!cola.isEmpty()) {
            int actual = cola.removeFirst();
            int x = actual % columnas;
            int y = actual / columnas;

            for (int[] d : DIRECCIONES) {
                int nx = x + d[0];
                int ny = y + d[1];

                if (nx < 0 || nx > ancho || ny < 0 || ny > alto) {
                    continue;
                }

                int ni = indice(nx, ny, columnas);
                if (visitado[ni]) {
                    continue;
                }

                // El nodo actual puede haber quedado bloqueado mientras el vehículo
                // estaba sobre él; se le permite salir, pero no entrar a otro bloqueado.
                if (cerrados.contains(clave(nx, ny))) {
                    continue;
                }

                visitado[ni] = true;
                anterior[ni] = actual;

                if (ni == fin) {
                    return Optional.of(reconstruir(anterior, inicio, fin, columnas));
                }

                cola.addLast(ni);
            }
        }

        return Optional.empty();
    }

    private static List<Nodo> reconstruir(
            int[] anterior,
            int inicio,
            int fin,
            int columnas) {

        List<Nodo> invertido = new ArrayList<>();
        int actual = fin;

        while (actual != inicio) {
            int x = actual % columnas;
            int y = actual / columnas;
            invertido.add(new Nodo(x, y));
            actual = anterior[actual];
            if (actual < 0) {
                return List.of();
            }
        }

        Collections.reverse(invertido);
        return List.copyOf(invertido);
    }

    private static int indice(int x, int y, int columnas) {
        return y * columnas + x;
    }

    private static boolean fuera(Nodo n, int ancho, int alto) {
        return n.getX() < 0 || n.getX() > ancho || n.getY() < 0 || n.getY() > alto;
    }

    public static String clave(int x, int y) {
        return x + "," + y;
    }
}
