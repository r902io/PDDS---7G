package pe.edu.pucp.sisrap.compartido.api;

import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
@Profile("!experimentacion")
public final class ErroresOperativosApi {
    private static final Logger LOG = LoggerFactory.getLogger(ErroresOperativosApi.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> http(ResponseStatusException e) {
        String descripcion = e.getReason() == null ? "La operación no pudo completarse" : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", descripcion));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, String>> peticionInvalida(Exception e) {
        String descripcion = e instanceof IllegalArgumentException && e.getMessage() != null
                ? e.getMessage() : "Los datos de la solicitud no son válidos";
        return ResponseEntity.badRequest().body(Map.of("error", descripcion));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> noEncontrado(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage() == null ? "Recurso no encontrado" : e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> errorInterno(Exception e) {
        LOG.error("Error inesperado en la operación", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "No se pudo completar la operación"));
    }
}
