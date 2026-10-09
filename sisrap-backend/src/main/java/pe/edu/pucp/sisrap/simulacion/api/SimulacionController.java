package pe.edu.pucp.sisrap.simulacion.api;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion.ControlNoAutorizadoException;
import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion.ControlOcupadoException;
import pe.edu.pucp.sisrap.simulacion.aplicacion.GestionarSimulacion;
import pe.edu.pucp.sisrap.simulacion.aplicacion.MotorSimulacion.ConflictoSimulacionException;
import pe.edu.pucp.sisrap.simulacion.dominio.ConfiguracionSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EscenarioSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.SnapshotSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.ResultadoCorrida;

@RestController
@RequestMapping("/api/simulacion")
public class SimulacionController {

    private final GestionarSimulacion servicio;

    public SimulacionController(
            GestionarSimulacion servicio
    ) {
        this.servicio = servicio;
    }

    /**
     * Estado público: todos los dispositivos pueden observar la simulación.
     */
    /** Consulta una corrida finalizada sin mezclar registros de otras ejecuciones. */
    @GetMapping("/{idSimulacion}/resultado")
    public ResultadoCorrida resultado(@PathVariable long idSimulacion) {
        try {
            return servicio.resultado(idSimulacion);
        } catch (java.util.NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/estado")
    public SnapshotSimulacion estado() {
        return servicio.estado();
    }

    /**
     * Flujo SSE público usado por el visualizador en tiempo real.
     */
    @GetMapping(
            value = "/eventos",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter eventos() {
        return servicio.eventos();
    }

    /**
     * La primera sesión que inicia una simulación se convierte
     * automáticamente en su controladora.
     */
    @PostMapping("/iniciar")
    public SnapshotSimulacion iniciar(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization,

            @RequestBody
            IniciarSimulacionRequest request
    ) {

        String token = extraerToken(authorization);

        long semilla =
                request.semilla() == null
                        ? java.util.concurrent.ThreadLocalRandom.current().nextLong()
                        : request.semilla();

        try {
            return servicio.iniciar(
                    token,
                    new ConfiguracionSimulacion(
                            request.escenario(),
                            request.fechaInicio(),
                            semilla
                    )
            );

        } catch (ControlOcupadoException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );

        } catch (ControlNoAutorizadoException e) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    e.getMessage()
            );

        } catch (ConflictoSimulacionException e) {
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

    @PostMapping("/pausar")
    public SnapshotSimulacion pausar(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization
    ) {
        return ejecutarControlado(
                authorization,
                Accion.PAUSAR
        );
    }

    @PostMapping("/reanudar")
    public SnapshotSimulacion reanudar(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization
    ) {
        return ejecutarControlado(
                authorization,
                Accion.REANUDAR
        );
    }

    @PostMapping("/detener")
    public SnapshotSimulacion detener(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization
    ) {
        return ejecutarControlado(
                authorization,
                Accion.DETENER
        );
    }

    private SnapshotSimulacion ejecutarControlado(
            String authorization,
            Accion accion
    ) {

        String token = extraerToken(authorization);

        try {
            return switch (accion) {
                case PAUSAR -> servicio.pausar(token);
                case REANUDAR -> servicio.reanudar(token);
                case DETENER -> servicio.detener(token);
            };

        } catch (ControlNoAutorizadoException e) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    e.getMessage()
            );

        } catch (ConflictoSimulacionException e) {
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
                authorization
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

    public record IniciarSimulacionRequest(
            EscenarioSimulacion escenario,
            LocalDate fechaInicio,
            Long semilla
    ) {
    }

    private enum Accion {
        PAUSAR,
        REANUDAR,
        DETENER
    }
}
