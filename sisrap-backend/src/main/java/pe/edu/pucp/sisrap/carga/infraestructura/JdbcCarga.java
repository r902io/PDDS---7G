package pe.edu.pucp.sisrap.carga.infraestructura;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import pe.edu.pucp.sisrap.carga.dominio.PedidoImportado;
import pe.edu.pucp.sisrap.carga.dominio.ReglasCarga;
import pe.edu.pucp.sisrap.carga.dominio.RepositorioCarga;
import pe.edu.pucp.sisrap.parametros.infraestructura.JdbcParametros;
import pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad;
public final class JdbcCarga implements RepositorioCarga {
    private final DataSource fuente;
    public JdbcCarga(DataSource fuente){this.fuente=fuente;}
    public ReglasCarga reglas(String perfil){
        try(var cn=fuente.getConnection()){
            cn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);cn.setAutoCommit(false);
            try{
                var c=JdbcParametros.leer(cn,perfil,false);
                Map<Integer,TipoPrioridad> prioridades=new HashMap<>();
                for(var p:TipoPrioridad.values()){
                    int horas=c.entero("prioridad."+p.name());
                    if(horas<=0 || prioridades.put(horas,p)!=null)throw new IllegalArgumentException("Plazos duplicados o inválidos");
                }
                try(var st=cn.createStatement();var rs=st.executeQuery("SELECT ancho_km,alto_km FROM ciudad")){
                    if(!rs.next())throw new IllegalArgumentException("Falta ciudad");
                    var reglas=new ReglasCarga(rs.getInt(1),rs.getInt(2),prioridades);
                    if(rs.next())throw new IllegalArgumentException("Ciudad ambigua");
                    cn.commit();return reglas;
                }
            }catch(Exception e){cn.rollback();throw e;}
        }catch(SQLException e){throw new IllegalStateException("No se pudo leer reglas de carga",e);}
    }
    public boolean guardar(String huella,int anio,int mes,List<PedidoImportado> pedidos){
        try(var cn=fuente.getConnection()){
            cn.setAutoCommit(false);
            try{
                try(var st=cn.prepareStatement("INSERT INTO carga_pedidos_archivo(huella,anio,mes,filas) VALUES(?,?,?,?)")){
                    st.setString(1,huella);st.setInt(2,anio);st.setInt(3,mes);st.setInt(4,pedidos.size());
                    try{st.executeUpdate();}catch(SQLException e){
                        if(e.getErrorCode()==1062){cn.rollback();return false;}throw e;
                    }
                }
                try(var st=cn.prepareStatement("INSERT INTO pedido(id_cliente,cantidad_qq,prioridad,horas_limite,fecha_llegada,ubicacion_x,ubicacion_y) VALUES(?,?,?,?,?,?,?)")){
                    for(var p:pedidos){
                        st.setString(1,p.cliente());st.setInt(2,p.cantidad());st.setString(3,p.prioridad().name());st.setInt(4,p.horas());
                        st.setTimestamp(5,Timestamp.valueOf(p.llegada()));st.setInt(6,p.x());st.setInt(7,p.y());st.addBatch();
                    }
                    st.executeBatch();
                }
                cn.commit();return true;
            }catch(Exception e){cn.rollback();throw e;}
        }catch(SQLException e){throw new IllegalStateException("No se pudo importar pedidos",e);}
    }
}
