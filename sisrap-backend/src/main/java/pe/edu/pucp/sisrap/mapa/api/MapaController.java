package pe.edu.pucp.sisrap.mapa.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion.ControlNoAutorizadoException;
import pe.edu.pucp.sisrap.mapa.aplicacion.GestionarMapa;
import pe.edu.pucp.sisrap.mapa.dominio.DimensionMapaInvalidaException;
import pe.edu.pucp.sisrap.mapa.dominio.MapaCiudad;

@RestController
@RequestMapping("/api/mapa")
public final class MapaController {

    private final GestionarMapa servicio;

    public MapaController(
            GestionarMapa servicio
    ) {
        this.servicio = servicio;
    }

    @GetMapping
    public MapaCiudad obtener() {
        return servicio.obtener();
    }

    @PutMapping("/dimensiones")
    public MapaCiudad cambiarDimensiones(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization,

            @RequestBody CambiarDimensionesRequest request
    ) {

        String token =
                extraerToken(authorization);

        try {

            return servicio.cambiarDimensiones(
                    token,
                    request.anchoKm(),
                    request.altoKm()
            );

        } catch (ControlNoAutorizadoException e) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    e.getMessage()
            );

        } catch (DimensionMapaInvalidaException e) {

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

    public record CambiarDimensionesRequest(
            int anchoKm,
            int altoKm
    ) {
    }
}