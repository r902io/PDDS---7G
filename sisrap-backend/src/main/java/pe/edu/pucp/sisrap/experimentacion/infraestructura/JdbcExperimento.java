package pe.edu.pucp.sisrap.experimentacion.infraestructura;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import javax.sql.DataSource;

import pe.edu.pucp.sisrap.experimentacion.dominio.BaseOperativa;
import pe.edu.pucp.sisrap.experimentacion.dominio.DatosExperimento;
import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento;
import pe.edu.pucp.sisrap.experimentacion.dominio.RepositorioExperimento;
import pe.edu.pucp.sisrap.experimentacion.dominio.SolicitudExperimento;
import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
import pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
public final class JdbcExperimento implements RepositorioExperimento {
    private final DataSource fuente;
    private final Function<InformeExperimento,String> serializar;
    private final Function<String,InformeExperimento> deserializar;
    private final int maxPedidos;
    public JdbcExperimento(DataSource fuente,Function<InformeExperimento,String> serializar,Function<String,InformeExperimento> deserializar,int maxPedidos){
        this.fuente=fuente;this.serializar=serializar;this.deserializar=deserializar;this.maxPedidos=maxPedidos;
    }
    public DatosExperimento cargar(SolicitudExperimento solicitud){
        try(var cn=fuente.getConnection()){
            cn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);cn.setAutoCommit(false);
            try{
                BaseOperativa base=JdbcBaseOperativa.leer(cn,solicitud.perfil());
                Configuracion cfg=base.configuracion();
                int limite=Arrays.stream(cfg.texto("experimento.tamanios").split(",")).map(String::trim).mapToInt(Integer::parseInt).max().orElseThrow();
                if(limite>maxPedidos)throw new IllegalArgumentException("Tamaño supera límite del servidor");
                List<Pedido> pedidos=new ArrayList<>();
                try(var st=cn.prepareStatement("SELECT * FROM pedido WHERE fecha_llegada>=? AND fecha_llegada<=? ORDER BY fecha_llegada,id_pedido LIMIT ?")){
                    st.setTimestamp(1,Timestamp.valueOf(solicitud.desde()));st.setTimestamp(2,Timestamp.valueOf(solicitud.instante()));st.setInt(3,limite);
                    try(var rs=st.executeQuery()){while(rs.next())pedidos.add(new Pedido(rs.getLong("id_pedido"),rs.getString("id_cliente"),
                        rs.getInt("cantidad_qq"),TipoPrioridad.valueOf(rs.getString("prioridad")),
                        JdbcBaseOperativa.nodo(rs.getInt("ubicacion_x"),rs.getInt("ubicacion_y"),base.anchoKm(),base.altoKm()),
                        rs.getTimestamp("fecha_llegada").toLocalDateTime(),rs.getInt("horas_limite")));}
                }
                var resultado=new DatosExperimento(cfg,new ContextoPlanificacion(pedidos,base.vehiculos(),base.almacenes(),
                    solicitud.instante(),ReglasPlanificacion.desde(cfg)),base.anchoKm(),base.altoKm());
                cn.commit();return resultado;
            }catch(Exception e){cn.rollback();throw e;}
        }catch(SQLException e){throw new IllegalStateException("No se pudo cargar la instantánea experimental",e);}
    }
    public void guardar(InformeExperimento informe){
        String json=serializar.apply(informe);
        try(var cn=fuente.getConnection();var st=cn.prepareStatement(
            "INSERT INTO experimento_numerico(id,perfil,fase,modelo,informe_json) VALUES(?,?,?,?,?)")){
            st.setString(1,informe.id());st.setString(2,informe.solicitud().perfil());st.setString(3,informe.solicitud().fase());
            st.setString(4,informe.modelo());st.setString(5,json);st.executeUpdate();
        }catch(SQLException e){throw new IllegalStateException("No se pudo guardar el experimento",e);}
    }
    public InformeExperimento obtener(String id){
        UUID.fromString(id);
        try(var cn=fuente.getConnection();var st=cn.prepareStatement("SELECT informe_json FROM experimento_numerico WHERE id=?")){
            st.setString(1,id);
            try(var rs=st.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Experimento inexistente");return deserializar.apply(rs.getString(1));}
        }catch(SQLException e){throw new IllegalStateException("No se pudo recuperar el experimento",e);}
    }
}