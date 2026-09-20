package pe.edu.pucp.sisrap.carga.dominio;
import java.util.List;
public interface RepositorioCarga {
    ReglasCarga reglas(String perfil);
    boolean guardar(String huella,int anio,int mes,List<PedidoImportado> pedidos);
}
