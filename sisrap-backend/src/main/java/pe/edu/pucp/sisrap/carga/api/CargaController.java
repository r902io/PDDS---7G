package pe.edu.pucp.sisrap.carga.api;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import pe.edu.pucp.sisrap.carga.aplicacion.ImportarPedidos;
@RestController
@Profile("experimentacion")
@RequestMapping("/api/carga")
public final class CargaController {
    private final ImportarPedidos importar;
    public CargaController(ImportarPedidos importar){this.importar=importar;}
    @PostMapping(value="/pedidos",consumes="text/plain")
    public ImportarPedidos.Resultado pedidos(@RequestParam String perfil,@RequestParam int anio,
            @RequestParam int mes,@RequestBody String contenido){
        return importar.ejecutar(perfil,anio,mes,contenido);
    }
}

