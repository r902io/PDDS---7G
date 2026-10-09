package pe.edu.pucp.sisrap.planificador.dominio.modelo;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

/**
 * Calcula la distancia mínima sobre la retícula de PaqRap evitando nodos
 * bloqueados.
 *
 * <p>La caché es local al hilo. Por lo tanto no comparte estado entre corridas
 * ejecutadas en hilos distintos y no necesita sincronización.</p>
 */
public final class DistanciaReticula {

    private static final int MAX_DISTANCIAS_CACHE = 150_000;

    private static final int[][] MOVIMIENTOS = {
            { 1, 0 },
            { -1, 0 },
            { 0, 1 },
            { 0, -1 }
    };

    private static final ThreadLocal<CacheHilo> CACHE =
            ThreadLocal.withInitial(CacheHilo::new);

    private DistanciaReticula() {
    }

    private record Estado(int x, int y, int distancia, int estimado) {
    }

    /**
     * Identifica una topología de bloqueos por referencia, no por contenido.
     * Dentro de un ContextoPlanificacion el Set es inmutable y todas las rutas
     * comparten exactamente la misma referencia.
     */
    private static final class Topologia {
        private final Set<String> bloqueados;
        private final int ancho;
        private final int alto;
        private final int hash;

        Topologia(Set<String> bloqueados, int ancho, int alto) {
            this.bloqueados = bloqueados;
            this.ancho = ancho;
            this.alto = alto;
            this.hash = 31 * (31 * System.identityHashCode(bloqueados) + ancho) + alto;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Topologia otra)) return false;
            return bloqueados == otra.bloqueados
                    && ancho == otra.ancho
                    && alto == otra.alto;
        }
    }

    /**
     * A -> B y B -> A tienen la misma distancia porque la retícula es no dirigida.
     */
    private record ClaveDistancia(
            int x1,
            int y1,
            int x2,
            int y2,
            Topologia topologia) {

        static ClaveDistancia crear(
                Nodo origen,
                Nodo destino,
                Topologia topologia) {

            int ox = origen.getX();
            int oy = origen.getY();
            int dx = destino.getX();
            int dy = destino.getY();

            boolean origenPrimero = ox < dx || (ox == dx && oy <= dy);

            return origenPrimero
                    ? new ClaveDistancia(ox, oy, dx, dy, topologia)
                    : new ClaveDistancia(dx, dy, ox, oy, topologia);
        }
    }

    private static final class CacheHilo {
        private final Map<Topologia, boolean[][]> matricesBloqueo = new HashMap<>();
        private final Map<ClaveDistancia, Integer> distancias = new HashMap<>(16_384);

        void limpiar() {
            matricesBloqueo.clear();
            distancias.clear();
        }
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

        if (origen.getX() == destino.getX()
                && origen.getY() == destino.getY()) {
            return bloqueos.contains(origen.getX() + "," + origen.getY())
                    ? -1
                    : 0;
        }

        /*
         * Sin bloqueos, Manhattan es la distancia exacta de la retícula.
         * Evitamos completamente A*.
         */
        if (bloqueos.isEmpty()) {
            return heuristica(
                    origen.getX(),
                    origen.getY(),
                    destino.getX(),
                    destino.getY());
        }

        CacheHilo cache = CACHE.get();
        Topologia topologia = new Topologia(bloqueos, ancho, alto);

        boolean[][] matrizBloqueo = cache.matricesBloqueo.computeIfAbsent(
                topologia,
                t -> construirMatrizBloqueos(t.bloqueados, t.ancho, t.alto));

        if (matrizBloqueo[origen.getX()][origen.getY()]
                || matrizBloqueo[destino.getX()][destino.getY()]) {
            return -1;
        }

        ClaveDistancia clave = ClaveDistancia.crear(
                origen,
                destino,
                topologia);

        Integer guardada = cache.distancias.get(clave);
        if (guardada != null) {
            return guardada;
        }

        int calculada = calcularAStar(
                origen,
                destino,
                matrizBloqueo,
                bloqueos,
                ancho,
                alto);

        if (cache.distancias.size() >= MAX_DISTANCIAS_CACHE) {
            /*
             * Solo se limpia la tabla de distancias. Las matrices de bloqueo son
             * pequeñas y siguen siendo útiles para el resto de la corrida.
             */
            cache.distancias.clear();
        }

        cache.distancias.put(clave, calculada);
        return calculada;
    }

    private static int calcularAStar(
            Nodo origen,
            Nodo destino,
            boolean[][] bloqueados,
            Set<String> arcosCerrados,
            int ancho,
            int alto) {

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

        while (!abiertos.isEmpty()) {
            Estado actual = abiertos.poll();

            if (actual.distancia() != mejor[actual.x()][actual.y()]) {
                continue;
            }

            if (actual.x() == destino.getX()
                    && actual.y() == destino.getY()) {
                return actual.distancia();
            }

            for (int[] movimiento : MOVIMIENTOS) {
                int nx = actual.x() + movimiento[0];
                int ny = actual.y() + movimiento[1];

                if (!dentro(nx, ny, ancho, alto)
                        || bloqueados[nx][ny]
                        || ArcoReticula.bloqueado(actual.x(), actual.y(), nx, ny, arcosCerrados)) {
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

    private static boolean[][] construirMatrizBloqueos(
            Set<String> bloqueados,
            int ancho,
            int alto) {

        boolean[][] matriz = new boolean[ancho + 1][alto + 1];

        for (String nodo : bloqueados) {
            int coma = nodo.indexOf(',');
            if (coma <= 0 || coma >= nodo.length() - 1) {
                continue;
            }

            if (nodo.indexOf('|') >= 0) continue;
            int x = Integer.parseInt(nodo.substring(0, coma));
            int y = Integer.parseInt(nodo.substring(coma + 1));

            if (dentro(x, y, ancho, alto)) {
                matriz[x][y] = true;
            }
        }

        return matriz;
    }

    /**
     * Debe llamarse antes de cada corrida de un algoritmo para que GA y SA
     * comiencen con una caché vacía y la métrica de tiempo siga siendo justa.
     */
    public static void limpiarCache() {
        CACHE.get().limpiar();
    }

    /** Libera completamente la caché asociada al hilo actual. */
    public static void liberarCache() {
        CACHE.remove();
    }

    public static int entradasCache() {
        return CACHE.get().distancias.size();
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

    private static int heuristica(
            int x1,
            int y1,
            int x2,
            int y2) {

        return Math.abs(x1 - x2)
                + Math.abs(y1 - y2);
    }
}