package pe.edu.pucp.sisrap.configuracionoperativa.api;

import java.util.List;
import pe.edu.pucp.sisrap.simulacion.dominio.EscenarioSimulacion;
import pe.edu.pucp.sisrap.configuracionoperativa.infraestructura.JdbcConfiguracionEscenario;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.pucp.sisrap.configuracionoperativa.aplicacion.CalendariosCasoService;
import pe.edu.pucp.sisrap.configuracionoperativa.aplicacion.CalendariosCasoService.ArchivoCalendario;
import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion;
import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion.ControlNoAutorizadoException;
import pe.edu.pucp.sisrap.simulacion.aplicacion.MotorSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EstadoSimulacion;

@RestController
@RequestMapping("/api/datos-caso/calendarios")
public class CalendariosCasoController {
    private final CalendariosCasoService archivos;
    private final GestionarControlSimulacion control;
    private final MotorSimulacion motor;
    private final JdbcConfiguracionEscenario escenarios;

    public CalendariosCasoController(CalendariosCasoService archivos,
                                    GestionarControlSimulacion control, MotorSimulacion motor, JdbcConfiguracionEscenario escenarios) {
        this.archivos = archivos;
        this.control = control;
        this.motor = motor;
        this.escenarios = escenarios;
    }

    @GetMapping
    public List<ArchivoCalendario> listar(
            @RequestParam(defaultValue = "OPERACION_DIARIA") EscenarioSimulacion escenario) {
        return archivos.listar(escenario);
    }

    @GetMapping("/programados")
    public CalendariosCasoService.Prevision programados(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "OPERACION_DIARIA") EscenarioSimulacion escenario) {
        try {
            return archivos.prevision(escenario, desde, hasta);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ArchivoCalendario>> subir(
            @RequestHeader(value = "Authorization", required = false) String autorizacion,
            @RequestPart("archivos") List<MultipartFile> entradas,
            @RequestParam(defaultValue = "OPERACION_DIARIA") EscenarioSimulacion escenario) {
        try {
            control.verificarControlador(token(autorizacion));
            synchronized (motor) {
                comprobarSinCorrida();
                return ResponseEntity.status(HttpStatus.CREATED).body(archivos.subir(escenario, entradas, escenarios.consultar(escenario)));
            }
        } catch (ControlNoAutorizadoException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && (e.getMessage().startsWith("Sesión") || e.getMessage().startsWith("Token")))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> eliminar(
            @RequestHeader(value = "Authorization", required = false) String autorizacion,
            @PathVariable String nombre,
            @RequestParam(defaultValue = "OPERACION_DIARIA") EscenarioSimulacion escenario) {
        try {
            control.verificarControlador(token(autorizacion));
            synchronized (motor) {
                comprobarSinCorrida();
                archivos.eliminar(escenario, nombre);
            }
            return ResponseEntity.noContent().build();
        } catch (ControlNoAutorizadoException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && (e.getMessage().startsWith("Sesión") || e.getMessage().startsWith("Token")))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private void comprobarSinCorrida() {
        EstadoSimulacion estado = motor.estadoActual().estado();
        if (estado == EstadoSimulacion.EJECUTANDO || estado == EstadoSimulacion.PAUSADA) {
            throw new IllegalStateException("Los calendarios solo pueden modificarse fuera de una corrida");
        }
    }

    private static String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw new IllegalArgumentException("Token de sesión requerido");
        return authorization.substring(7).trim();
    }
}
