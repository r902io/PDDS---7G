package pe.edu.pucp.sisrap.carga.dominio;
import java.util.Map;
import pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad;
public record ReglasCarga(int ancho,int alto,Map<Integer,TipoPrioridad> prioridades) {
    public ReglasCarga { prioridades=Map.copyOf(prioridades); }
}
