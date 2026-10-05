package pe.edu.pucp.sisrap.sesion.api;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.pucp.sisrap.sesion.aplicacion.GestionarSesiones;
import pe.edu.pucp.sisrap.sesion.dominio.Sesion;

@RestController
@RequestMapping("/api/sesiones")
public final class SesionController {

    private final GestionarSesiones servicio;

    public SesionController(GestionarSesiones servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ResponseEntity<SesionCreadaRespuesta> crear() {

        var emitida = servicio.crear();

        Sesion sesion = emitida.sesion();

        var respuesta = new SesionCreadaRespuesta(
                sesion.id(),
                emitida.token(),
                sesion.creadaEn(),
                sesion.expiraEn()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(respuesta);
    }

    @GetMapping("/actual")
    public SesionRespuesta actual(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization
    ) {

        String token = extraerToken(authorization);

        Sesion sesion;

        try {
            sesion = servicio.validar(token);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    e.getMessage()
            );
        }

        return new SesionRespuesta(
                sesion.id(),
                sesion.creadaEn(),
                sesion.ultimaActividadEn(),
                sesion.expiraEn()
        );
    }

    @DeleteMapping("/actual")
    public ResponseEntity<Void> cerrar(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization
    ) {

        String token = extraerToken(authorization);

        servicio.cerrar(token);

        return ResponseEntity.noContent().build();
    }

    private String extraerToken(String authorization) {

        if (authorization == null
                || authorization.isBlank()
                || !authorization.startsWith("Bearer ")) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Se requiere una sesión válida"
            );
        }

        String token = authorization
                .substring(7)
                .trim();

        if (token.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token de sesión vacío"
            );
        }

        return token;
    }

    public record SesionCreadaRespuesta(
            UUID sesionId,
            String token,
            Instant creadaEn,
            Instant expiraEn
    ) {}

    public record SesionRespuesta(
            UUID sesionId,
            Instant creadaEn,
            Instant ultimaActividadEn,
            Instant expiraEn
    ) {}
}