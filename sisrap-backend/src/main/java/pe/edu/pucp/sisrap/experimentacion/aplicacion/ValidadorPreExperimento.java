package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import pe.edu.pucp.sisrap.experimentacion.dominio.BaseOperativa;
import pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo;
import pe.edu.pucp.sisrap.experimentacion.dominio.Escenario;
import pe.edu.pucp.sisrap.experimentacion.dominio.EscenarioOperativo;
import pe.edu.pucp.sisrap.experimentacion.dominio.PerfilPresion;
import pe.edu.pucp.sisrap.experimentacion.dominio.PlanExperimento;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;

/** Validaciones estructurales antes de gastar tiempo en las corridas. */
public final class ValidadorPreExperimento {

    private ValidadorPreExperimento() {}

    public static void validar(
            PlanExperimento plan,
            BaseOperativa base,
            List<Escenario> escenarios) {

        int esperados = plan.escenariosOperativos().size()
                * plan.perfiles().size()
                * plan.instancias();

        if (escenarios.size() != esperados) {
            throw new IllegalStateException(
                    "Se esperaban " + esperados
                            + " escenarios, pero se generaron "
                            + escenarios.size());
        }

        Set<String> idsEscenario = new HashSet<>();
        Set<String> vehiculosValidos = base.vehiculos().stream()
                .map(v -> v.getIdVehiculo())
                .collect(Collectors.toSet());

        for (Escenario escenario : escenarios) {
            if (!idsEscenario.add(escenario.id())) {
                throw new IllegalStateException("Escenario duplicado: " + escenario.id());
            }
            if (escenario.pedidos().isEmpty()) {
                throw new IllegalStateException("Escenario sin pedidos: " + escenario.id());
            }

            validarPedidos(escenario, base.anchoKm(), base.altoKm());
            validarBloqueos(escenario, base.anchoKm(), base.altoKm());

            for (String vehiculo : escenario.vehiculosEnMantenimiento()) {
                if (!vehiculosValidos.contains(vehiculo)) {
                    throw new IllegalStateException(
                            "Mantenimiento asociado a vehículo inexistente "
                                    + vehiculo + " en " + escenario.id());
                }
            }

            if (!escenario.vehiculosEnBajaPorPerfil().isEmpty()) {
                throw new IllegalStateException(
                        "Se detectaron vehículos dados de baja artificialmente en "
                                + escenario.id()
                                + ". Las averías/bajas deben permanecer deshabilitadas.");
            }
        }

        validarPareamientoEntrePerfiles(plan, escenarios);
    }

    /**
     * Comprueba el nuevo diseño experimental:
     * - Operación diaria y cinco días: NORMAL ⊆ ALTA ⊆ CRITICA.
     * - Colapso: los tres perfiles comienzan con exactamente la misma instancia.
     */
    private static void validarPareamientoEntrePerfiles(
            PlanExperimento plan,
            List<Escenario> escenarios) {

        for (EscenarioOperativo operativo : plan.escenariosOperativos()) {
            for (int instancia = 1; instancia <= plan.instancias(); instancia++) {
                Escenario normal = buscar(escenarios, operativo, PerfilPresion.NORMAL, instancia);
                Escenario alta = buscar(escenarios, operativo, PerfilPresion.ALTA, instancia);
                Escenario critica = buscar(escenarios, operativo, PerfilPresion.CRITICA, instancia);

                if (operativo == EscenarioOperativo.COLAPSO_LOGISTICO) {
                    exigirIgualdadPedidos(normal, alta, "NORMAL", "ALTA");
                    exigirIgualdadPedidos(normal, critica, "NORMAL", "CRITICA");
                    exigirIgualdadBloqueos(normal, alta, "NORMAL", "ALTA");
                    exigirIgualdadBloqueos(normal, critica, "NORMAL", "CRITICA");
                } else {
                    exigirSubconjuntoPedidos(normal, alta, "NORMAL", "ALTA");
                    exigirSubconjuntoPedidos(alta, critica, "ALTA", "CRITICA");
                    exigirSubconjuntoBloqueos(normal, alta, "NORMAL", "ALTA");
                    exigirSubconjuntoBloqueos(alta, critica, "ALTA", "CRITICA");
                }
            }
        }
    }

    private static Escenario buscar(
            List<Escenario> escenarios,
            EscenarioOperativo operativo,
            PerfilPresion perfil,
            int instancia) {
        return escenarios.stream()
                .filter(e -> e.escenarioOperativo() == operativo
                        && e.perfilPresion() == perfil
                        && e.instancia() == instancia)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Falta escenario " + operativo + "/" + perfil + "/N" + instancia));
    }

    private static void exigirSubconjuntoPedidos(
            Escenario menor,
            Escenario mayor,
            String nombreMenor,
            String nombreMayor) {

        Map<Long, String> mayorPorId = firmasPedidos(mayor);
        for (Pedido p : menor.pedidos()) {
            String firmaMayor = mayorPorId.get(p.getIdPedido());
            if (firmaMayor == null) {
                throw new IllegalStateException(
                        "El pedido " + p.getIdPedido() + " de " + menor.id()
                                + " no existe en el perfil " + nombreMayor);
            }
            if (!firmaPedido(p).equals(firmaMayor)) {
                throw new IllegalStateException(
                        "El pedido compartido " + p.getIdPedido()
                                + " cambió sus atributos entre " + nombreMenor
                                + " y " + nombreMayor + " en N" + menor.instancia());
            }
        }
    }

    private static void exigirIgualdadPedidos(
            Escenario a,
            Escenario b,
            String nombreA,
            String nombreB) {
        Map<Long, String> fa = firmasPedidos(a);
        Map<Long, String> fb = firmasPedidos(b);
        if (!fa.equals(fb)) {
            throw new IllegalStateException(
                    "COLAPSO debe comenzar con los mismos pedidos para "
                            + nombreA + " y " + nombreB + " en N" + a.instancia());
        }
    }

    private static void exigirSubconjuntoBloqueos(
            Escenario menor,
            Escenario mayor,
            String nombreMenor,
            String nombreMayor) {
        Set<String> firmasMayor = mayor.bloqueosProgramados().stream()
                .map(ValidadorPreExperimento::firmaBloqueo)
                .collect(Collectors.toSet());
        for (Bloqueo b : menor.bloqueosProgramados()) {
            if (!firmasMayor.contains(firmaBloqueo(b))) {
                throw new IllegalStateException(
                        "Un bloqueo de " + nombreMenor + " no está contenido en "
                                + nombreMayor + " para " + menor.escenarioOperativo()
                                + "/N" + menor.instancia());
            }
        }
    }

    private static void exigirIgualdadBloqueos(
            Escenario a,
            Escenario b,
            String nombreA,
            String nombreB) {
        Set<String> fa = a.bloqueosProgramados().stream()
                .map(ValidadorPreExperimento::firmaBloqueo)
                .collect(Collectors.toSet());
        Set<String> fb = b.bloqueosProgramados().stream()
                .map(ValidadorPreExperimento::firmaBloqueo)
                .collect(Collectors.toSet());
        if (!fa.equals(fb)) {
            throw new IllegalStateException(
                    "COLAPSO debe comenzar con los mismos bloqueos para "
                            + nombreA + " y " + nombreB + " en N" + a.instancia());
        }
    }

    private static Map<Long, String> firmasPedidos(Escenario escenario) {
        Map<Long, String> salida = new HashMap<>();
        for (Pedido p : escenario.pedidos()) {
            salida.put(p.getIdPedido(), firmaPedido(p));
        }
        return salida;
    }

    private static String firmaPedido(Pedido p) {
        return p.getIdPedido() + "|" + p.getIdCliente() + "|" + p.getCantidadQq()
                + "|" + p.getPrioridad() + "|" + p.getHorasLimite()
                + "|" + p.getFechaLlegada()
                + "|" + p.getUbicacion().getX() + "," + p.getUbicacion().getY();
    }

    private static String firmaBloqueo(Bloqueo b) {
        return b.inicio() + "|" + b.fin() + "|"
                + b.vertices().stream()
                        .map(n -> n.getX() + ":" + n.getY())
                        .collect(Collectors.joining(";"));
    }

    private static void validarPedidos(
            Escenario escenario,
            int ancho,
            int alto) {

        Set<Long> ids = new HashSet<>();

        for (var pedido : escenario.pedidos()) {
            if (!ids.add(pedido.getIdPedido())) {
                throw new IllegalStateException(
                        "Pedido duplicado " + pedido.getIdPedido() + " en " + escenario.id());
            }

            int x = pedido.getUbicacion().getX();
            int y = pedido.getUbicacion().getY();

            if (x < 0 || x > ancho || y < 0 || y > alto) {
                throw new IllegalStateException(
                        "Pedido fuera del mapa en " + escenario.id()
                                + ": (" + x + "," + y + ")");
            }

            if (!pedido.getFechaLimite().isAfter(pedido.getFechaLlegada())) {
                throw new IllegalStateException(
                        "Pedido con deadline inválido en " + escenario.id());
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
                        "Bloqueo con duración inválida en " + escenario.id());
            }

            if (bloqueo.vertices().size() < 2) {
                throw new IllegalStateException(
                        "Bloqueo sin suficientes vértices en " + escenario.id());
            }

            for (var nodo : bloqueo.vertices()) {
                if (nodo.getX() < 0 || nodo.getX() > ancho
                        || nodo.getY() < 0 || nodo.getY() > alto) {
                    throw new IllegalStateException(
                            "Bloqueo fuera del mapa en " + escenario.id()
                                    + ": (" + nodo.getX() + "," + nodo.getY() + ")");
                }
            }

            for (int i = 0; i < bloqueo.vertices().size() - 1; i++) {
                var a = bloqueo.vertices().get(i);
                var b = bloqueo.vertices().get(i + 1);

                boolean horizontal = a.getY() == b.getY();
                boolean vertical = a.getX() == b.getX();

                if (!horizontal && !vertical) {
                    throw new IllegalStateException(
                            "Bloqueo diagonal en " + escenario.id()
                                    + ": (" + a.getX() + "," + a.getY() + ") -> ("
                                    + b.getX() + "," + b.getY() + ")");
                }

                if (a.getX() == b.getX() && a.getY() == b.getY()) {
                    throw new IllegalStateException(
                            "Bloqueo con tramo de longitud cero en " + escenario.id());
                }
            }
        }
    }
}