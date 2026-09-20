package pe.edu.pucp.sisrap.configuracion;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import pe.edu.pucp.sisrap.carga.aplicacion.ImportarPedidos;
import pe.edu.pucp.sisrap.carga.infraestructura.JdbcCarga;
import pe.edu.pucp.sisrap.experimentacion.aplicacion.ConsultarExperimento;
import pe.edu.pucp.sisrap.experimentacion.aplicacion.EjecutarExperimento;
import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento;
import pe.edu.pucp.sisrap.experimentacion.dominio.RepositorioExperimento;
import pe.edu.pucp.sisrap.experimentacion.infraestructura.JdbcExperimento;
import pe.edu.pucp.sisrap.parametros.aplicacion.GestionarParametros;
import pe.edu.pucp.sisrap.parametros.infraestructura.JdbcParametros;
import tools.jackson.databind.json.JsonMapper;
@Configuration
@Profile("experimentacion")
public class ExperimentacionConfig {
    @Bean
    RepositorioExperimento repositorioExperimento(
            DataSource ds,
            JsonMapper mapper,
            @Value("${sisrap.experimento.max-pedidos}") int maxPedidos) {
        // Los DTO de evidencia son records: preservan también la instantánea completa.
        return new JdbcExperimento(ds,informe->{
            try{return mapper.writeValueAsString(informe);}catch(Exception e){throw new IllegalStateException("No se pudo serializar resultado",e);}
        },json->{
            try{return mapper.readValue(json,InformeExperimento.class);}catch(Exception e){throw new IllegalStateException("No se pudo leer resultado",e);}
        },maxPedidos);
    }
    @Bean EjecutarExperimento ejecutarExperimento(RepositorioExperimento r,
            @Value("${sisrap.experimento.max-repeticiones}") int rep,
            @Value("${sisrap.experimento.max-pedidos}") int pedidos,
            @Value("${sisrap.experimento.max-corridas}") int corridas){
        return new EjecutarExperimento(r,rep,pedidos,corridas);
    }
    @Bean ConsultarExperimento consultarExperimento(RepositorioExperimento r){return new ConsultarExperimento(r);}
    @Bean GestionarParametros gestionarParametros(DataSource ds){return new GestionarParametros(new JdbcParametros(ds));}
    @Bean ImportarPedidos importarPedidos(DataSource ds,@Value("${sisrap.carga.max-caracteres}") int limite){
        return new ImportarPedidos(new JdbcCarga(ds),limite);
    }
}
