package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.LocalDateTime;
import java.util.List;

public record ResultadoCorrida(
        long idSimulacion, String escenario, String estado,
        LocalDateTime fechaInicio, LocalDateTime fechaFin, long tiempoRealMs,
        int totalPedidos, int entregados, int retrasados,
        double costoPlanificado, double distanciaPlanificadaKm,
        List<PedidoRegistrado> pedidos,
        List<IncidenciaRegistrada> incidencias,
        List<MantenimientoRegistrado> mantenimientos,
        List<VehiculoInicial> flotaInicial,
        List<AlmacenInicial> almacenesIniciales,
        List<FraccionRegistrada> fracciones,
        Long idPedidoColapso,
        LocalDateTime instanteColapso) {

    /** Parámetros efectivamente usados al iniciar ESTA ejecución. */
    public record VehiculoInicial(String idVehiculo, String tipo, int capacidadPaquetes,
                                 double velocidadKmh, double costoPorKm) { }

    public record AlmacenInicial(String idAlmacen, String nombre, String tipo,
                                int x, int y, Integer capacidadMaxima) { }

    public record FraccionRegistrada(
            long idPedido, long idFraccion, int cantidad, String idVehiculo,
            String estado, LocalDateTime fechaArribo, LocalDateTime fechaEntrega) { }

    public record PedidoRegistrado(
            long idPedido, String cliente, int cantidad, String prioridad,
            LocalDateTime fechaRegistro, LocalDateTime fechaArribo, LocalDateTime fechaEntrega,
            String estado, int x, int y) { }

    public record IncidenciaRegistrada(
            long idIncidencia, String tipo, LocalDateTime fechaOcurrencia,
            boolean activa, LocalDateTime inicioBloqueo, LocalDateTime finBloqueo,
            String idVehiculo, String tipoAveria, Integer x, Integer y,
            LocalDateTime retornoEstimado, List<Vertice> vertices) { }

    public record Vertice(int orden, int x, int y) { }

    public record MantenimientoRegistrado(
            long idMantenimiento, String idVehiculo, String tipo,
            LocalDateTime inicio, LocalDateTime fin, boolean activo) { }
}
