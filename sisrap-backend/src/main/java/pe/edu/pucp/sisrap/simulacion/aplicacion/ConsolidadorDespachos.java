package pe.edu.pucp.sisrap.simulacion.aplicacion;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.DistanciaReticula;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Ruta;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;

/** Consolidación exclusiva del despacho operativo, posterior a GA; no cambia su función objetivo. */
public final class ConsolidadorDespachos {

    private static final int MINUTOS_ESPERA = 30;

    private ConsolidadorDespachos() { }

    /**
     * Solo difiere brevemente un pedido no prioritario si hay margen suficiente.
     * En todos los demás casos el planificador lo despacha inmediatamente.
     */
    public static boolean convieneEsperar(List<Pedido> pedidos, ContextoPlanificacion contexto,
                                       LocalDateTime finCorrida) {
        if (pedidos.size() != 1) return false;
        Pedido pedido = pedidos.get(0);
        LocalDateTime ahora = contexto.instante();
        if (pedido.getHorasLimite() < 8
                || !pedido.getFechaLlegada().plusMinutes(MINUTOS_ESPERA).isAfter(ahora)
                || (finCorrida != null && !finCorrida.isAfter(ahora.plusMinutes(MINUTOS_ESPERA)))) {
            return false;
        }
        double velocidadMaxima = contexto.vehiculos().stream()
                .filter(v -> v.isDisponible() && v.getCapacidadPaquetes() >= pedido.getCantidadQq())
                .mapToDouble(v -> v.getVelocidadKmh()).max().orElse(0);
        if (velocidadMaxima <= 0) return false;
        int distancia = contexto.almacenes().stream()
                .filter(Almacen::tieneStockDisponible)
                .mapToInt(a -> DistanciaReticula.distancia(a.getUbicacion(), pedido.getUbicacion(),
                        contexto.nodosBloqueados(), contexto.reglas().anchoCiudad(),
                        contexto.reglas().altoCiudad()))
                .filter(d -> d >= 0).min().orElse(Integer.MAX_VALUE);
        if (distancia == Integer.MAX_VALUE) return false;
        double viajeHoras = distancia * contexto.reglas().distanciaNodoKm() / velocidadMaxima;
        // Dos horas de holgura para contingencias y replanificación, además del viaje previsto.
        double holguraHoras = Duration.between(ahora, pedido.getFechaLimite()).toMinutes() / 60.0;
        return holguraHoras > viajeHoras + 2.0 + MINUTOS_ESPERA / 60.0;
    }

    /** Agrupa entregas ya confirmadas cuando reduce el costo y mantiene la factibilidad. */
    public static void consolidar(Solucion solucion, ContextoPlanificacion contexto,
                                  Map<String, Integer> stockReservadoPrevio) {
        List<Ruta> rutas = solucion.getRutas();
        if (rutas == null || rutas.isEmpty()) return;
        // El algoritmo puede dejar rutas vacías; nunca se crean pedidos adicionales.
        List<Ruta> donantes = rutas.stream()
                .filter(r -> !r.getSecuenciaPedidos().isEmpty())
                .sorted(Comparator.comparingInt(Ruta::cargaTotal))
                .toList();
        for (Ruta donante : donantes) {
            if (donante.getSecuenciaPedidos().isEmpty()) continue;
            int cantidad = donante.cargaTotal();
            Ruta mejor = null;
            List<Pedido> mejorOrden = null;
            double ahorroMaximo = 1e-6;
            for (Ruta receptor : rutas) {
                if (receptor == donante || receptor.getSecuenciaPedidos().isEmpty()
                        || !receptor.getVehiculo().isDisponible()
                        || receptor.cargaTotal() + cantidad > receptor.getVehiculo().getCapacidadPaquetes()) {
                    continue;
                }
                List<Pedido> base = receptor.getSecuenciaPedidos();
                for (int posicion = 0; posicion <= base.size(); posicion++) {
                    Ruta candidata = receptor.copiar();
                    candidata.getSecuenciaPedidos().addAll(posicion, donante.getSecuenciaPedidos());
                    candidata.recalcular();
                    if (!factible(candidata) || !stockSuficiente(rutas, receptor, candidata, donante, stockReservadoPrevio)) {
                        continue;
                    }
                    double ahorro = receptor.getCostoTotal() + donante.getCostoTotal()
                            - candidata.getCostoTotal();
                    if (ahorro > ahorroMaximo) {
                        ahorroMaximo = ahorro;
                        mejor = receptor;
                        mejorOrden = new ArrayList<>(candidata.getSecuenciaPedidos());
                    }
                }
            }
            if (mejor != null) {
                mejor.getSecuenciaPedidos().clear();
                mejor.getSecuenciaPedidos().addAll(mejorOrden);
                mejor.recalcular();
                donante.getSecuenciaPedidos().clear();
                donante.recalcular();
            }
        }
        // Una ruta debe acabar en un almacén, no necesariamente en su almacén de carga.
        for (Ruta ruta : rutas) {
            if (ruta.getSecuenciaPedidos().isEmpty()) continue;
            Nodo finalEntrega = ruta.getSecuenciaPedidos()
                    .get(ruta.getSecuenciaPedidos().size() - 1).getUbicacion();
            Almacen destino = ruta.getAlmacenOrigen();
            int distanciaMinima = distancia(contexto, finalEntrega, destino.getUbicacion());
            for (Almacen almacen : contexto.almacenes()) {
                if (!almacen.tieneStockDisponible()) continue;
                int distancia = distancia(contexto, finalEntrega, almacen.getUbicacion());
                if (distancia >= 0 && (distanciaMinima < 0 || distancia < distanciaMinima)) {
                    destino = almacen;
                    distanciaMinima = distancia;
                }
            }
            ruta.setAlmacenRetorno(destino);
            ruta.recalcular();
        }
    }

    private static int distancia(ContextoPlanificacion contexto, Nodo origen, Nodo destino) {
        return DistanciaReticula.distancia(origen, destino, contexto.nodosBloqueados(),
                contexto.reglas().anchoCiudad(), contexto.reglas().altoCiudad());
    }

    private static boolean factible(Ruta ruta) {
        if (ruta.isIntransitable() || ruta.isFueraDeTurno()
                || ruta.cargaTotal() > ruta.getVehiculo().getCapacidadPaquetes()) return false;
        for (int i = 0; i < ruta.getSecuenciaPedidos().size(); i++) {
            if (ruta.retrasoDe(i) > 1e-9) return false;
        }
        return true;
    }

    private static boolean stockSuficiente(List<Ruta> rutas, Ruta receptor, Ruta candidato,
                                           Ruta donante, Map<String, Integer> stockReservadoPrevio) {
        Map<String, Integer> reservado = new HashMap<>(stockReservadoPrevio);
        for (Ruta ruta : rutas) {
            if (ruta == donante) continue;
            Ruta efectiva = ruta == receptor ? candidato : ruta;
            if (efectiva.getSecuenciaPedidos().isEmpty()) continue;
            Almacen origen = efectiva.getAlmacenOrigen();
            if (origen.getCapacidadMaxima() == null) continue;
            int total = reservado.merge(origen.getIdAlmacen(), efectiva.cargaTotal(), Integer::sum);
            if (origen.getStockActual() == null || total > origen.getStockActual()) return false;
        }
        return true;
    }
}
