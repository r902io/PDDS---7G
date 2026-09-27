package pe.edu.pucp.sisrap.experimentacion.dominio;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;

public record Escenario(String id,
                        EscenarioOperativo escenarioOperativo,
                        PerfilPresion perfilPresion,
                        int tamanio,
                        int instancia,
                        LocalDateTime instante,
                        List<Pedido> pedidos,
                        List<Vehiculo> vehiculos,
                        List<String> vehiculosEnMantenimiento,
                        List<String> vehiculosEnBajaPorPerfil,
                        List<Bloqueo> bloqueosProgramados) {

    public Escenario {
        if (instancia < 1) throw new IllegalArgumentException("La instancia debe ser >= 1");
        if (tamanio < 1) throw new IllegalArgumentException("El tamaño debe ser >= 1");
        if (instante == null) throw new IllegalArgumentException("Falta instante del escenario");
        pedidos = List.copyOf(pedidos);
        vehiculos = List.copyOf(vehiculos);
        vehiculosEnMantenimiento = List.copyOf(vehiculosEnMantenimiento);
        vehiculosEnBajaPorPerfil = List.copyOf(vehiculosEnBajaPorPerfil);
        bloqueosProgramados = List.copyOf(bloqueosProgramados);
    }

    /**
     * Contexto estático usado por el planificador actual.
     *
     * Los archivos contienen bloqueos con intervalo de inicio y fin. Como el contexto
     * estático representa un instante concreto, solamente se incorporan los bloqueos
     * vigentes en dicho instante. Los demás quedan registrados como bloqueos programados
     * de la instancia y conservan sus fechas para una simulación temporal posterior.
     */
    public ContextoPlanificacion contexto(BaseOperativa base, ReglasPlanificacion reglas) {
        List<Bloqueo> activos = bloqueosActivosEnInstante();
        return new ContextoPlanificacion(
                pedidos,
                vehiculos,
                base.almacenes(),
                instante,
                reglas,
                expandirNodosBloqueados(activos));
    }

    public List<Bloqueo> bloqueosActivosEnInstante() {
        return bloqueosProgramados.stream()
                .filter(b -> b.activoEn(instante))
                .toList();
    }

    /** Expande los segmentos de cada poligonal a todos los nodos de la retícula bloqueados. */
    public static Set<String> expandirNodosBloqueados(List<Bloqueo> bloqueos) {
        Set<String> salida = new HashSet<>();

        for (Bloqueo bloqueo : bloqueos) {
            List<pe.edu.pucp.sisrap.geografia.dominio.Nodo> vertices = bloqueo.vertices();

            for (int i = 0; i < vertices.size() - 1; i++) {
                var a = vertices.get(i);
                var b = vertices.get(i + 1);

                int x = a.getX();
                int y = a.getY();
                salida.add(x + "," + y);

                if (x == b.getX()) {
                    while (y != b.getY()) {
                        y += Integer.compare(b.getY(), y);
                        salida.add(x + "," + y);
                    }
                } else {
                    while (x != b.getX()) {
                        x += Integer.compare(b.getX(), x);
                        salida.add(x + "," + y);
                    }
                }
            }
        }

        return Set.copyOf(salida);
    }

    public int cantidadTotalQq() {
        return pedidos.stream().mapToInt(Pedido::getCantidadQq).sum();
    }

    public int capacidadDisponibleQq() {
        return vehiculos.stream()
                .filter(Vehiculo::isDisponible)
                .mapToInt(Vehiculo::getCapacidadPaquetes)
                .sum();
    }
}