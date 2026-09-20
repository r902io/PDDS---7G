package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pe.edu.pucp.sisrap.experimentacion.dominio.BaseOperativa;
import pe.edu.pucp.sisrap.experimentacion.dominio.DatosArchivos;
import pe.edu.pucp.sisrap.experimentacion.dominio.Escenario;
import pe.edu.pucp.sisrap.experimentacion.dominio.PlanExperimento;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;

/**
 * Convierte los datos recopilados en instancias del problema.
 * Para cada tamaño n y cada instancia j se toma el bloque de n pedidos consecutivos
 * [j*n, (j+1)*n) y se planifica en el instante en que llegó el último pedido del bloque.
 * Las instancias de un mismo tamaño no se solapan; los tamaños distintos sí (el de 25 está dentro del de 50).
 */
public final class GeneradorEscenarios {
    private GeneradorEscenarios() {}

    public static List<Escenario> generar(PlanExperimento plan, BaseOperativa base, DatosArchivos datos,
                                          List<String> advertencias) {
        if (datos.pedidos().size() < plan.pedidosNecesarios())
            throw new IllegalArgumentException("Se necesitan " + plan.pedidosNecesarios() + " pedidos desde "
                    + plan.desde() + " y solo hay " + datos.pedidos().size()
                    + ". Reduzca --instancias/--tamanios o adelante --desde.");
        List<Pedido> pedidos = crearPedidos(datos, base);
        validarMantenimiento(datos, base, advertencias);

        List<Escenario> escenarios = new ArrayList<>();
        for (int n : plan.tamanios()) {
            for (int j = 0; j < plan.instancias(); j++) {
                List<Pedido> bloque = pedidos.subList(j * n, (j + 1) * n);
                var instante = bloque.get(n - 1).getFechaLlegada();
                LocalDate dia = instante.toLocalDate();
                Set<String> enMantenimiento = plan.aplicarMantenimiento()
                        ? datos.mantenimiento().getOrDefault(dia, Set.of()) : Set.of();
                List<Vehiculo> flota = base.vehiculos().stream().map(v -> copiar(v, enMantenimiento)).toList();
                List<String> excluidos = flota.stream().filter(v -> enMantenimiento.contains(v.getIdVehiculo()))
                        .map(Vehiculo::getIdVehiculo).sorted().toList();
                var bloqueosActivos = datos.bloqueos().stream().filter(b -> b.activoEn(instante)).toList();
                escenarios.add(new Escenario(String.format("N%03d-I%02d", n, j + 1), n, j + 1, instante,
                        bloque, flota, excluidos, bloqueosActivos));
            }
        }
        return List.copyOf(escenarios);
    }

    /** Los archivos de ventas no traen id: se asigna la posición (1-based) en el orden de llegada leído. */
    private static List<Pedido> crearPedidos(DatosArchivos datos, BaseOperativa base) {
        List<Pedido> pedidos = new ArrayList<>();
        long id = 1;
        for (var p : datos.pedidos()) {
            if (p.x() > base.anchoKm() || p.y() > base.altoKm())
                throw new IllegalArgumentException("Pedido fuera de la ciudad: " + p.x() + "," + p.y());
            pedidos.add(new Pedido(id++, p.cliente(), p.cantidad(), p.prioridad(), new Nodo(p.x(), p.y()), p.llegada(), p.horas()));
        }
        return pedidos;
    }

    /** Copia la flota de la BD; el vehículo en mantenimiento ese día queda no disponible. */
    private static Vehiculo copiar(Vehiculo v, Set<String> enMantenimiento) {
        var copia = new Vehiculo(v.getIdVehiculo(), v.getCapacidadPaquetes(), v.getVelocidadKmh(), v.getCostoPorKm(), v.getPosicionActual());
        copia.setDisponible(v.isDisponible() && !enMantenimiento.contains(v.getIdVehiculo()));
        return copia;
    }

    private static void validarMantenimiento(DatosArchivos datos, BaseOperativa base, List<String> advertencias) {
        Set<String> conocidos = new HashSet<>();
        base.vehiculos().forEach(v -> conocidos.add(v.getIdVehiculo()));
        Set<String> desconocidos = new HashSet<>();
        datos.mantenimiento().values().forEach(ids -> ids.stream().filter(id -> !conocidos.contains(id)).forEach(desconocidos::add));
        if (!desconocidos.isEmpty())
            advertencias.add("Mantenimiento menciona vehículos que no existen en la BD: " + desconocidos.stream().sorted().toList());
    }
}