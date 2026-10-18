package pe.edu.pucp.sisrap.simulacion.aplicacion;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pe.edu.pucp.sisrap.simulacion.dominio.IndicadoresOperacion;
import pe.edu.pucp.sisrap.simulacion.dominio.PerfilFlotaSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.ResumenPedidosSimulacion;

/** Indicadores de ejecución basados en tramos consumados, no en rutas previstas. */
public final class AcumuladorIndicadores {
    private static final int MAX_MUESTRAS = 300;
    private static final int MINUTOS_ENTRE_MUESTRAS = 15;

    private final Map<String, PerfilFlotaSimulacion> flota = new LinkedHashMap<>();
    private final Map<String, Double> horasOcupadas = new LinkedHashMap<>();
    private final List<IndicadoresOperacion.MuestraTemporal> muestras = new ArrayList<>();
    private LocalDateTime inicio;
    private double distanciaKm;
    private double costoSoles;

    public void iniciar(LocalDateTime instante, List<PerfilFlotaSimulacion> perfiles) {
        if (instante == null || perfiles == null || perfiles.isEmpty()) {
            throw new IllegalArgumentException("El instante y la flota son obligatorios");
        }
        inicio = instante;
        flota.clear();
        horasOcupadas.clear();
        muestras.clear();
        distanciaKm = 0;
        costoSoles = 0;
        for (PerfilFlotaSimulacion perfil : perfiles) {
            if (flota.putIfAbsent(perfil.idVehiculo(), perfil) != null) {
                throw new IllegalArgumentException("Vehículo duplicado: " + perfil.idVehiculo());
            }
            horasOcupadas.put(perfil.idVehiculo(), 0.0);
        }
    }

    public String tipoVehiculo(String idVehiculo) {
        PerfilFlotaSimulacion perfil = flota.get(idVehiculo);
        if (perfil == null) throw new IllegalArgumentException("Vehículo desconocido: " + idVehiculo);
        return perfil.tipo();
    }

    public void registrarTramo(String idVehiculo, double longitudKm) {
        PerfilFlotaSimulacion perfil = flota.get(idVehiculo);
        if (perfil == null) throw new IllegalArgumentException("Vehículo desconocido: " + idVehiculo);
        if (!Double.isFinite(longitudKm) || longitudKm < 0) {
            throw new IllegalArgumentException("Longitud de tramo inválida");
        }
        distanciaKm += longitudKm;
        costoSoles += longitudKm * perfil.costoPorKm();
    }

    /** La ocupación incluye el tiempo de atención y los desplazamientos de rutas activas. */
    public void registrarOcupacion(Set<String> idsEnRuta, double horasSimuladas) {
        if (!Double.isFinite(horasSimuladas) || horasSimuladas < 0) {
            throw new IllegalArgumentException("Intervalo de simulación inválido");
        }
        for (String id : idsEnRuta) {
            if (!horasOcupadas.containsKey(id)) {
                throw new IllegalArgumentException("Vehículo desconocido: " + id);
            }
            horasOcupadas.merge(id, horasSimuladas, Double::sum);
        }
    }

    public IndicadoresOperacion obtener(LocalDateTime reloj, Set<String> idsEnRuta,
            ResumenPedidosSimulacion pedidos, boolean tomarMuestra) {
        if (inicio == null) throw new IllegalStateException("Los indicadores no se han iniciado");
        if (reloj.isBefore(inicio)) throw new IllegalArgumentException("El reloj precede al inicio");
        if (tomarMuestra) agregarMuestra(reloj, pedidos);

        double horas = Duration.between(inicio, reloj).toNanos() / 3_600_000_000_000.0;
        Map<String, Integer> cantidadPorTipo = new LinkedHashMap<>();
        Map<String, Integer> activosPorTipo = new LinkedHashMap<>();
        Map<String, Double> horasPorTipo = new LinkedHashMap<>();
        for (PerfilFlotaSimulacion vehiculo : flota.values()) {
            String tipo = vehiculo.tipo();
            cantidadPorTipo.merge(tipo, 1, Integer::sum);
            if (idsEnRuta.contains(vehiculo.idVehiculo())) activosPorTipo.merge(tipo, 1, Integer::sum);
            horasPorTipo.merge(tipo, horasOcupadas.getOrDefault(vehiculo.idVehiculo(), 0.0), Double::sum);
        }
        List<IndicadoresOperacion.UtilizacionTipo> utilizacion = new ArrayList<>();
        for (var entry : cantidadPorTipo.entrySet()) {
            String tipo = entry.getKey();
            int total = entry.getValue();
            int activos = activosPorTipo.getOrDefault(tipo, 0);
            double acumulado = horas > 0 ? 100.0 * horasPorTipo.getOrDefault(tipo, 0.0) / (horas * total) : 0;
            utilizacion.add(new IndicadoresOperacion.UtilizacionTipo(tipo, total, activos,
                    100.0 * activos / total, Math.min(100.0, acumulado)));
        }
        return new IndicadoresOperacion(distanciaKm, costoSoles, utilizacion, muestras);
    }

    private void agregarMuestra(LocalDateTime instante, ResumenPedidosSimulacion pedidos) {
        int entregados = pedidos.entregados();
        int retrasados = pedidos.retrasados();
        int evaluados = entregados + retrasados;
        double porcentaje = evaluados > 0 ? 100.0 * entregados / evaluados : 100.0;
        IndicadoresOperacion.MuestraTemporal nueva = new IndicadoresOperacion.MuestraTemporal(
                instante, distanciaKm, costoSoles, entregados, retrasados, porcentaje);
        if (!muestras.isEmpty()) {
            var ultima = muestras.getLast();
            if (ultima.instante().equals(instante)) {
                muestras.set(muestras.size() - 1, nueva);
                return;
            }
            if (Duration.between(ultima.instante(), instante).toMinutes() < MINUTOS_ENTRE_MUESTRAS) {
                return;
            }
        }
        muestras.add(nueva);
        if (muestras.size() > MAX_MUESTRAS) {
            // Retener extremos y muestras alternadas para limitar tamaño de SSE en corridas largas.
            List<IndicadoresOperacion.MuestraTemporal> reducidas = new ArrayList<>();
            reducidas.add(muestras.getFirst());
            for (int i = 2; i < muestras.size() - 1; i += 2) reducidas.add(muestras.get(i));
            reducidas.add(muestras.getLast());
            muestras.clear();
            muestras.addAll(reducidas);
        }
    }
}
