package pe.edu.pucp.sisrap.almacen.api;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.pucp.sisrap.almacen.aplicacion.GestionarAlmacenes;
import pe.edu.pucp.sisrap.almacen.dominio.AlmacenOperativo;
import pe.edu.pucp.sisrap.almacen.dominio.ConflictoAlmacenException;
import pe.edu.pucp.sisrap.almacen.dominio.TipoAlmacen;
import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion.ControlNoAutorizadoException;

@RestController
@RequestMapping("/api/almacenes")
public class AlmacenController {

    private final GestionarAlmacenes servicio;

    public AlmacenController(
            GestionarAlmacenes servicio
    ) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<AlmacenOperativo> listar() {
        return servicio.listar();
    }

    @GetMapping("/{idAlmacen}")
    public AlmacenOperativo buscar(
            @PathVariable String idAlmacen
    ) {

        try {
            return servicio.buscar(idAlmacen);

        } catch (NoSuchElementException e) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage()
            );
        }
    }

    @PostMapping
    public ResponseEntity<AlmacenOperativo> crear(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization,

            @RequestBody CrearAlmacenRequest request
    ) {

        String token =
                extraerToken(authorization);

        try {

            AlmacenOperativo almacen =
                    servicio.crear(
                            token,
                            request.idAlmacen(),
                            request.nombre(),
                            request.tipo(),
                            request.ubicacionX(),
                            request.ubicacionY(),
                            request.capacidadMaxima()
                    );

            return ResponseEntity
                    .created(
                            URI.create(
                                    "/api/almacenes/"
                                            + almacen.idAlmacen()
                            )
                    )
                    .body(almacen);

        } catch (ControlNoAutorizadoException e) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    e.getMessage()
            );

        } catch (ConflictoAlmacenException e) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage()
            );
        }
    }

    @PutMapping("/{idAlmacen}")
    public AlmacenOperativo actualizar(
            @PathVariable String idAlmacen,

            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization,

            @RequestBody ActualizarAlmacenRequest request
    ) {

        String token =
                extraerToken(authorization);

        try {

            return servicio.actualizar(
                    token,
                    idAlmacen,
                    request.nombre(),
                    request.ubicacionX(),
                    request.ubicacionY(),
                    request.capacidadMaxima()
            );

        } catch (ControlNoAutorizadoException e) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    e.getMessage()
            );

        } catch (NoSuchElementException e) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage()
            );

        } catch (ConflictoAlmacenException e) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage()
            );
        }
    }

    @DeleteMapping("/{idAlmacen}")
    public ResponseEntity<Void> eliminar(
            @PathVariable String idAlmacen,

            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization
    ) {

        String token =
                extraerToken(authorization);

        try {

            servicio.eliminar(
                    token,
                    idAlmacen
            );

            return ResponseEntity
                    .noContent()
                    .build();

        } catch (ControlNoAutorizadoException e) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    e.getMessage()
            );

        } catch (NoSuchElementException e) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage()
            );

        } catch (ConflictoAlmacenException e) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );
        }
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

    public record CrearAlmacenRequest(
            String idAlmacen,
            String nombre,
            TipoAlmacen tipo,
            int ubicacionX,
            int ubicacionY,
            Integer capacidadMaxima
    ) {
    }

    public record ActualizarAlmacenRequest(
            String nombre,
            int ubicacionX,
            int ubicacionY,
            Integer capacidadMaxima
    ) {
    }
}