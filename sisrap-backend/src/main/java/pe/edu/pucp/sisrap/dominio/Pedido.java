package pe.edu.pucp.sisrap.dominio;

public class Pedido {
    private final Long idPedido;
    private final String idCliente;
    private final int cantidadQq;
    private final TipoPrioridad prioridad;
    private final Nodo ubicacion;

    public Pedido(Long idPedido, String idCliente, int cantidadQq,
                  TipoPrioridad prioridad, Nodo ubicacion) {
        this.idPedido = idPedido;
        this.idCliente = idCliente;
        this.cantidadQq = cantidadQq;
        this.prioridad = prioridad;
        this.ubicacion = ubicacion;
    }

    public Long getIdPedido() { return idPedido; }
    public String getIdCliente() { return idCliente; }
    public int getCantidadQq() { return cantidadQq; }
    public TipoPrioridad getPrioridad() { return prioridad; }
    public Nodo getUbicacion() { return ubicacion; }
}
