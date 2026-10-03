package pe.edu.pucp.sisrap.bloqueo.api;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.pucp.sisrap.bloqueo.aplicacion.GestionarBloqueos;
import pe.edu.pucp.sisrap.bloqueo.aplicacion.GestionarBloqueos.SesionNoAutorizadaException;
import pe.edu.pucp.sisrap.bloqueo.dominio.BloqueoOperativo;
import pe.edu.pucp.sisrap.bloqueo.dominio.EstadoBloqueo;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

@RestController
@RequestMapping("/api/bloqueos")
public final class BloqueoController {

    private final GestionarBloqueos servicio;

    public BloqueoController(
            GestionarBloqueos servicio
    ) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<BloqueoRespuesta> listar(
            @RequestParam(
                    required = false
            )
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME
            )
            LocalDateTime instante
    ) {

        LocalDateTime referencia =
                instante != null
                        ? instante
                        : LocalDateTime.now();

        return servicio.listar()
                .stream()
                .map(b ->
                        respuesta(
                                b,
                                referencia
                        )
                )
                .toList();
    }

    @GetMapping("/{idIncidencia}")
    public BloqueoRespuesta buscar(
            @PathVariable long idIncidencia,

            @RequestParam(
                    required = false
            )
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME
            )
            LocalDateTime instante
    ) {

        try {

            LocalDateTime referencia =
                    instante != null
                            ? instante
                            : LocalDateTime.now();

            return respuesta(
                    servicio.buscar(idIncidencia),
                    referencia
            );

        } catch (NoSuchElementException e) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage()
            );
        }
    }

    @PostMapping
    public ResponseEntity<BloqueoRespuesta> crear(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization,

            @RequestBody CrearBloqueoRequest request
    ) {

        String token =
                extraerToken(authorization);

        try {

            List<Nodo> vertices =
                    request.vertices()
                            .stream()
                            .map(p ->
                                    new Nodo(
                                            p.x(),
                                            p.y()
                                    )
                            )
                            .toList();

            BloqueoOperativo bloqueo =
                    servicio.crear(
                            token,
                            request.inicio(),
                            request.fin(),
                            vertices
                    );

            return ResponseEntity
                    .created(
                            URI.create(
                                    "/api/bloqueos/"
                                            + bloqueo.idIncidencia()
                            )
                    )
                    .body(
                            respuesta(
                                    bloqueo,
                                    request.inicio()
                            )
                    );

        } catch (SesionNoAutorizadaException e) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    e.getMessage()
            );

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage()
            );
        }
    }

    @DeleteMapping("/{idIncidencia}")
    public ResponseEntity<Void> cancelar(
            @PathVariable long idIncidencia,

            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization
    ) {

        String token =
                extraerToken(authorization);

        try {

            servicio.cancelar(
                    token,
                    idIncidencia
            );

            return ResponseEntity.noContent().build();

        } catch (SesionNoAutorizadaException e) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    e.getMessage()
            );

        } catch (NoSuchElementException e) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage()
            );

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage()
            );
        }
    }

    private BloqueoRespuesta respuesta(
            BloqueoOperativo bloqueo,
            LocalDateTime instante
    ) {

        List<PuntoRespuesta> vertices =
                bloqueo.vertices()
                        .stream()
                        .map(n ->
                                new PuntoRespuesta(
                                        n.getX(),
                                        n.getY()
                                )
                        )
                        .toList();

        return new BloqueoRespuesta(
                bloqueo.idIncidencia(),
                bloqueo.inicio(),
                bloqueo.fin(),
                bloqueo.estadoEn(instante),
                vertices
        );
    }

    private String extraerToken(
            String authorization
    ) {

        if (authorization == null
                || !authorization.startsWith("Bearer ")) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Se requiere una sesión"
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

    public record CrearBloqueoRequest(
            LocalDateTime inicio,
            LocalDateTime fin,
            List<PuntoRequest> vertices
    ) {
    }

    public record PuntoRequest(
            int x,
            int y
    ) {
    }

    public record PuntoRespuesta(
            int x,
            int y
    ) {
    }

    public record BloqueoRespuesta(
            long idIncidencia,
            LocalDateTime inicio,
            LocalDateTime fin,
            EstadoBloqueo estado,
            List<PuntoRespuesta> vertices
    ) {
    }
}