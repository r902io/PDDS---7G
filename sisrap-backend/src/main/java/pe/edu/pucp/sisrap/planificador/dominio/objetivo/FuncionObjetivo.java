package pe.edu.pucp.sisrap.planificador.dominio.objetivo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;

/*
  F(S) = T(S) + beta1*R(S) + beta2*N(S) + beta3*V(S).
 
  T: tiempo total de atención.
  R: retraso acumulado respecto del deadline.
  N: pedidos no atendidos.
  V: cantidad de violaciones de restricciones duras modeladas por PaqRap.
 */
public final class FuncionObjetivo {
    private final double beta1;
    private final double beta2;
    private final double beta3;
    private ContextoPlanificacion contexto;

    public FuncionObjetivo(Configuracion c, ContextoPlanificacion contexto) {
        beta1 = c.numero("objetivo.beta1");
        beta2 = c.numero("objetivo.beta2");
        beta3 = c.numero("objetivo.beta3");
        if (beta1 <= 0 || beta2 <= 0 || beta3 <= 0) {
            throw new IllegalArgumentException("Los pesos de la función objetivo deben ser positivos");
        }
        this.contexto = java.util.Objects.requireNonNull(contexto, "Falta contexto de planificación");
    }

    /** Actualiza el contexto antes de una replanificación para evaluar disponibilidad, bloqueos y stock vigentes. */
    public void actualizarContexto(ContextoPlanificacion contexto) {
        this.contexto = java.util.Objects.requireNonNull(contexto, "Falta contexto de planificación");
    }

    public double calcular(Solucion s) {
        double t = 0.0;
        double r = 0.0;
        double v = 0.0;
        double costo = 0.0;

        Set<Long> universoPedidos = new HashSet<>();
        contexto.getPedidos().forEach(p -> universoPedidos.add(p.getIdPedido()));

        Map<String, Vehiculo> vehiculosContexto = new HashMap<>();
        contexto.getVehiculos().forEach(x -> vehiculosContexto.put(x.getIdVehiculo(), x));

        Map<String, Almacen> almacenesContexto = new HashMap<>();
        contexto.getAlmacenes().forEach(x -> almacenesContexto.put(x.getIdAlmacen(), x));

        Set<Long> pedidosVistos = new HashSet<>();
        Set<String> vehiculosUsados = new HashSet<>();
        Map<String, Integer> consumoPorAlmacen = new HashMap<>();
        Set<String> bloqueados = contexto.nodosBloqueados();

        for (var ruta : s.getRutas()) {
            ruta.setNodosBloqueados(bloqueados);
            ruta.recalcular();
            costo += ruta.getCostoTotal();

            if (ruta.isIntransitable() || ruta.isFueraDeTurno()) {
                v++;
            }

            if (ruta.getSecuenciaPedidos().isEmpty()) continue;

            String idVehiculo = ruta.getVehiculo().getIdVehiculo();
            Vehiculo vehiculo = vehiculosContexto.get(idVehiculo);

            // Restricción dura: cada unidad de transporte se usa como máximo en una ruta simultánea.
            if (!vehiculosUsados.add(idVehiculo)) v++;

            // Restricción dura: el vehículo debe pertenecer al contexto y estar disponible.
            if (vehiculo == null) {
                v++;
            } else {
                if (!vehiculo.isDisponible()) v++;
                if (ruta.cargaTotal() > vehiculo.getCapacidadPaquetes()) v++;
            }

            String idAlmacen = ruta.getAlmacenOrigen().getIdAlmacen();
            Almacen almacen = almacenesContexto.get(idAlmacen);

            // Restricción dura: la ruta debe salir de un almacén válido del contexto.
            if (almacen == null) {
                v++;
            } else {
                consumoPorAlmacen.merge(idAlmacen, ruta.cargaTotal(), Integer::sum);
            }

            for (int i = 0; i < ruta.getSecuenciaPedidos().size(); i++) {
                var pedido = ruta.getSecuenciaPedidos().get(i);

                // Restricción dura: no se aceptan pedidos ajenos al contexto ni duplicados.
                if (!universoPedidos.contains(pedido.getIdPedido())) v++;
                if (!pedidosVistos.add(pedido.getIdPedido())) v++;

                // Restricción dura: no se puede realizar una entrega sobre un nodo bloqueado.
                String nodo = pedido.getUbicacion().getX() + "," + pedido.getUbicacion().getY();
                if (bloqueados.contains(nodo)) v++;

                t += ruta.tiempoAtencionDe(i);
                r += ruta.retrasoDe(i);
            }
        }

        // Restricción dura: no exceder el stock disponible de los almacenes intermedios.
        // El almacén central se representa con capacidadMaxima == null y se considera ilimitado.
        for (var almacen : contexto.getAlmacenes()) {
            if (almacen.getCapacidadMaxima() == null) continue;

            Integer stock = almacen.getStockActual();
            int consumo = consumoPorAlmacen.getOrDefault(almacen.getIdAlmacen(), 0);
            if (stock == null || stock < 0 || stock > almacen.getCapacidadMaxima() || consumo > stock) v++;
        }

        var faltantes = contexto.getPedidos().stream()
                .filter(p -> !pedidosVistos.contains(p.getIdPedido()))
                .toList();

        s.setPedidosNoAsignados(new java.util.ArrayList<>(faltantes));
        int n = faltantes.size();

        double f = t + beta1 * r + beta2 * n + beta3 * v;

        s.setValorT(t);
        s.setValorR(r);
        s.setValorN(n);
        s.setValorV(v);
        s.setCostoTransporte(costo);
        s.setValorFuncionObjetivo(f);
        s.setEsFactible(v == 0.0);
        return f;
    }
}