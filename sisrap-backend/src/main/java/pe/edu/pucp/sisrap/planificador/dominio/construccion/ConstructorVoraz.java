package pe.edu.pucp.sisrap.planificador.dominio.construccion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Ruta;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;

/** Construcción inicial y reparación de soluciones. */
public final class ConstructorVoraz {

    private ConstructorVoraz() {}

    public static Solucion construir(
            ContextoPlanificacion c,
            Random random,
            boolean voraz) {

        Solucion s = new Solucion();
        s.setRutas(crearRutasVacias(c));

        insertarPedidos(
                s.getRutas(),
                ordenarPedidos(c.getPedidos(), random, voraz),
                s.getPedidosNoAsignados());

        s.getRutas().forEach(Ruta::recalcular);
        return s;
    }

    /**
     * Se mantiene una ruta por vehículo, pero el almacén de origen ya no queda
     * forzado al central. Cada ruta conoce los tres almacenes como candidatos.
     * La selección real del origen se hace al insertar pedidos, respetando el
     * stock de los almacenes intermedios.
     */
    public static List<Ruta> crearRutasVacias(ContextoPlanificacion c) {
        Almacen central = c.getAlmacenes().stream()
                .filter(a -> a.getCapacidadMaxima() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Se requiere almacén central"));

        List<Ruta> rutas = new ArrayList<>();
        for (var vehiculo : c.getVehiculos()) {
            Ruta ruta = new Ruta(
                    vehiculo,
                    central,
                    c.instante(),
                    c.reglas());

            ruta.setNodosBloqueados(c.nodosBloqueados());
            ruta.setAlmacenesCandidatos(c.getAlmacenes());
            rutas.add(ruta);
        }
        return rutas;
    }

    public static List<Pedido> ordenarPedidos(
            List<Pedido> pedidos,
            Random random,
            boolean voraz) {

        var copia = new ArrayList<>(pedidos);
        Collections.shuffle(copia, random);

        if (voraz) {
            copia.sort(Comparator.comparing(Pedido::getFechaLimite));
        }
        return copia;
    }

    /**
     * Inserta cada pedido probando no solo el vehículo/ruta, sino también el
     * almacén de origen. De esta forma los almacenes intermedios sí participan
     * en la solución y el central deja de ser el único origen posible.
     */
    public static void insertarPedidos(
            List<Ruta> rutas,
            List<Pedido> pedidos,
            List<Pedido> noAsignados) {

        for (Pedido pedido : pedidos) {
            Ruta mejorRuta = null;
            Almacen mejorAlmacen = null;
            double mejorTiempo = Double.POSITIVE_INFINITY;

            for (Ruta ruta : rutas) {
                if (!ruta.getVehiculo().isDisponible()) {
                    continue;
                }

                int nuevaCarga = ruta.cargaTotal() + pedido.getCantidadQq();
                if (nuevaCarga > ruta.getVehiculo().getCapacidadPaquetes()) {
                    continue;
                }

                List<Almacen> candidatos = ruta.getAlmacenesCandidatos().isEmpty()
                        ? List.of(ruta.getAlmacenOrigen())
                        : ruta.getAlmacenesCandidatos();

                for (Almacen almacen : candidatos) {
                    if (!puedeConsumirDesde(rutas, ruta, almacen, nuevaCarga)) {
                        continue;
                    }

                    double tiempo = ruta.horaLlegadaSiAgrega(
                            pedido,
                            almacen);

                    if (!Double.isFinite(tiempo)) {
                        continue;
                    }

                    if (tiempo < mejorTiempo) {
                        mejorTiempo = tiempo;
                        mejorRuta = ruta;
                        mejorAlmacen = almacen;
                    }
                }
            }

            if (mejorRuta == null) {
                noAsignados.add(pedido);
            } else {
                mejorRuta.setAlmacenOrigen(mejorAlmacen);
                mejorRuta.getSecuenciaPedidos().add(pedido);
            }
        }
    }

    /**
     * Comprueba el stock de un almacén considerando la carga que ya consumen
     * las demás rutas que salen de ese mismo origen.
     */
    private static boolean puedeConsumirDesde(
            List<Ruta> rutas,
            Ruta rutaEvaluada,
            Almacen almacen,
            int nuevaCargaRuta) {

        if (almacen.getCapacidadMaxima() == null) {
            return true; // almacén central: stock ilimitado
        }

        Integer stock = almacen.getStockActual();
        if (stock == null || stock < 0) {
            return false;
        }

        int consumoOtrasRutas = rutas.stream()
                .filter(r -> r != rutaEvaluada)
                .filter(r -> r.getAlmacenOrigen().getIdAlmacen()
                        .equals(almacen.getIdAlmacen()))
                .mapToInt(Ruta::cargaTotal)
                .sum();

        return consumoOtrasRutas + nuevaCargaRuta <= stock;
    }

    /** Reutiliza el orden anterior, pero reconstruye con vehículos/pedidos actuales. */
    public static Solucion reparar(
            Solucion anterior,
            ContextoPlanificacion c,
            Random random) {

        Map<Long, Pedido> porId = new HashMap<>();
        c.getPedidos().forEach(p -> porId.put(p.getIdPedido(), p));

        List<Pedido> orden = new ArrayList<>();
        for (Ruta ruta : anterior.getRutas()) {
            for (Pedido pedido : ruta.getSecuenciaPedidos()) {
                Pedido vigente = porId.remove(pedido.getIdPedido());
                if (vigente != null) {
                    orden.add(vigente);
                }
            }
        }

        orden.addAll(ordenarPedidos(
                new ArrayList<>(porId.values()),
                random,
                true));

        Solucion s = new Solucion();
        s.setRutas(crearRutasVacias(c));
        insertarPedidos(s.getRutas(), orden, s.getPedidosNoAsignados());
        s.getRutas().forEach(Ruta::recalcular);
        return s;
    }
}