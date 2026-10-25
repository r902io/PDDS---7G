package pe.edu.pucp.sisrap.simulacion.aplicacion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.DistanciaReticula;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Ruta;

/** Recuperación física de pedidos ya vencidos, independiente de la evaluación de GA. */
public final class RecuperacionPedidosVencidos {

    private RecuperacionPedidosVencidos() { }

    /**
     * Genera rutas únicamente para pedidos cuyo SLA ya venció. Nunca altera la
     * fecha límite original ni los considera puntuales. Se usan vehículos libres,
     * almacenes con stock, caminos transitables y capacidad suficiente.
     */
    public static List<Ruta> construir(
            ContextoPlanificacion contexto,
            Set<String> vehiculosOcupados,
            Map<String, Integer> stockReservado,
            Predicate<Ruta> mantenimientoCompatible) {

        if (contexto == null || vehiculosOcupados == null
                || stockReservado == null || mantenimientoCompatible == null) {
            throw new IllegalArgumentException("Faltan condiciones para recuperar pedidos vencidos");
        }

        Map<String, Ruta> asignadas = new LinkedHashMap<>();
        Map<String, Integer> reservado = new HashMap<>(stockReservado);
        List<Pedido> vencidos = contexto.getPedidos().stream()
                .filter(p -> p.getFechaLimite().isBefore(contexto.instante()))
                .sorted(Comparator.comparing(Pedido::getFechaLimite)
                        .thenComparing(Pedido::getIdPedido))
                .toList();

        for (Pedido pedido : vencidos) {
            Ruta elegida = null;
            double menorLlegada = Double.POSITIVE_INFINITY;
            double menorCosto = Double.POSITIVE_INFINITY;

            for (Vehiculo vehiculo : contexto.getVehiculos()) {
                if (!vehiculo.isDisponible()
                        || vehiculosOcupados.contains(vehiculo.getIdVehiculo())) continue;

                Ruta previa = asignadas.get(vehiculo.getIdVehiculo());
                if (previa != null
                        && previa.cargaTotal() + pedido.getCantidadQq()
                            > vehiculo.getCapacidadPaquetes()) continue;
                if (previa == null && pedido.getCantidadQq() > vehiculo.getCapacidadPaquetes()) {
                    continue;
                }

                List<Almacen> origenes = previa == null
                        ? contexto.getAlmacenes() : List.of(previa.getAlmacenOrigen());
                for (Almacen origen : origenes) {
                    Ruta candidata;
                    if (previa != null) {
                        candidata = previa.copiar();
                    } else {
                        candidata = contexto.respetarPosicionVehiculo()
                                ? new Ruta(vehiculo, origen, vehiculo.getPosicionActual(),
                                        true, contexto.instante(), contexto.reglas())
                                : new Ruta(vehiculo, origen,
                                        contexto.instante(), contexto.reglas());
                        candidata.setNodosBloqueados(contexto.nodosBloqueados());
                        candidata.setCalendarioBloqueos(contexto.calendarioBloqueos());
                        candidata.setDisponibleHasta(contexto.disponibleHasta().get(vehiculo.getIdVehiculo()));
                        candidata.setAlmacenesCandidatos(contexto.getAlmacenes());
                    }
                    candidata.getSecuenciaPedidos().add(pedido);
                    seleccionarRetorno(candidata, contexto);
                    candidata.recalcular();

                    if (candidata.isIntransitable() || candidata.isFueraDeTurno()
                            || candidata.cargaTotal() > vehiculo.getCapacidadPaquetes()
                            || !stockDisponible(candidata, previa, reservado)
                            || !mantenimientoCompatible.test(candidata)) continue;

                    double llegada = candidata.horaLlegadaDe(
                            candidata.getSecuenciaPedidos().size() - 1);
                    if (!Double.isFinite(llegada)) continue;
                    if (llegada < menorLlegada - 1e-9
                            || (Math.abs(llegada - menorLlegada) <= 1e-9
                                    && candidata.getCostoTotal() < menorCosto)) {
                        elegida = candidata;
                        menorLlegada = llegada;
                        menorCosto = candidata.getCostoTotal();
                    }
                }
            }

            if (elegida != null) {
                Ruta anterior = asignadas.put(elegida.getVehiculo().getIdVehiculo(), elegida);
                String almacen = elegida.getAlmacenOrigen().getIdAlmacen();
                if (elegida.getAlmacenOrigen().getCapacidadMaxima() != null) {
                    // Reemplaza la reserva anterior de ese mismo vehículo.
                    int cargaAnterior = anterior == null ? 0 : anterior.cargaTotal();
                    reservado.merge(almacen, elegida.cargaTotal() - cargaAnterior, Integer::sum);
                }
            }
        }
        return List.copyOf(asignadas.values());
    }

    private static boolean stockDisponible(Ruta candidata, Ruta previa,
                                           Map<String, Integer> reservado) {
        Almacen almacen = candidata.getAlmacenOrigen();
        if (almacen.getCapacidadMaxima() == null) return true;
        Integer stock = almacen.getStockActual();
        if (stock == null || stock < 0) return false;
        int anterior = previa == null ? 0 : previa.cargaTotal();
        return reservado.getOrDefault(almacen.getIdAlmacen(), 0)
                + candidata.cargaTotal() - anterior <= stock;
    }

    private static void seleccionarRetorno(Ruta ruta, ContextoPlanificacion contexto) {
        if (!contexto.reglas().incluirRetorno() || ruta.getSecuenciaPedidos().isEmpty()) return;
        var destino = ruta.getSecuenciaPedidos()
                .get(ruta.getSecuenciaPedidos().size() - 1).getUbicacion();
        Almacen mejor = ruta.getAlmacenOrigen();
        int minima = DistanciaReticula.distancia(destino, mejor.getUbicacion(),
                contexto.nodosBloqueados(), contexto.reglas().anchoCiudad(),
                contexto.reglas().altoCiudad());
        for (Almacen almacen : contexto.getAlmacenes()) {
            if (!almacen.tieneStockDisponible()) continue;
            int distancia = DistanciaReticula.distancia(destino, almacen.getUbicacion(),
                    contexto.nodosBloqueados(), contexto.reglas().anchoCiudad(),
                    contexto.reglas().altoCiudad());
            if (distancia >= 0 && (minima < 0 || distancia < minima)) {
                mejor = almacen;
                minima = distancia;
            }
        }
        ruta.setAlmacenRetorno(mejor);
    }
}
