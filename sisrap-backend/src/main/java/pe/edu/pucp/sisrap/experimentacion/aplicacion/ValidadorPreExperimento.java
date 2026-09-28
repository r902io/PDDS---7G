package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pe.edu.pucp.sisrap.experimentacion.dominio.BaseOperativa;
import pe.edu.pucp.sisrap.experimentacion.dominio.Escenario;
import pe.edu.pucp.sisrap.experimentacion.dominio.PlanExperimento;

public final class ValidadorPreExperimento {

    private ValidadorPreExperimento() {
    }

    public static void validar(
            PlanExperimento plan,
            BaseOperativa base,
            List<Escenario> escenarios) {

        int esperados =
                plan.escenariosOperativos().size()
                        * plan.perfiles().size()
                        * plan.instancias();

        if (escenarios.size() != esperados) {
            throw new IllegalStateException(
                    "Se esperaban " + esperados
                            + " escenarios, pero se generaron "
                            + escenarios.size());
        }

        Set<String> idsEscenario = new HashSet<>();

        Set<String> vehiculosValidos = base.vehiculos()
                .stream()
                .map(v -> v.getIdVehiculo())
                .collect(java.util.stream.Collectors.toSet());

        for (Escenario escenario : escenarios) {

            if (!idsEscenario.add(escenario.id())) {
                throw new IllegalStateException(
                        "Escenario duplicado: " + escenario.id());
            }

            if (escenario.pedidos().isEmpty()) {
                throw new IllegalStateException(
                        "Escenario sin pedidos: " + escenario.id());
            }

            validarPedidos(
                    escenario,
                    base.anchoKm(),
                    base.altoKm());

            validarBloqueos(
                    escenario,
                    base.anchoKm(),
                    base.altoKm());

            for (String vehiculo
                    : escenario.vehiculosEnMantenimiento()) {

                if (!vehiculosValidos.contains(vehiculo)) {
                    throw new IllegalStateException(
                            "Mantenimiento asociado a vehículo inexistente "
                                    + vehiculo
                                    + " en "
                                    + escenario.id());
                }
            }

            /*
             * En la versión actual no se generan averías ni bajas sintéticas.
             */
            if (!escenario.vehiculosEnBajaPorPerfil().isEmpty()) {
                throw new IllegalStateException(
                        "Se detectaron vehículos dados de baja artificialmente "
                                + "en " + escenario.id()
                                + ". Las averías/bajas deben permanecer "
                                + "deshabilitadas.");
            }
        }
    }

    private static void validarPedidos(
            Escenario escenario,
            int ancho,
            int alto) {

        Set<Long> ids = new HashSet<>();

        for (var pedido : escenario.pedidos()) {

            if (!ids.add(pedido.getIdPedido())) {
                throw new IllegalStateException(
                        "Pedido duplicado "
                                + pedido.getIdPedido()
                                + " en "
                                + escenario.id());
            }

            int x = pedido.getUbicacion().getX();
            int y = pedido.getUbicacion().getY();

            if (x < 0 || x > ancho
                    || y < 0 || y > alto) {

                throw new IllegalStateException(
                        "Pedido fuera del mapa en "
                                + escenario.id()
                                + ": (" + x + "," + y + ")");
            }

            if (!pedido.getFechaLimite()
                    .isAfter(pedido.getFechaLlegada())) {

                throw new IllegalStateException(
                        "Pedido con deadline inválido en "
                                + escenario.id());
            }
        }
    }

    private static void validarBloqueos(
            Escenario escenario,
            int ancho,
            int alto) {

        for (var bloqueo : escenario.bloqueosProgramados()) {

            if (!bloqueo.fin().isAfter(bloqueo.inicio())) {
                throw new IllegalStateException(
                        "Bloqueo con duración inválida en "
                                + escenario.id());
            }

            if (bloqueo.vertices().size() < 2) {
                throw new IllegalStateException(
                        "Bloqueo sin suficientes vértices en "
                                + escenario.id());
            }

            for (var nodo : bloqueo.vertices()) {

                if (nodo.getX() < 0
                        || nodo.getX() > ancho
                        || nodo.getY() < 0
                        || nodo.getY() > alto) {

                    throw new IllegalStateException(
                            "Bloqueo fuera del mapa en "
                                    + escenario.id()
                                    + ": ("
                                    + nodo.getX()
                                    + ","
                                    + nodo.getY()
                                    + ")");
                }
            }

            for (int i = 0;
                    i < bloqueo.vertices().size() - 1;
                    i++) {

                var a = bloqueo.vertices().get(i);
                var b = bloqueo.vertices().get(i + 1);

                boolean horizontal =
                        a.getY() == b.getY();

                boolean vertical =
                        a.getX() == b.getX();

                if (!horizontal && !vertical) {
                    throw new IllegalStateException(
                            "Bloqueo diagonal en "
                                    + escenario.id()
                                    + ": ("
                                    + a.getX()
                                    + ","
                                    + a.getY()
                                    + ") -> ("
                                    + b.getX()
                                    + ","
                                    + b.getY()
                                    + ")");
                }

                if (a.getX() == b.getX()
                        && a.getY() == b.getY()) {

                    throw new IllegalStateException(
                            "Bloqueo con tramo de longitud cero en "
                                    + escenario.id());
                }
            }
        }
    }
}