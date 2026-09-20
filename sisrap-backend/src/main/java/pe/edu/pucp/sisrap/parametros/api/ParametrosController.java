package pe.edu.pucp.sisrap.parametros.api;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import pe.edu.pucp.sisrap.parametros.aplicacion.GestionarParametros;
import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
@RestController
@Profile("experimentacion")
@RequestMapping("/api/parametros")
public final class ParametrosController {
    private final GestionarParametros servicio;
    public ParametrosController(GestionarParametros servicio){this.servicio=servicio;}
    @GetMapping("/{perfil}") public Configuracion leer(@PathVariable String perfil){return servicio.consultar(perfil);}
    @PatchMapping("/{perfil}") public void actualizar(@PathVariable String perfil,@RequestBody Map<String,String> cambios){
        servicio.actualizar(perfil,cambios);
    }
    @PostMapping("/{origen}/copias/{destino}") public void copiar(@PathVariable String origen,@PathVariable String destino){
        servicio.copiar(origen,destino);
    }
}