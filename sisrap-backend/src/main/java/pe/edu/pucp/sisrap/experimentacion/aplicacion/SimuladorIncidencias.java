package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import pe.edu.pucp.sisrap.experimentacion.dominio.PerfilPresion;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Ruta;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;

/**
 * Simula, sobre una solución ya planificada del escenario OPERACION_DIARIA, la incidencia (avería de
 * vehículo y/o bloqueo de calle) descrita en la sección 4.2 del informe de Selección de Algoritmos:
 * "el planificador debe modificar las rutas de los pedidos que todavía no hayan sido atendidos".
 * <p>
 * Supuesto explícito (el modelo del planificador es estático, sin reloj de simulación dentro de una
 * corrida): los "pedidos aún no atendidos" en el instante de la incidencia se aproximan mediante un
 * subconjunto aleatorio de los pedidos ya asignados en la solución inicial, en la proporción que define
 * el perfil de presión (ver {@link PerfilPresion#proporcionPedidosAfectados}). Este supuesto se declara
 * en el manifiesto del experimento para no sobrevender el resultado.
 */
public final class SimuladorIncidencias {
    private SimuladorIncidencias() {}

    public record Incidencia(ContextoPlanificacion contextoTrasIncidencia, Solucion solucionTrasIncidencia,
                             int pedidosAfectados, int vehiculosEnAveria) {}

    public static Incidencia simular(Solucion solucionInicial, ContextoPlanificacion contexto, PerfilPresion perfil, Random random) {
        // 1) Avería de vehículo(s): se eligen entre los que tienen pedidos asignados, para que la
        //    incidencia obligue a reasignar rutas y no solo a "apagar" un vehículo ya vacío.
        List<Ruta> conCarga = solucionInicial.getRutas().stream().filter(r -> !r.getSecuenciaPedidos().isEmpty()).toList();
        List<Vehiculo> vehiculosActualizados = new ArrayList<>();
        int enAveria = 0;
        for (Vehiculo v : contexto.getVehiculos()) {
            boolean averiado = enAveria < perfil.vehiculosAveriaIncidente
                    && v.isDisponible()
                    && conCarga.stream().anyMatch(r -> r.getVehiculo().getIdVehiculo().equals(v.getIdVehiculo()))
                    && random.nextDouble() < 0.5;
            var copia = new Vehiculo(v.getIdVehiculo(), v.getCapacidadPaquetes(), v.getVelocidadKmh(), v.getCostoPorKm(), v.getPosicionActual());
            copia.setDisponible(v.isDisponible() && !averiado);
            if (averiado) enAveria++;
            vehiculosActualizados.add(copia);
        }

        // 2) Bloqueo: un subconjunto de pedidos ya asignados queda con su ruta invalidada y debe reinsertarse.
        Solucion afectada = solucionInicial.copiar();
        int marcados = 0;
        for (Ruta ruta : afectada.getRutas()) {
            boolean vehiculoAveriado = vehiculosActualizados.stream()
                    .anyMatch(v -> v.getIdVehiculo().equals(ruta.getVehiculo().getIdVehiculo()) && !v.isDisponible());
            List<Pedido> conservados = new ArrayList<>();
            for (Pedido p : ruta.getSecuenciaPedidos()) {
                boolean afectadoPorBloqueo = !vehiculoAveriado && random.nextDouble() < perfil.proporcionPedidosAfectados;
                if (vehiculoAveriado || afectadoPorBloqueo) {
                    marcados++;
                } else {
                    conservados.add(p);
                }
            }
            ruta.getSecuenciaPedidos().clear();
            ruta.getSecuenciaPedidos().addAll(conservados);
            ruta.recalcular();
        }

        var contextoActualizado = new ContextoPlanificacion(contexto.getPedidos(), vehiculosActualizados,
                contexto.getAlmacenes(), contexto.instante(), contexto.reglas());
        return new Incidencia(contextoActualizado, afectada, marcados, enAveria);
    }
}