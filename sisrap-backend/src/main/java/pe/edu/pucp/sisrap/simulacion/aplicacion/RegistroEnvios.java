package pe.edu.pucp.sisrap.simulacion.aplicacion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.sisrap.pedido.dominio.EstadoPedido;
import pe.edu.pucp.sisrap.simulacion.dominio.EnvioSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EnvioSimulacion.Asignacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EnvioSimulacion.EstadoAsignacion;
import pe.edu.pucp.sisrap.simulacion.dominio.PedidoCorrida;
import pe.edu.pucp.sisrap.simulacion.dominio.PuntoSimulacion;

/**
 * Qué unidad lleva cada pedido o fracción en la corrida actual.
 *
 * <p>Vive en memoria y se reinicia con cada corrida: anota lo que hace el
 * motor sin intervenir en la planificación. No es seguro entre hilos; el
 * motor lo usa siempre bajo su propio bloqueo.</p>
 *
 * <p>El caso no define identificadores de producto, así que cada paquete de
 * un pedido recibe un número estable entre 1 y su cantidad: se muestra como
 * idPedido-n y se conserva si el paquete pasa a otra unidad.</p>
 */
final class RegistroEnvios {

    /** Parte de una fracción que se divide durante un rescate. */
    record Parte(long idPlanificado, int cantidad) { }

    private static final class Linea {
        private long idPlanificado;
        private final String idVehiculo;
        private final int cantidad;
        private final List<Integer> productos;
        private EstadoAsignacion estado = EstadoAsignacion.PROGRAMADA;
        private LocalDateTime horaSalida;
        private LocalDateTime horaLlegada;
        private boolean llegadaReal;
        private LocalDateTime horaEntrega;
        private List<PuntoSimulacion> rutaRecorrida = List.of();
        private List<PuntoSimulacion> rutaPendiente = List.of();
        private List<PuntoSimulacion> rutaEstimada = List.of();

        private Linea(long idPlanificado, String idVehiculo, int cantidad, List<Integer> productos) {
            this.idPlanificado = idPlanificado;
            this.idVehiculo = idVehiculo;
            this.cantidad = cantidad;
            this.productos = productos;
        }

        /** Sus paquetes siguen en esta unidad o ya se entregaron. */
        private boolean vigente() {
            return estado != EstadoAsignacion.REASIGNADA && estado != EstadoAsignacion.TRASVASADA;
        }

        private boolean abierta() {
            return vigente() && estado != EstadoAsignacion.ENTREGADA;
        }

        private void limpiarEstimacion() {
            if (!llegadaReal) horaLlegada = null;
            rutaPendiente = List.of();
            rutaEstimada = List.of();
        }

        private Linea partir(Parte parte, List<Integer> productosParte) {
            Linea copia = new Linea(parte.idPlanificado(), idVehiculo, parte.cantidad(), productosParte);
            copia.estado = estado;
            copia.horaSalida = horaSalida;
            copia.horaLlegada = horaLlegada;
            copia.llegadaReal = llegadaReal;
            copia.rutaRecorrida = rutaRecorrida;
            copia.rutaPendiente = rutaPendiente;
            copia.rutaEstimada = rutaEstimada;
            return copia;
        }

        private ViajeUnidad viaje() {
            return new ViajeUnidad(idVehiculo, estado, horaSalida, horaLlegada, horaEntrega);
        }

        private Asignacion aAsignacion(long idPedido, int cantidadViaje, List<Integer> productosViaje) {
            return new Asignacion(idVehiculo, estado, cantidadViaje, identificadores(idPedido, productosViaje),
                    rutaRecorrida, rutaPendiente, rutaEstimada,
                    horaSalida, horaLlegada, llegadaReal, horaEntrega);
        }
    }

    /** Las fracciones que viajan juntas en una unidad se muestran como una sola asignación. */
    private record ViajeUnidad(String idVehiculo, EstadoAsignacion estado, LocalDateTime horaSalida,
                               LocalDateTime horaLlegada, LocalDateTime horaEntrega) { }

    private static final class LineasDePedido {
        private final List<Linea> lineas = new ArrayList<>();
        private final Map<Long, List<Integer>> productosPorId = new HashMap<>();

        private Linea abierta(long idPlanificado, String idVehiculo) {
            for (Linea linea : lineas) {
                if (linea.idPlanificado == idPlanificado && linea.abierta()
                        && linea.idVehiculo.equals(idVehiculo)) return linea;
            }
            return null;
        }

        private List<Integer> productosPara(long idPlanificado, int cantidad) {
            List<Integer> previos = productosPorId.get(idPlanificado);
            if (previos != null && previos.size() == cantidad) return previos;
            List<Integer> nuevos = productosLibres(cantidad);
            productosPorId.put(idPlanificado, nuevos);
            return nuevos;
        }

        private List<Integer> productosLibres(int cantidad) {
            BitSet ocupados = new BitSet();
            for (Linea linea : lineas) {
                if (linea.vigente()) linea.productos.forEach(ocupados::set);
            }
            List<Integer> libres = new ArrayList<>(cantidad);
            for (int n = ocupados.nextClearBit(1); libres.size() < cantidad; n = ocupados.nextClearBit(n + 1)) {
                libres.add(n);
            }
            return List.copyOf(libres);
        }
    }

    private final Map<Long, LineasDePedido> porPedido = new HashMap<>();
    /** Pedido visible de cada identificador planificado, sea pedido o fracción. */
    private final Map<Long, Long> pedidoDe = new HashMap<>();

    void reiniciar() {
        porPedido.clear();
        pedidoDe.clear();
    }

    private LineasDePedido lineasDe(long idPlanificado) {
        Long idPedido = pedidoDe.get(idPlanificado);
        return idPedido == null ? null : porPedido.get(idPedido);
    }

    private Linea abierta(long idPlanificado, String idVehiculo) {
        LineasDePedido envios = lineasDe(idPlanificado);
        return envios == null ? null : envios.abierta(idPlanificado, idVehiculo);
    }

    /** Una ruta del plan compromete estos paquetes con la unidad. */
    void asignar(String idVehiculo, long idPlanificado, long idPedido, int cantidad) {
        pedidoDe.put(idPlanificado, idPedido);
        LineasDePedido envios = porPedido.computeIfAbsent(idPedido, id -> new LineasDePedido());
        if (envios.abierta(idPlanificado, idVehiculo) != null) return;
        envios.lineas.add(new Linea(idPlanificado, idVehiculo, cantidad,
                envios.productosPara(idPlanificado, cantidad)));
    }

    void registrarSalida(String idVehiculo, Collection<Long> ids, LocalDateTime instante) {
        for (long id : ids) {
            Linea linea = abierta(id, idVehiculo);
            if (linea == null || linea.estado != EstadoAsignacion.PROGRAMADA) continue;
            linea.estado = EstadoAsignacion.EN_RUTA;
            linea.horaSalida = instante;
        }
    }

    /** La unidad llegó al cliente: la hora y la ruta dejan de ser estimadas. */
    void registrarLlegada(String idVehiculo, long idPlanificado, LocalDateTime instante,
                          List<PuntoSimulacion> rutaRecorrida) {
        Linea linea = abierta(idPlanificado, idVehiculo);
        if (linea == null) return;
        linea.estado = EstadoAsignacion.EN_ATENCION;
        linea.horaLlegada = instante;
        linea.llegadaReal = true;
        linea.rutaRecorrida = rutaRecorrida;
        linea.rutaPendiente = List.of();
        linea.rutaEstimada = List.of();
    }

    /** Busca por pedido: si la unidad se avería en plena descarga, la entrega igual se completa. */
    void registrarEntrega(long idPlanificado, LocalDateTime instante) {
        LineasDePedido envios = lineasDe(idPlanificado);
        if (envios == null) return;
        for (Linea linea : envios.lineas) {
            if (linea.idPlanificado == idPlanificado && linea.abierta()) {
                linea.estado = EstadoAsignacion.ENTREGADA;
                linea.horaEntrega = instante;
                return;
            }
        }
    }

    /** El plan se canceló y los paquetes vuelven a la cola. */
    void registrarReasignacion(String idVehiculo, Collection<Long> ids,
                               List<PuntoSimulacion> rutaRecorrida) {
        for (long id : ids) {
            Linea linea = abierta(id, idVehiculo);
            if (linea == null) continue;
            linea.estado = EstadoAsignacion.REASIGNADA;
            linea.limpiarEstimacion();
            if (linea.horaSalida != null) linea.rutaRecorrida = rutaRecorrida;
        }
    }

    /** La unidad se detuvo con los paquetes a bordo: su llegada deja de ser conocida. */
    void registrarDetencion(String idVehiculo, Collection<Long> ids,
                            List<PuntoSimulacion> rutaRecorrida) {
        for (long id : ids) {
            Linea linea = abierta(id, idVehiculo);
            if (linea == null || linea.estado == EstadoAsignacion.EN_ATENCION) continue;
            linea.limpiarEstimacion();
            if (linea.horaSalida != null) linea.rutaRecorrida = rutaRecorrida;
        }
    }

    /** Divide una línea abierta conservando los números de sus paquetes. */
    void dividir(long idPlanificado, List<Parte> partes) {
        LineasDePedido envios = lineasDe(idPlanificado);
        if (envios == null) return;
        List<Integer> productos = envios.productosPorId.getOrDefault(idPlanificado, List.of());
        Long idPedido = pedidoDe.get(idPlanificado);
        int desde = 0;
        for (Parte parte : partes) {
            int hasta = Math.min(productos.size(), desde + parte.cantidad());
            envios.productosPorId.put(parte.idPlanificado(),
                    List.copyOf(productos.subList(Math.min(desde, hasta), hasta)));
            pedidoDe.put(parte.idPlanificado(), idPedido);
            desde = hasta;
        }
        for (Linea linea : new ArrayList<>(envios.lineas)) {
            if (linea.idPlanificado != idPlanificado || !linea.abierta()) continue;
            int posicion = envios.lineas.indexOf(linea);
            envios.lineas.remove(posicion);
            for (Parte parte : partes) {
                envios.lineas.add(posicion++,
                        linea.partir(parte, envios.productosPorId.get(parte.idPlanificado())));
            }
        }
    }

    /** Los paquetes pasan a la unidad de rescate; pueden cambiar de identificador planificado. */
    void registrarTrasvase(String idVehiculoDestino, long idOrigen, long idNuevo) {
        LineasDePedido envios = lineasDe(idOrigen);
        if (envios == null) return;
        Linea recibida = null;
        for (Linea linea : envios.lineas) {
            if (linea.idPlanificado != idOrigen || !linea.abierta()) continue;
            if (linea.idVehiculo.equals(idVehiculoDestino)) {
                recibida = linea;
            } else {
                linea.estado = EstadoAsignacion.TRASVASADA;
                linea.limpiarEstimacion();
            }
        }
        if (recibida != null) {
            recibida.idPlanificado = idNuevo;
            envios.productosPorId.put(idNuevo, recibida.productos);
            pedidoDe.put(idNuevo, pedidoDe.get(idOrigen));
        }
    }

    /** Actualiza lo aún no ocurrido. Una unidad que ya llegó conserva su hora real. */
    void estimar(String idVehiculo, long idPlanificado, LocalDateTime llegada,
                 List<PuntoSimulacion> rutaRecorrida,
                 List<PuntoSimulacion> rutaPendiente,
                 List<PuntoSimulacion> rutaEstimada) {
        Linea linea = abierta(idPlanificado, idVehiculo);
        if (linea == null || linea.estado == EstadoAsignacion.EN_ATENCION) return;
        linea.horaLlegada = llegada;
        linea.rutaRecorrida = rutaRecorrida;
        linea.rutaPendiente = rutaPendiente;
        linea.rutaEstimada = rutaEstimada;
    }

    /** Copia inmutable de los pedidos registrados, del más reciente al más antiguo. */
    List<EnvioSimulacion> construir(List<PedidoCorrida> pedidos) {
        List<EnvioSimulacion> envios = new ArrayList<>(pedidos.size());
        for (PedidoCorrida pedido : pedidos) envios.add(construir(pedido));
        envios.sort(Comparator.comparingLong(EnvioSimulacion::idPedido).reversed());
        return List.copyOf(envios);
    }

    private EnvioSimulacion construir(PedidoCorrida pedido) {
        LineasDePedido envios = porPedido.getOrDefault(pedido.idPedido(), new LineasDePedido());
        Map<ViajeUnidad, List<Linea>> viajes = new LinkedHashMap<>();
        int entregada = 0, cubierta = 0;
        for (Linea linea : envios.lineas) {
            viajes.computeIfAbsent(linea.viaje(), v -> new ArrayList<>()).add(linea);
            if (linea.vigente()) cubierta += linea.cantidad;
            if (linea.estado == EstadoAsignacion.ENTREGADA) entregada += linea.cantidad;
        }
        List<Asignacion> asignaciones = new ArrayList<>();
        for (List<Linea> viaje : viajes.values()) {
            List<Integer> productos = viaje.stream().flatMap(l -> l.productos.stream()).sorted().toList();
            int cantidad = viaje.stream().mapToInt(l -> l.cantidad).sum();
            asignaciones.add(viaje.get(0).aAsignacion(pedido.idPedido(), cantidad, productos));
        }
        int sinUnidad = pedido.cantidad() - cubierta;
        if (sinUnidad > 0 && pedido.estado() != EstadoPedido.ENTREGADO) {
            asignaciones.add(new Asignacion(null, EstadoAsignacion.SIN_ASIGNAR, sinUnidad,
                    identificadores(pedido.idPedido(), envios.productosLibres(sinUnidad)),
                    List.of(), List.of(), List.of(), null, null, false, null));
        }
        return new EnvioSimulacion(pedido.idPedido(), pedido.idCliente(),
                new PuntoSimulacion(pedido.x(), pedido.y()), pedido.horasLimite(),
                pedido.fechaRegistro(), pedido.fechaLimite(), pedido.estado(), pedido.cantidad(),
                entregada, entregada > 0 && entregada < pedido.cantidad(), asignaciones);
    }

    private static List<String> identificadores(long idPedido, List<Integer> productos) {
        List<String> ids = new ArrayList<>(productos.size());
        for (int n : productos) ids.add(idPedido + "-" + n);
        return ids;
    }
}
