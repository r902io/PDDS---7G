package pe.edu.pucp.sisrap.parametros.dominio;
import java.util.Map;
public interface RepositorioParametros {
    Configuracion leer(String perfil);
    void actualizar(String perfil,Map<String,String> valores);
    void copiar(String origen,String destino);
}


