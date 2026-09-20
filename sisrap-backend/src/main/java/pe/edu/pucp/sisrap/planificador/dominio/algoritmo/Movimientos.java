package pe.edu.pucp.sisrap.planificador.dominio.algoritmo;
import java.util.*;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.*;
import pe.edu.pucp.sisrap.planificador.dominio.construccion.ConstructorVoraz;
public final class Movimientos {
    private final Random random;
    private final List<String> habilitados;
    public Movimientos(Random random,List<String> habilitados){this.random=random;this.habilitados=habilitados;}
    public Solucion vecino(Solucion base){
        Solucion s=base.copiar();
        switch(habilitados.get(random.nextInt(habilitados.size()))){
            case "DOS_OPT" -> aplicarDosOpt(s);
            case "SWAP" -> aplicarSwap(s);
            case "RELOCATE" -> aplicarRelocate(s);
            case "CROSS_ROUTE" -> aplicarCrossRouteRelocate(s);
            case "VEHICLE_CHANGE" -> aplicarVehicleChange(s);
            default -> throw new IllegalArgumentException("Movimiento inválido");
        }
        var pendientes=new ArrayList<>(s.getPedidosNoAsignados());
        s.getPedidosNoAsignados().clear();
        ConstructorVoraz.insertarPedidos(s.getRutas(),pendientes,s.getPedidosNoAsignados());
        s.getRutas().forEach(Ruta::recalcular);
        return s;
    }
    // 2-opt: invierte un segmento dentro de una misma ruta.
    private void aplicarDosOpt(Solucion solucion) {
        Ruta ruta = rutaConPedidosAlAzar(solucion, 2);
        if (ruta == null) return;

        List<Pedido> secuencia = ruta.getSecuenciaPedidos();
        int i = random.nextInt(secuencia.size());
        int j = random.nextInt(secuencia.size());
        if (i > j) { int tmp = i; i = j; j = tmp; }
        Collections.reverse(secuencia.subList(i, j + 1));
    }

    // Swap: intercambia dos pedidos dentro de la misma ruta.
    private void aplicarSwap(Solucion solucion) {
        Ruta ruta = rutaConPedidosAlAzar(solucion, 2);
        if (ruta == null) return;

        List<Pedido> secuencia = ruta.getSecuenciaPedidos();
        int i = random.nextInt(secuencia.size());
        int j = random.nextInt(secuencia.size());
        Collections.swap(secuencia, i, j);
    }

    //Relocate: retira un pedido y lo reinserta en otra posición de la misma ruta.
    private void aplicarRelocate(Solucion solucion) {
        Ruta ruta = rutaConPedidosAlAzar(solucion, 2);
        if (ruta == null) return;

        List<Pedido> secuencia = ruta.getSecuenciaPedidos();
        int origen = random.nextInt(secuencia.size());
        Pedido pedido = secuencia.remove(origen);
        int destino = random.nextInt(secuencia.size() + 1);
        secuencia.add(destino, pedido);
    }

    // Cross-route relocate: mueve un pedido de una ruta a otra con capacidad disponible.
    private void aplicarCrossRouteRelocate(Solucion solucion) {
        List<Ruta> conPedidos = solucion.getRutas().stream()
                .filter(r -> !r.getSecuenciaPedidos().isEmpty())
                .toList();
        if (conPedidos.isEmpty()) return;

        Ruta rutaOrigen = conPedidos.get(random.nextInt(conPedidos.size()));
        int idx = random.nextInt(rutaOrigen.getSecuenciaPedidos().size());
        Pedido pedido = rutaOrigen.getSecuenciaPedidos().get(idx);

        List<Ruta> destinosValidos = solucion.getRutas().stream()
                .filter(r -> r != rutaOrigen && r.getVehiculo().isDisponible())
                .filter(r -> r.cargaTotal() + pedido.getCantidadQq()
                        <= r.getVehiculo().getCapacidadPaquetes())
                .toList();
        if (destinosValidos.isEmpty()) return;

        Ruta rutaDestino = destinosValidos.get(random.nextInt(destinosValidos.size()));
        rutaOrigen.getSecuenciaPedidos().remove(idx);
        rutaDestino.getSecuenciaPedidos().add(pedido);
    }

    // Vehicle change: intercambia completamente el vehículo asignado a una ruta por otro disponible con capacidad suficiente para la carga actual.
    private void aplicarVehicleChange(Solucion solucion) {
        List<Ruta> conPedidos = solucion.getRutas().stream()
                .filter(r -> !r.getSecuenciaPedidos().isEmpty())
                .toList();
        if (conPedidos.isEmpty()) return;

        Ruta ruta = conPedidos.get(random.nextInt(conPedidos.size()));
        List<Ruta> candidatas = solucion.getRutas().stream()
                .filter(r -> r != ruta)
                .filter(r -> r.getVehiculo().isDisponible())
                .filter(r -> r.cargaTotal() + ruta.cargaTotal() <= r.getVehiculo().getCapacidadPaquetes())
                .toList();
        if (candidatas.isEmpty()) return;

        Ruta candidata = candidatas.get(random.nextInt(candidatas.size()));
        List<Pedido> temp = new ArrayList<>(ruta.getSecuenciaPedidos());
        ruta.getSecuenciaPedidos().clear();
        candidata.getSecuenciaPedidos().addAll(temp);
    }

    private Ruta rutaConPedidosAlAzar(Solucion solucion, int minimoPedidos) {
        List<Ruta> candidatas = solucion.getRutas().stream()
                .filter(r -> r.getSecuenciaPedidos().size() >= minimoPedidos)
                .toList();
        if (candidatas.isEmpty()) return null;
        return candidatas.get(random.nextInt(candidatas.size()));
    }


}
