package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import pe.edu.pucp.sisrap.experimentacion.dominio.PerfilPresion;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Ruta;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;

/** Simula una incidencia reproducible para OPERACION_DIARIA. */
public final class SimuladorIncidencias {
    private SimuladorIncidencias() {}

    public record Incidencia(ContextoPlanificacion contextoTrasIncidencia,
                             Solucion solucionTrasIncidencia,
                             int pedidosAfectados,
                             int vehiculosEnAveria) {}

    public static Incidencia simular(Solucion solucionInicial,
                                     ContextoPlanificacion contexto,
                                     PerfilPresion perfil,
                                     Random random) {
        List<String> candidatosAveria = solucionInicial.getRutas().stream()
                .filter(r -> !r.getSecuenciaPedidos().isEmpty())
                .map(r -> r.getVehiculo().getIdVehiculo())
                .distinct()
                .filter(id -> contexto.getVehiculos().stream()
                        .anyMatch(v -> v.getIdVehiculo().equals(id) && v.isDisponible()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidatosAveria, random);

        int cantidadAverias = Math.min(perfil.vehiculosAveriaIncidente, candidatosAveria.size());
        Set<String> idsAveriados = new HashSet<>(candidatosAveria.subList(0, cantidadAverias));

        List<Vehiculo> vehiculosActualizados = new ArrayList<>();
        for (Vehiculo v : contexto.getVehiculos()) {
            var copia = new Vehiculo(
                    v.getIdVehiculo(),
                    v.getCapacidadPaquetes(),
                    v.getVelocidadKmh(),
                    v.getCostoPorKm(),
                    v.getPosicionActual());
            copia.setDisponible(v.isDisponible() && !idsAveriados.contains(v.getIdVehiculo()));
            vehiculosActualizados.add(copia);
        }

        Solucion afectada = solucionInicial.copiar();

        List<Long> candidatosBloqueo = afectada.getRutas().stream()
                .filter(r -> !idsAveriados.contains(r.getVehiculo().getIdVehiculo()))
                .flatMap(r -> r.getSecuenciaPedidos().stream())
                .map(Pedido::getIdPedido)
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidatosBloqueo, random);

        int objetivoBloqueo = (int) Math.ceil(
                candidatosBloqueo.size() * perfil.proporcionPedidosAfectados);
        Set<Long> afectadosPorBloqueo = new HashSet<>(
                candidatosBloqueo.subList(0, Math.min(objetivoBloqueo, candidatosBloqueo.size())));

        int pedidosAfectados = 0;
        for (Ruta ruta : afectada.getRutas()) {
            boolean vehiculoAveriado = idsAveriados.contains(ruta.getVehiculo().getIdVehiculo());
            List<Pedido> conservados = new ArrayList<>();

            for (Pedido p : ruta.getSecuenciaPedidos()) {
                boolean afectado = vehiculoAveriado || afectadosPorBloqueo.contains(p.getIdPedido());
                if (afectado) pedidosAfectados++;
                else conservados.add(p);
            }

            ruta.getSecuenciaPedidos().clear();
            ruta.getSecuenciaPedidos().addAll(conservados);
            ruta.recalcular();
        }

        var contextoActualizado = new ContextoPlanificacion(
                contexto.getPedidos(),
                vehiculosActualizados,
                contexto.getAlmacenes(),
                contexto.instante(),
                contexto.reglas(),
                contexto.nodosBloqueados());

        return new Incidencia(
                contextoActualizado,
                afectada,
                pedidosAfectados,
                cantidadAverias);
    }
}