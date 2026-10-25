package pe.edu.pucp.sisrap.configuracionoperativa.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.pucp.sisrap.configuracionoperativa.aplicacion.CalendariosCasoService;
import pe.edu.pucp.sisrap.configuracionoperativa.infraestructura.JdbcConfiguracionEscenario;
import pe.edu.pucp.sisrap.configuracionoperativa.infraestructura.JdbcConfiguracionEscenario.Plantilla;
import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion;
import pe.edu.pucp.sisrap.simulacion.aplicacion.MotorSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EscenarioSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EstadoSimulacion;

@RestController
@RequestMapping("/api/configuracion-operativa/escenarios")
public class ConfiguracionEscenarioController {
    private final JdbcConfiguracionEscenario escenarios;
    private final CalendariosCasoService calendarios;
    private final GestionarControlSimulacion control;
    private final MotorSimulacion motor;

    public ConfiguracionEscenarioController(JdbcConfiguracionEscenario escenarios,
            CalendariosCasoService calendarios, GestionarControlSimulacion control, MotorSimulacion motor) {
        this.escenarios = escenarios; this.calendarios = calendarios; this.control = control; this.motor = motor;
    }

    @GetMapping("/{escenario}")
    public Plantilla consultar(@PathVariable EscenarioSimulacion escenario) {
        synchronized (motor) { return escenarios.consultar(escenario); }
    }

    @PutMapping("/{escenario}")
    public Plantilla guardar(@PathVariable EscenarioSimulacion escenario,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Plantilla plantilla) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.substring(7).isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere una sesión válida");
        try {
            control.verificarControlador(authorization.substring(7).trim());
            synchronized (motor) {
                var estado = motor.estadoActual().estado();
                if (estado == EstadoSimulacion.EJECUTANDO || estado == EstadoSimulacion.PAUSADA)
                    throw new IllegalStateException("La configuración no puede cambiar durante una corrida");
                if (plantilla == null || plantilla.escenario() != escenario || plantilla.flota() == null
                        || plantilla.flota().stream().anyMatch(java.util.Objects::isNull))
                    throw new IllegalArgumentException("La configuración enviada no es válida para este escenario");
                escenarios.validarPlantilla(escenario, plantilla);
                calendarios.validarCompatibilidad(plantilla);
                return escenarios.guardar(escenario, plantilla);
            }
        } catch (GestionarControlSimulacion.ControlNoAutorizadoException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
