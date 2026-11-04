package pe.edu.pucp.sisrap.simulacion.aplicacion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.JornadaOperativa;
import pe.edu.pucp.sisrap.simulacion.dominio.PuntoSimulacion;

/**
 * Horas, distancias y trazados de una ruta, sin estado ni acceso a datos.
 *
 * <p>Las paradas que siguen a la actual aún no tienen camino calculado: el
 * motor lo busca al llegar a cada tramo. Por eso se estiman por distancia
 * Manhattan, sin considerar bloqueos, y se informan aparte de la ruta real.</p>
 */
final class EstimadorRuta {

    private EstimadorRuta() { }

    /** Parada prevista de un pedido o fracción. */
    record Parada(long idPlanificado, Nodo ubicacion, String idCliente) {

        /** El motor descarga en una sola parada las fracciones seguidas de un cliente. */
        boolean mismaParadaQue(Parada otra) {
            return otra != null
                    && ubicacion.getX() == otra.ubicacion.getX()
                    && ubicacion.getY() == otra.ubicacion.getY()
                    && idCliente.equals(otra.idCliente);
        }
    }

    record ParadaEstimada(long idPlanificado, LocalDateTime llegada, List<PuntoSimulacion> ruta) { }

    static double redondearKm(double km) {
        return Math.round(Math.max(0, km) * 100.0) / 100.0;
    }

    static double kmManhattan(Nodo a, Nodo b, double kmPorNodo) {
        return (Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY())) * kmPorNodo;
    }

    static PuntoSimulacion punto(Nodo nodo) {
        return new PuntoSimulacion(nodo.getX(), nodo.getY());
    }

    /**
     * Llegada al destino inmediato. Con el tramo calculado se parte de la salida
     * de su último nodo, que ya incluye el refrigerio y las esperas por bloqueo.
     * Si no, se avanza desde el reloj con la velocidad de la unidad.
     *
     * @param ultimaSalida salida del último nodo del tramo, o null si no hay tramo
     * @param horasEspera tiempo detenido antes de continuar, como un trasvase
     */
    static LocalDateTime llegadaAlDestino(
            LocalDateTime reloj,
            LocalDateTime ultimaSalida,
            double horasPorNodo,
            double horasEspera,
            double kmPendientes,
            double velocidadKmh) {
        if (ultimaSalida != null) {
            LocalDateTime llegada = JornadaOperativa.avanzar(ultimaSalida, horasPorNodo);
            if (!llegada.isBefore(reloj)) return llegada;
        }
        return JornadaOperativa.avanzar(reloj, horasEspera + kmPendientes / velocidadKmh);
    }

    /**
     * Agrega un nodo guardando solo las esquinas. Sigue en línea recta si
     * mantiene la dirección; una vuelta en U sí queda como esquina.
     */
    static void agregarNodo(List<PuntoSimulacion> ruta, PuntoSimulacion nodo) {
        int n = ruta.size();
        if (n > 0 && ruta.get(n - 1).equals(nodo)) return;
        if (n >= 2) {
            PuntoSimulacion a = ruta.get(n - 2), b = ruta.get(n - 1);
            if (Integer.signum(b.x() - a.x()) == Integer.signum(nodo.x() - b.x())
                    && Integer.signum(b.y() - a.y()) == Integer.signum(nodo.y() - b.y())) {
                ruta.set(n - 1, nodo);
                return;
            }
        }
        ruta.add(nodo);
    }

    /** Une dos puntos con una esquina si hace falta: un trazado nunca es diagonal. */
    static void agregarOrtogonal(List<PuntoSimulacion> ruta, PuntoSimulacion destino) {
        if (!ruta.isEmpty()) {
            PuntoSimulacion ultimo = ruta.get(ruta.size() - 1);
            if (ultimo.x() != destino.x() && ultimo.y() != destino.y()) {
                agregarNodo(ruta, new PuntoSimulacion(destino.x(), ultimo.y()));
            }
        }
        agregarNodo(ruta, destino);
    }

    /**
     * Estima la llegada a cada parada siguiente, en el orden del plan.
     *
     * @param instanteBase llegada al punto base, o el reloj si la unidad ya está allí
     * @param paradaBase parada que se atiende en el punto base; null si no es un cliente
     * @param horasEnBase tiempo detenido en el punto base: descarga o trasvase
     * @param rutaBase tramo estimado hasta el punto base; vacío si el tramo es conocido
     */
    static List<ParadaEstimada> estimarParadas(
            LocalDateTime instanteBase,
            Nodo puntoBase,
            Parada paradaBase,
            double horasEnBase,
            List<PuntoSimulacion> rutaBase,
            List<Parada> siguientes,
            double horasServicio,
            double kmPorNodo,
            double velocidadKmh) {
        List<ParadaEstimada> estimadas = new ArrayList<>(siguientes.size());
        List<PuntoSimulacion> ruta = new ArrayList<>(rutaBase);
        LocalDateTime llegada = instanteBase;
        Nodo anterior = puntoBase;
        Parada previa = paradaBase;
        double espera = horasEnBase;

        for (Parada parada : siguientes) {
            if (!parada.mismaParadaQue(previa)) {
                double km = kmManhattan(anterior, parada.ubicacion(), kmPorNodo);
                llegada = JornadaOperativa.avanzar(llegada, espera + km / velocidadKmh);
                if (km > 0) {
                    if (ruta.isEmpty()) ruta.add(punto(anterior));
                    agregarOrtogonal(ruta, punto(parada.ubicacion()));
                }
                anterior = parada.ubicacion();
                espera = horasServicio;
            }
            estimadas.add(new ParadaEstimada(parada.idPlanificado(), llegada, List.copyOf(ruta)));
            previa = parada;
        }
        return estimadas;
    }
}
