package pe.edu.pucp.sisrap.simulacion.api;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion.ControlNoAutorizadoException;
import pe.edu.pucp.sisrap.simulacion.aplicacion.GestionarSimulacion;
import pe.edu.pucp.sisrap.simulacion.aplicacion.MotorSimulacion.ConflictoSimulacionException;
import pe.edu.pucp.sisrap.simulacion.dominio.ConfiguracionSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.SnapshotSimulacion;

@RestController
@RequestMapping("/api/simulacion")
public final class SimulacionController {

    private final GestionarSimulacion servicio;

    public SimulacionController(GestionarSimulacion servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/estado")
    public SnapshotSimulacion estado() {
        return servicio.estado();
    }

    @GetMapping(
            value = "/eventos",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventos() {
        return servicio.eventos();
    }

    @PostMapping("/iniciar")
    public SnapshotSimulacion iniciar(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @RequestBody IniciarSimulacionRequest request) {

        String token = extraerToken(authorization);
        long semilla = request.semilla() == null ? 42L : request.semilla();

        try {
            return servicio.iniciar(
                    token,
                    new ConfiguracionSimulacion(
                            request.fechaHoraInicio(),
                            request.fechaHoraFin(),
                            request.velocidad(),
                            semilla));
        } catch (ControlNoAutorizadoException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (ConflictoSimulacionException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/pausar")
    public SnapshotSimulacion pausar(
            @RequestHeader(value = "Authorization", required = false)
            String authorization) {
        return ejecutarControlado(authorization, Accion.PAUSAR, 0.0);
    }

    @PostMapping("/reanudar")
    public SnapshotSimulacion reanudar(
            @RequestHeader(value = "Authorization", required = false)
            String authorization) {
        return ejecutarControlado(authorization, Accion.REANUDAR, 0.0);
    }

    @PostMapping("/detener")
    public SnapshotSimulacion detener(
            @RequestHeader(value = "Authorization", required = false)
            String authorization) {
        return ejecutarControlado(authorization, Accion.DETENER, 0.0);
    }

    @PutMapping("/velocidad")
    public SnapshotSimulacion cambiarVelocidad(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @RequestBody CambiarVelocidadRequest request) {
        return ejecutarControlado(authorization, Accion.VELOCIDAD, request.velocidad());
    }

    private SnapshotSimulacion ejecutarControlado(
            String authorization,
            Accion accion,
            double velocidad) {

        String token = extraerToken(authorization);
        try {
            return switch (accion) {
                case PAUSAR -> servicio.pausar(token);
                case REANUDAR -> servicio.reanudar(token);
                case DETENER -> servicio.detener(token);
                case VELOCIDAD -> servicio.cambiarVelocidad(token, velocidad);
            };
        } catch (ControlNoAutorizadoException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (ConflictoSimulacionException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private String extraerToken(String authorization) {
        if (authorization == null
                || authorization.isBlank()
                || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Se requiere una sesión válida");
        }
        String token = authorization.substring(7).trim();
        if (token.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token de sesión vacío");
        }
        return token;
    }

    public record IniciarSimulacionRequest(
            LocalDateTime fechaHoraInicio,
            LocalDateTime fechaHoraFin,
            double velocidad,
            Long semilla) {
    }

    public record CambiarVelocidadRequest(double velocidad) {
    }

    private enum Accion {
        PAUSAR,
        REANUDAR,
        DETENER,
        VELOCIDAD
    }
}
