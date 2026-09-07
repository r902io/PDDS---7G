package pe.edu.pucp.sisrap.planificador.objetivo;

import pe.edu.pucp.sisrap.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.modelo.Ruta;
import pe.edu.pucp.sisrap.planificador.modelo.Solucion;

/**
 * Min F(S) = Ctransporte(S) + lambda1.R(S) + lambda2.U(S) + lambda3.V(S)
 */
public class FuncionObjetivo {
    private final double lambda1;
    private final double lambda2;
    private final double lambda3;

    /** Penalización fija por cada pedido no asignado a ninguna ruta (violación dura). */
    private static final double PENALIZACION_NO_ASIGNADO = 100_000.0;
    /** Penalización fija por cada unidad de capacidad excedida en una ruta. */
    private static final double PENALIZACION_SOBRECAPACIDAD = 50_000.0;
    /** Factor multiplicador de U(S) respecto al retraso base de un pedido priorizado. */
    private static final double FACTOR_PRIORIDAD = 3.0;

    public FuncionObjetivo(double lambda1, double lambda2, double lambda3) {
        this.lambda1 = lambda1;
        this.lambda2 = lambda2;
        this.lambda3 = lambda3;
    }

    public double calcular(Solucion solucion) {
        double ctransporte = 0;
        double r = 0;
        double u = 0;
        double v = 0;

        for (Ruta ruta : solucion.getRutas()) {
            ruta.recalcular();
            ctransporte += ruta.getCostoTotal();

            // Sobrecapacidad de vehículo -> violación dura
            int exceso = ruta.cargaTotal() - ruta.getVehiculo().getCapacidadPaquetes();
            if (exceso > 0) {
                v += exceso * PENALIZACION_SOBRECAPACIDAD;
            }

            // Vehículo no disponible pero con ruta asignada -> violación dura
            if (!ruta.getVehiculo().isDisponible() && !ruta.getSecuenciaPedidos().isEmpty()) {
                v += PENALIZACION_SOBRECAPACIDAD;
            }

            for (int i = 0; i < ruta.getSecuenciaPedidos().size(); i++) {
                Pedido pedido = ruta.getSecuenciaPedidos().get(i);
                double horaEntrega = ruta.horaEntregaDe(i);
                double horasLimite = pedido.getPrioridad().getHorasLimite();
                double retraso = Math.max(0.0, horaEntrega - horasLimite);

                r += retraso;
                if (retraso > 0 && pedido.getPrioridad().esPriorizado()) {
                    u += retraso * FACTOR_PRIORIDAD;
                }
            }
        }

        // Pedidos que ninguna ruta pudo atender -> violación dura
        v += solucion.getPedidosNoAsignados().size() * PENALIZACION_NO_ASIGNADO;

        double valor = ctransporte + lambda1 * r + lambda2 * u + lambda3 * v;

        solucion.setCostoTransporte(ctransporte);
        solucion.setValorR(r);
        solucion.setValorU(u);
        solucion.setValorV(v);
        solucion.setValorFuncionObjetivo(valor);
        solucion.setEsFactible(v == 0.0);

        return valor;
    }
}
