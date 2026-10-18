package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.LocalDateTime;
import java.util.List;

/** Recorrido efectivamente simulado, distinto de la distancia de los planes guardados. */
public record IndicadoresOperacion(
        double distanciaRecorridaKm,
        double costoTransporteAcumuladoSoles,
        List<UtilizacionTipo> utilizacionPorTipo,
        List<MuestraTemporal> evolucion) {

    public IndicadoresOperacion {
        utilizacionPorTipo = utilizacionPorTipo == null ? List.of() : List.copyOf(utilizacionPorTipo);
        evolucion = evolucion == null ? List.of() : List.copyOf(evolucion);
    }

    public static IndicadoresOperacion vacios() {
        return new IndicadoresOperacion(0, 0, List.of(), List.of());
    }

    public record UtilizacionTipo(String tipo, int cantidadTotal, int activos,
            double porcentajeOcupacionActual, double porcentajeUtilizacionAcumulada) { }

    public record MuestraTemporal(LocalDateTime instante, double distanciaRecorridaKm,
            double costoTransporteAcumuladoSoles, int entregados, int retrasados,
            double porcentajeLlegadasEnPlazo) { }
}
