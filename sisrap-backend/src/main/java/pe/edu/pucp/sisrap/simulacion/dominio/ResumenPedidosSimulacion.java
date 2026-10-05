package pe.edu.pucp.sisrap.simulacion.dominio;

public record ResumenPedidosSimulacion(
        int total,
        int futuros,
        int pendientes,
        int enRuta,
        int reasignados,
        int retrasados,
        int entregados) {
}
