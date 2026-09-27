package pe.edu.pucp.sisrap.planificador.dominio.modelo;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Set;

import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

/**
 * Calcula la distancia mínima sobre la retícula de PaqRap evitando nodos
 * bloqueados.
 *
 * No existen diagonales. Cada movimiento conecta dos nodos adyacentes.
 */
public final class DistanciaReticula {

    private DistanciaReticula() {
    }

    private record Estado(int x, int y, int distancia, int estimado) {
    }

    public static int distancia(
            Nodo origen,
            Nodo destino,
            Set<String> bloqueados,
            int ancho,
            int alto) {

        if (origen == null || destino == null) {
            throw new IllegalArgumentException("Origen/destino no pueden ser nulos");
        }

        if (!dentro(origen.getX(), origen.getY(), ancho, alto)
                || !dentro(destino.getX(), destino.getY(), ancho, alto)) {
            throw new IllegalArgumentException("Nodo fuera de la ciudad");
        }

        Set<String> bloqueos = bloqueados == null
                ? Set.of()
                : bloqueados;

        if (estaBloqueado(origen.getX(), origen.getY(), bloqueos)
                || estaBloqueado(destino.getX(), destino.getY(), bloqueos)) {
            return -1;
        }

        if (origen.getX() == destino.getX()
                && origen.getY() == destino.getY()) {
            return 0;
        }

        int[][] mejor = new int[ancho + 1][alto + 1];

        for (int x = 0; x <= ancho; x++) {
            Arrays.fill(mejor[x], Integer.MAX_VALUE);
        }

        PriorityQueue<Estado> abiertos =
                new PriorityQueue<>(Comparator.comparingInt(Estado::estimado));

        mejor[origen.getX()][origen.getY()] = 0;

        abiertos.add(new Estado(
                origen.getX(),
                origen.getY(),
                0,
                heuristica(
                        origen.getX(),
                        origen.getY(),
                        destino.getX(),
                        destino.getY())));

        int[][] movimientos = {
                { 1, 0 },
                { -1, 0 },
                { 0, 1 },
                { 0, -1 }
        };

        while (!abiertos.isEmpty()) {
            Estado actual = abiertos.poll();

            if (actual.distancia() != mejor[actual.x()][actual.y()]) {
                continue;
            }

            if (actual.x() == destino.getX()
                    && actual.y() == destino.getY()) {
                return actual.distancia();
            }

            for (int[] movimiento : movimientos) {
                int nx = actual.x() + movimiento[0];
                int ny = actual.y() + movimiento[1];

                if (!dentro(nx, ny, ancho, alto)) {
                    continue;
                }

                if (estaBloqueado(nx, ny, bloqueos)) {
                    continue;
                }

                int nuevaDistancia = actual.distancia() + 1;

                if (nuevaDistancia >= mejor[nx][ny]) {
                    continue;
                }

                mejor[nx][ny] = nuevaDistancia;

                int estimado = nuevaDistancia
                        + heuristica(
                                nx,
                                ny,
                                destino.getX(),
                                destino.getY());

                abiertos.add(new Estado(
                        nx,
                        ny,
                        nuevaDistancia,
                        estimado));
            }
        }

        return -1;
    }

    private static boolean dentro(
            int x,
            int y,
            int ancho,
            int alto) {

        return x >= 0
                && x <= ancho
                && y >= 0
                && y <= alto;
    }

    private static boolean estaBloqueado(
            int x,
            int y,
            Set<String> bloqueados) {

        return bloqueados.contains(x + "," + y);
    }

    private static int heuristica(
            int x1,
            int y1,
            int x2,
            int y2) {

        return Math.abs(x1 - x2)
                + Math.abs(y1 - y2);
    }
}