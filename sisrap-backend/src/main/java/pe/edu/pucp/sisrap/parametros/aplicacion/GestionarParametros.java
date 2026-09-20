package pe.edu.pucp.sisrap.parametros.aplicacion;
import java.util.Map;
import pe.edu.pucp.sisrap.parametros.dominio.*;
public final class GestionarParametros {
    private final RepositorioParametros repositorio;
    public GestionarParametros(RepositorioParametros r){repositorio=r;}
    public Configuracion consultar(String perfil){return repositorio.leer(perfil);}
    public void actualizar(String perfil,Map<String,String> cambios){repositorio.actualizar(perfil,cambios);}
    public void copiar(String origen,String destino){repositorio.copiar(origen,destino);}
}

