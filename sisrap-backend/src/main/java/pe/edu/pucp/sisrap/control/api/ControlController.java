package pe.edu.pucp.sisrap.control.api;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion;
import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion.ControlNoAutorizadoException;
import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion.ControlOcupadoException;
import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion.SinControlException;
import pe.edu.pucp.sisrap.control.dominio.ControlSimulacion;

@RestController
@RequestMapping("/api/control")
public final class ControlController {

    private final GestionarControlSimulacion servicio;

    public ControlController(
            GestionarControlSimulacion servicio
    ) {
        this.servicio = servicio;
    }

    /**
     * Público.
     *
     * Permite saber si alguien está controlando
     * actualmente la simulación.
     */
    @GetMapping
    public EstadoControlRespuesta estado() {

        return servicio.obtenerActual()
                .map(control ->
                        new EstadoControlRespuesta(
                                true,
                                control.sesionControladoraId(),
                                control.adquiridoEn(),
                                control.renovadoEn(),
                                control.expiraEn()
                        )
                )
                .orElseGet(
                        () -> new EstadoControlRespuesta(
                                false,
                                null,
                                null,
                                null,
                                null
                        )
                );
    }

    /**
     * Intenta adquirir el control.
     */
    @PostMapping("/adquirir")
    public ResponseEntity<ControlRespuesta> adquirir(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization
    ) {

        String token = extraerToken(authorization);

        try {

            ControlSimulacion control =
                    servicio.adquirir(token);

            return ResponseEntity.ok(
                    respuesta(control)
            );

        } catch (ControlOcupadoException e) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Otra sesión controla actualmente "
                            + "la simulación. El control expira en: "
                            + e.getExpiraEn()
            );

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    e.getMessage()
            );
        }
    }

    /**
     * Renueva el lease.
     */
    @PutMapping("/renovar")
    public ControlRespuesta renovar(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization
    ) {

        String token = extraerToken(authorization);

        try {

            ControlSimulacion control =
                    servicio.renovar(token);

            return respuesta(control);

        } catch (ControlNoAutorizadoException e) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    e.getMessage()
            );

        } catch (SinControlException e) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    e.getMessage()
            );
        }
    }

    /**
     * Libera el control voluntariamente.
     */
    @DeleteMapping
    public ResponseEntity<Void> liberar(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization
    ) {

        String token = extraerToken(authorization);

        try {

            servicio.liberar(token);

            return ResponseEntity.noContent().build();

        } catch (ControlNoAutorizadoException e) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    e.getMessage()
            );

        } catch (SinControlException e) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    e.getMessage()
            );
        }
    }

    private ControlRespuesta respuesta(
            ControlSimulacion control
    ) {
        return new ControlRespuesta(
                control.sesionControladoraId(),
                control.adquiridoEn(),
                control.renovadoEn(),
                control.expiraEn()
        );
    }

    private String extraerToken(
            String authorization
    ) {

        if (authorization == null
                || authorization.isBlank()
                || !authorization.startsWith("Bearer ")) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Se requiere una sesión válida"
            );
        }

        String token =
                authorization.substring(7).trim();

        if (token.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token de sesión vacío"
            );
        }

        return token;
    }

    public record EstadoControlRespuesta(
            boolean ocupado,
            UUID sesionControladoraId,
            Instant adquiridoEn,
            Instant renovadoEn,
            Instant expiraEn
    ) {}

    public record ControlRespuesta(
            UUID sesionControladoraId,
            Instant adquiridoEn,
            Instant renovadoEn,
            Instant expiraEn
    ) {}
}