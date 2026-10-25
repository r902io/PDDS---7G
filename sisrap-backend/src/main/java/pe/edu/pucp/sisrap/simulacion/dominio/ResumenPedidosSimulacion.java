package pe.edu.pucp.sisrap.simulacion.dominio;

public record ResumenPedidosSimulacion(
        int total,
        int futuros,
        int pendientes,
        int enRuta,
        int reasignados,
        int retrasados,
        int entregados,
        int retrasadosSinEntregar,
        int retrasadosEntregados,
        int retrasadosEnRuta, int arribadosEnPlazo, int sinArriboPlazoVigente) {
    public ResumenPedidosSimulacion(int total,int futuros,int pendientes,int enRuta,int reasignados,
            int retrasados,int entregados,int retrasadosSinEntregar,int retrasadosEntregados,int retrasadosEnRuta) {
        this(total,futuros,pendientes,enRuta,reasignados,retrasados,entregados,retrasadosSinEntregar,
                retrasadosEntregados,retrasadosEnRuta,Math.max(0,entregados-retrasadosEntregados),
                Math.max(0,total-futuros-retrasados-Math.max(0,entregados-retrasadosEntregados)));
    }
}
