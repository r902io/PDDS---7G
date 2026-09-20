package pe.edu.pucp.sisrap.experimentacion.api;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.pucp.sisrap.experimentacion.dominio.*;
import pe.edu.pucp.sisrap.experimentacion.aplicacion.*;
@RestController
@Profile("experimentacion")
@RequestMapping("/api/experimentos")
public final class ExperimentoController {
    private final EjecutarExperimento ejecutar;
    private final ConsultarExperimento consultar;
    private final AtomicBoolean ocupado=new AtomicBoolean();
    public ExperimentoController(EjecutarExperimento e,ConsultarExperimento c){ejecutar=e;consultar=c;}
    @PostMapping public InformeExperimento ejecutar(@RequestBody SolicitudExperimento solicitud){
        if(!ocupado.compareAndSet(false,true))throw new ResponseStatusException(HttpStatus.CONFLICT,"Ya hay un experimento ejecutándose");
        try{return ejecutar.ejecutar(solicitud);}finally{ocupado.set(false);}
    }
    @GetMapping("/{id}") public InformeExperimento obtener(@PathVariable String id){return consultar.obtener(id);}
    @GetMapping(value="/{id}/csv",produces="text/csv") public ResponseEntity<String> csv(@PathVariable String id){
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=experimento.csv").body(consultar.csv(id));
    }
}
