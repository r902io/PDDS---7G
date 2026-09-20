package pe.edu.pucp.sisrap.parametros.infraestructura;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;

import javax.sql.DataSource;

import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
import pe.edu.pucp.sisrap.parametros.dominio.RepositorioParametros;
import pe.edu.pucp.sisrap.parametros.dominio.ValidarConfiguracion;
public final class JdbcParametros implements RepositorioParametros {
    private final DataSource fuente;
    public JdbcParametros(DataSource fuente){this.fuente=fuente;}
    public static Configuracion leer(Connection cn,String perfil,boolean bloquear)throws SQLException{
        Map<String,String> mapa=new TreeMap<>();
        try(var st=cn.prepareStatement("SELECT clave,valor FROM perfil_parametro WHERE perfil=? ORDER BY clave"+(bloquear?" FOR UPDATE":""))){
            st.setString(1,perfil);
            try(var rs=st.executeQuery()){while(rs.next())mapa.put(rs.getString(1),rs.getString(2));}
        }
        if(mapa.isEmpty())throw new IllegalArgumentException("Perfil inexistente: "+perfil);
        return new Configuracion(mapa);
    }
    public Configuracion leer(String perfil){
        try(var cn=fuente.getConnection()){return leer(cn,perfil,false);}
        catch(SQLException e){throw new IllegalStateException("No se pudo leer configuración",e);}
    }
    public void actualizar(String perfil,Map<String,String> valores){
        if(valores==null || valores.isEmpty())throw new IllegalArgumentException("Cambios vacíos");
        if(valores.entrySet().stream().anyMatch(e->e.getKey()==null || e.getValue()==null || e.getValue().length()>255))
            throw new IllegalArgumentException("Valores nulos o demasiado extensos");
        try(var cn=fuente.getConnection()){
            cn.setAutoCommit(false);
            try {
                var actual=leer(cn,perfil,true);
                if(!actual.valores().keySet().containsAll(valores.keySet()))throw new IllegalArgumentException("Clave de parámetro desconocida");
                Map<String,String> combinado=new TreeMap<>(actual.valores());combinado.putAll(valores);
                validar(new Configuracion(combinado));
                try(var st=cn.prepareStatement("UPDATE perfil_parametro SET valor=? WHERE perfil=? AND clave=?")){
                    for(var e:valores.entrySet()){
                        st.setString(1,e.getValue());st.setString(2,perfil);st.setString(3,e.getKey());st.addBatch();
                    }
                    st.executeBatch();
                }
                cn.commit();
            }catch(Exception e){cn.rollback();throw e;}
        }catch(SQLException e){throw new IllegalStateException("No se pudo actualizar configuración",e);}
    }
    public static void validar(Configuracion c){
        ValidarConfiguracion.ejecutar(c);
    }
    public void copiar(String origen,String destino){
        if(destino==null || !destino.matches("[A-Za-z0-9_-]{1,60}"))throw new IllegalArgumentException("Perfil inválido");
        try(var cn=fuente.getConnection();var st=cn.prepareStatement(
            "INSERT INTO perfil_parametro(perfil,clave,valor) SELECT ?,clave,valor FROM perfil_parametro WHERE perfil=?")){
            st.setString(1,destino);st.setString(2,origen);
            if(st.executeUpdate()==0)throw new IllegalArgumentException("Perfil origen inexistente");
        }catch(SQLException e){throw new IllegalStateException("No se pudo copiar perfil; verifique que el destino no exista",e);}
    }
}
