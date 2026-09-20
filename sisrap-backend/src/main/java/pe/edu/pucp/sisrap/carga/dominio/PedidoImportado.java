package pe.edu.pucp.sisrap.carga.dominio;
import java.time.LocalDateTime;
import pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad;
public record PedidoImportado(String cliente,int cantidad,TipoPrioridad prioridad,int horas,
                              LocalDateTime llegada,int x,int y) {}
