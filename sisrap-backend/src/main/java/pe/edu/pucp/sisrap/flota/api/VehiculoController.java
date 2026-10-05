package pe.edu.pucp.sisrap.flota.api;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.pucp.sisrap.flota.aplicacion.GestionarVehiculos;
import pe.edu.pucp.sisrap.flota.aplicacion.GestionarVehiculos.SesionNoAutorizadaException;
import pe.edu.pucp.sisrap.flota.dominio.AveriaVehiculo;
import pe.edu.pucp.sisrap.flota.dominio.ConflictoVehiculoException;
import pe.edu.pucp.sisrap.flota.dominio.MantenimientoVehiculo;
import pe.edu.pucp.sisrap.flota.dominio.TipoAveria;
import pe.edu.pucp.sisrap.flota.dominio.TipoMantenimiento;
import pe.edu.pucp.sisrap.flota.dominio.VehiculoOperativo;

@RestController
@RequestMapping("/api/vehiculos")
public final class VehiculoController {

        public record RegistrarMantenimientoRequest(
                TipoMantenimiento tipo,
                LocalDateTime fechaInicio,
                LocalDateTime fechaFin
        ) {
        }

    private final GestionarVehiculos servicio;

    public VehiculoController(
            GestionarVehiculos servicio
    ) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<VehiculoOperativo> listar() {
        return servicio.listar();
    }

    @GetMapping("/{idVehiculo}")
    public VehiculoOperativo buscar(
            @PathVariable String idVehiculo
    ) {

        try {
            return servicio.buscar(idVehiculo);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage()
            );
        }
    }

    @GetMapping("/{idVehiculo}/averias")
    public List<AveriaVehiculo> averias(
            @PathVariable String idVehiculo
    ) {

        try {
            return servicio.listarAverias(
                    idVehiculo
            );
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage()
            );
        }
    }

    @PostMapping("/{idVehiculo}/averias")
    public ResponseEntity<AveriaVehiculo> registrarAveria(
            @PathVariable String idVehiculo,

            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization,

            @RequestBody RegistrarAveriaRequest request
    ) {

        String token =
                extraerToken(authorization);

        try {

            AveriaVehiculo averia =
                    servicio.registrarAveria(
                            token,
                            idVehiculo,
                            request.tipoAveria(),
                            request.fechaOcurrencia(),
                            request.horaRetornoEstimada()
                    );

            return ResponseEntity
                    .created(
                            URI.create(
                                    "/api/vehiculos/"
                                            + idVehiculo
                                            + "/averias/"
                                            + averia.idIncidencia()
                            )
                    )
                    .body(averia);

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

        } catch (ConflictoVehiculoException e) {

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

    @PostMapping(
            "/{idVehiculo}/averias/{idIncidencia}/resolver"
    )
    public AveriaVehiculo resolverAveria(
            @PathVariable String idVehiculo,
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

            return servicio.resolverAveria(
                    token,
                    idVehiculo,
                    idIncidencia
            );

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

    public record RegistrarAveriaRequest(
            TipoAveria tipoAveria,
            LocalDateTime fechaOcurrencia,
            LocalDateTime horaRetornoEstimada
    ) {
    }
        @GetMapping("/{idVehiculo}/mantenimientos")
        public List<MantenimientoVehiculo>
        listarMantenimientos(
                @PathVariable String idVehiculo
        ) {

        try {

                return servicio
                        .listarMantenimientos(
                                idVehiculo
                        );

        } catch (NoSuchElementException e) {

                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        e.getMessage()
                );
        }
        }

        @PostMapping("/{idVehiculo}/mantenimientos")
        public ResponseEntity<MantenimientoVehiculo>
        registrarMantenimiento(

                @PathVariable String idVehiculo,

                @RequestHeader(
                        value = "Authorization",
                        required = false
                )
                String authorization,

                @RequestBody
                RegistrarMantenimientoRequest request
        ) {

        String token =
                extraerToken(authorization);

        try {

                MantenimientoVehiculo mantenimiento =
                        servicio.registrarMantenimiento(
                                token,
                                idVehiculo,
                                request.tipo(),
                                request.fechaInicio(),
                                request.fechaFin()
                        );

                return ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(mantenimiento);

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

        } catch (ConflictoVehiculoException e) {

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
        @DeleteMapping(
                "/{idVehiculo}/mantenimientos/{idMantenimiento}"
        )
        public ResponseEntity<Void>
        cancelarMantenimiento(

                @PathVariable String idVehiculo,

                @PathVariable long idMantenimiento,

                @RequestHeader(
                        value = "Authorization",
                        required = false
                )
                String authorization
        ) {

        String token =
                extraerToken(authorization);

        try {

                servicio.cancelarMantenimiento(
                        token,
                        idVehiculo,
                        idMantenimiento
                );

                return ResponseEntity
                        .noContent()
                        .build();

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
}