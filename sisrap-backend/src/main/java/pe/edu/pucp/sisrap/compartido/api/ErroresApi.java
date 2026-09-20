package pe.edu.pucp.sisrap.compartido.api;
import java.time.DateTimeException;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
@RestControllerAdvice
@Profile("experimentacion")
public final class ErroresApi {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ErroresApi.class);
    @ExceptionHandler({IllegalArgumentException.class,DateTimeException.class})
    public ResponseEntity<Map<String,String>> invalido(RuntimeException e){
        return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String,String>> fallo(IllegalStateException e){
        LOG.error("Error de operación experimental",e);
        return ResponseEntity.internalServerError().body(Map.of("error","No se pudo completar la operación. Revise los registros del servidor."));
    }
}