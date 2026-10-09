package pe.edu.pucp.sisrap.configuracionoperativa.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.pucp.sisrap.configuracionoperativa.aplicacion.GestionarConfiguracionOperativa;
import pe.edu.pucp.sisrap.configuracionoperativa.dominio.ConfiguracionOperativa;
import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion.ControlNoAutorizadoException;

/** El PUT guarda una plantilla; la flota real se crea al iniciar la corrida. */
@RestController
@RequestMapping("/api/configuracion-operativa")
public class ConfiguracionOperativaController {
    private final GestionarConfiguracionOperativa servicio;

    public ConfiguracionOperativaController(GestionarConfiguracionOperativa servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ConfiguracionOperativa consultar() {
        return servicio.consultar();
    }

    @PutMapping
    public ConfiguracionOperativa actualizar(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ConfiguracionOperativa configuracion) {
        try {
            return servicio.actualizar(extraerToken(authorization), configuracion);
        } catch (ControlNoAutorizadoException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private String extraerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere token Bearer");
        }
        return authorization.substring(7).trim();
    }
}
