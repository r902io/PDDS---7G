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
            return 0;
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

                if (!dentro(nx, ny, ancho, alto)) {
                    continue;
                }
                boolean llegadaDestino = nx == destino.getX() && ny == destino.getY();
                boolean salidaOrigen = actual.x() == origen.getX()
                        && actual.y() == origen.getY();
                if ((bloqueados[nx][ny] && !llegadaDestino)
                        || !ArcoReticula.transitableComoExtremo(
                                actual.x(), actual.y(), nx, ny, arcosCerrados,
                                salidaOrigen, llegadaDestino)) {
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
        TEMPORALES.get().clear();
    }

    /** Libera completamente la caché asociada al hilo actual. */
    public static void liberarCache() {
        CACHE.remove();
        TEMPORALES.remove();
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

    public record Trayecto(java.time.LocalDateTime llegada, java.util.List<Nodo> nodos,
                           java.util.List<java.time.LocalDateTime> salidas) { }

    private record ClaveTemporal(int ox, int oy, int dx, int dy, int px, int py,
            java.time.LocalDateTime salida, double velocidad, double distancia, int ancho, int alto,
            Set<String> fijos, java.util.List<pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo> calendario) { }
    private static final ThreadLocal<Map<ClaveTemporal, java.util.Optional<Trayecto>>> TEMPORALES =
            ThreadLocal.withInitial(HashMap::new);

    /** Camino de llegada más temprana. Esperar una reapertura es una alternativa física. */
    public static java.util.Optional<Trayecto> trayectoTemporal(Nodo origen, Nodo destino,
            Nodo anterior, java.time.LocalDateTime salida, double velocidad, double distanciaNodo,
            Set<String> bloqueosActuales,
            java.util.List<pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo> calendario,
            int ancho, int alto) {
        if (velocidad <= 0 || distanciaNodo <= 0 || salida == null)
            throw new IllegalArgumentException("Parámetros temporales inválidos");
        var conocidos = calendario == null ? java.util.List.<pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo>of()
                : calendario;
        Set<String> fijos = new java.util.HashSet<>(bloqueosActuales == null ? Set.of() : bloqueosActuales);
        for (var b : conocidos) fijos.removeAll(BloqueosReticula.cerrarTramos(b.vertices()));
        var clave = new ClaveTemporal(origen.getX(), origen.getY(), destino.getX(), destino.getY(),
                anterior == null ? -1 : anterior.getX(), anterior == null ? -1 : anterior.getY(),
                salida, velocidad, distanciaNodo, ancho, alto, Set.copyOf(fijos), conocidos);
        var cache = TEMPORALES.get();
        var existente = cache.get(clave);
        if (existente != null) return existente;
        var resultado = buscarTemporal(origen, destino, anterior, salida, velocidad, distanciaNodo,
                fijos, conocidos, ancho, alto);
        if (cache.size() >= 12000) cache.clear();
        cache.put(clave, resultado);
        return resultado;
    }

    private record Etiqueta(int x, int y, int px, int py, java.time.LocalDateTime llegada,
                             java.time.LocalDateTime salidaArco, Etiqueta previa,
                             java.time.LocalDateTime estimada) { }
    private record EstadoTemporal(int x, int y, int px, int py) { }

    private static java.util.Optional<Trayecto> buscarTemporal(Nodo origen, Nodo destino,
            Nodo anterior, java.time.LocalDateTime inicio, double velocidad, double distanciaNodo,
            Set<String> fijos, java.util.List<pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo> calendario,
            int ancho, int alto) {
        if (!dentro(origen.getX(), origen.getY(), ancho, alto)
                || !dentro(destino.getX(), destino.getY(), ancho, alto)) return java.util.Optional.empty();
        if (fijos.isEmpty() && calendario.isEmpty()) {
            var nodos = new java.util.ArrayList<Nodo>();
            var salidas = new java.util.ArrayList<java.time.LocalDateTime>();
            int x=origen.getX(), y=origen.getY();
            var tiempo=inicio;
            while (x!=destino.getX() || y!=destino.getY()) {
                tiempo=JornadaOperativa.avanzar(tiempo,0);
                salidas.add(tiempo);
                if (x!=destino.getX()) x+=Integer.signum(destino.getX()-x);
                else y+=Integer.signum(destino.getY()-y);
                nodos.add(new Nodo(x,y));
                tiempo=JornadaOperativa.avanzar(tiempo,distanciaNodo/velocidad);
            }
            return java.util.Optional.of(new Trayecto(tiempo,java.util.List.copyOf(nodos),
                    java.util.List.copyOf(salidas)));
        }
        var cierres = calendario.stream().map(b -> new CierreTemporal(b.inicio(), b.fin(),
                BloqueosReticula.cerrarTramos(b.vertices()))).toList();
        double horasArco = distanciaNodo / velocidad;
        var cola = new PriorityQueue<Etiqueta>(Comparator.comparing(Etiqueta::estimada)
                .thenComparing(Etiqueta::llegada, Comparator.reverseOrder()).thenComparingInt(Etiqueta::x).thenComparingInt(Etiqueta::y));
        Map<EstadoTemporal, java.time.LocalDateTime> mejores = new HashMap<>();
        var primero = new Etiqueta(origen.getX(), origen.getY(), anterior == null ? -1 : anterior.getX(),
                anterior == null ? -1 : anterior.getY(), inicio, inicio, null, inicio);
        cola.add(primero);
        mejores.put(new EstadoTemporal(primero.x(),primero.y(),primero.px(),primero.py()), inicio);
        while (!cola.isEmpty()) {
            var e = cola.poll();
            var estado = new EstadoTemporal(e.x(),e.y(),e.px(),e.py());
            if (!e.llegada().equals(mejores.get(estado))) continue;
            if (e.x() == destino.getX() && e.y() == destino.getY()) {
                var nodos = new java.util.ArrayList<Nodo>();
                var salidas = new java.util.ArrayList<java.time.LocalDateTime>();
                for (var n = e; n.previa() != null; n = n.previa()) {
                    nodos.add(new Nodo(n.x(),n.y())); salidas.add(n.salidaArco());
                }
                java.util.Collections.reverse(nodos); java.util.Collections.reverse(salidas);
                return java.util.Optional.of(new Trayecto(e.llegada(), java.util.List.copyOf(nodos),
                        java.util.List.copyOf(salidas)));
            }
            for (int[] d : MOVIMIENTOS) {
                int nx=e.x()+d[0], ny=e.y()+d[1];
                if (!dentro(nx,ny,ancho,alto)) continue;
                boolean finalTramo = nx==destino.getX() && ny==destino.getY();
                java.time.LocalDateTime partida = e.llegada();
                java.time.LocalDateTime llegada = null;
                for (int intento=0; intento<=cierres.size()+1; intento++) {
                    partida = JornadaOperativa.avanzar(partida, 0);
                    llegada = JornadaOperativa.avanzar(partida, horasArco);
                    if (!pasoPermitido(e.x(),e.y(),nx,ny,e.px(),e.py(),finalTramo,fijos)) {
                        llegada=null; break;
                    }
                    java.time.LocalDateTime esperarHasta=null;
                    for (var cierre : cierres) {
                        if (cierre.inicio().isBefore(llegada) && cierre.fin().isAfter(partida)
                                && !pasoPermitido(e.x(),e.y(),nx,ny,e.px(),e.py(),finalTramo,cierre.nodos())) {
                            if (esperarHasta==null || cierre.fin().isAfter(esperarHasta)) esperarHasta=cierre.fin();
                        }
                    }
                    if (esperarHasta==null) break;
                    partida=esperarHasta; llegada=null;
                }
                if (llegada==null) continue;
                var siguiente=new EstadoTemporal(nx,ny,e.x(),e.y());
                var conocida=mejores.get(siguiente);
                if (conocida!=null && !llegada.isBefore(conocida)) continue;
                mejores.put(siguiente,llegada);
                long heuristicaNs=Math.round(heuristica(nx,ny,destino.getX(),destino.getY())
                        *horasArco*3_600_000_000_000.0);
                cola.add(new Etiqueta(nx,ny,e.x(),e.y(),llegada,partida,e,llegada.plusNanos(heuristicaNs)));
            }
        }
        return java.util.Optional.empty();
    }

    private record CierreTemporal(java.time.LocalDateTime inicio, java.time.LocalDateTime fin,
                                   Set<String> nodos) { }

    private static boolean pasoPermitido(int x,int y,int nx,int ny,int px,int py,
                                         boolean destino,Set<String> cierres) {
        if (!ArcoReticula.transitableComoExtremo(x,y,nx,ny,cierres,true,destino)) return false;
        return !cierres.contains(x+","+y) || px<0 || (nx==px && ny==py);
    }
}
