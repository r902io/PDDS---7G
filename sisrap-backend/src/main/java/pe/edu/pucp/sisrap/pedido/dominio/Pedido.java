package pe.edu.pucp.sisrap.pedido.dominio;

import java.time.LocalDateTime;

import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

public final class Pedido {
    private final Long idPedido;
    private final String idCliente;
    private final int cantidadQq;
    private final TipoPrioridad prioridad;
    private final Nodo ubicacion;
    private final LocalDateTime fechaLlegada;
    private final LocalDateTime fechaDisponiblePlanificacion;
    private final int horasLimite;

    public Pedido(Long id,
                  String cliente,
                  int cantidad,
                  TipoPrioridad prioridad,
                  Nodo ubicacion,
                  LocalDateTime fechaLlegada,
                  int horasLimite) {
        this(id, cliente, cantidad, prioridad, ubicacion,
                fechaLlegada, fechaLlegada, horasLimite);
    }

    private Pedido(Long id,
                   String cliente,
                   int cantidad,
                   TipoPrioridad prioridad,
                   Nodo ubicacion,
                   LocalDateTime fechaLlegada,
                   LocalDateTime fechaDisponiblePlanificacion,
                   int horasLimite) {
        if (id == null || cantidad <= 0 || horasLimite <= 0
                || fechaLlegada == null || fechaDisponiblePlanificacion == null
                || ubicacion == null || prioridad == null) {
            throw new IllegalArgumentException("Pedido inválido");
        }
        if (fechaDisponiblePlanificacion.isAfter(fechaLlegada)) {
            throw new IllegalArgumentException(
                    "La disponibilidad de planificación no puede ser posterior a la llegada del pedido");
        }

        this.idPedido = id;
        this.idCliente = cliente;
        this.cantidadQq = cantidad;
        this.prioridad = prioridad;
        this.ubicacion = ubicacion;
        this.fechaLlegada = fechaLlegada;
        this.fechaDisponiblePlanificacion = fechaDisponiblePlanificacion;
        this.horasLimite = horasLimite;
    }

    /**
     * Crea una vista batch del pedido: el planificador lo conoce desde
     * {@code disponibleDesde}, pero se conserva la llegada real y por tanto el
     * deadline real del pedido.
     */
    public Pedido disponibleDesde(LocalDateTime disponibleDesde) {
        return new Pedido(
                idPedido,
                idCliente,
                cantidadQq,
                prioridad,
                ubicacion,
                fechaLlegada,
                disponibleDesde,
                horasLimite);
    }

    public Long getIdPedido() { return idPedido; }
    public String getIdCliente() { return idCliente; }
    public int getCantidadQq() { return cantidadQq; }
    public TipoPrioridad getPrioridad() { return prioridad; }
    public Nodo getUbicacion() { return ubicacion; }
    public LocalDateTime getFechaLlegada() { return fechaLlegada; }
    public LocalDateTime getFechaDisponiblePlanificacion() { return fechaDisponiblePlanificacion; }
    public int getHorasLimite() { return horasLimite; }
    public LocalDateTime getFechaLimite() { return fechaLlegada.plusHours(horasLimite); }
}