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
    private final int horasLimite;
    public Pedido(Long id, String cliente, int cantidad, TipoPrioridad prioridad, Nodo ubicacion,
                  LocalDateTime fechaLlegada, int horasLimite) {
        if(id==null || cantidad<=0 || horasLimite<=0 || fechaLlegada==null || ubicacion==null || prioridad==null)
            throw new IllegalArgumentException("Pedido inválido");
        this.idPedido=id; this.idCliente=cliente; this.cantidadQq=cantidad; this.prioridad=prioridad;
        this.ubicacion=ubicacion; this.fechaLlegada=fechaLlegada; this.horasLimite=horasLimite;
    }
    public Long getIdPedido(){return idPedido;}
    public String getIdCliente(){return idCliente;}
    public int getCantidadQq(){return cantidadQq;}
    public TipoPrioridad getPrioridad(){return prioridad;}
    public Nodo getUbicacion(){return ubicacion;}
    public LocalDateTime getFechaLlegada(){return fechaLlegada;}
    public int getHorasLimite(){return horasLimite;}
    public LocalDateTime getFechaLimite(){return fechaLlegada.plusHours(horasLimite);}
}
