package pe.edu.pucp.sisrap.mapa.aplicacion;

import org.springframework.stereotype.Service;

import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion;
import pe.edu.pucp.sisrap.mapa.dominio.MapaCiudad;
import pe.edu.pucp.sisrap.mapa.dominio.RepositorioMapa;

@Service
public final class GestionarMapa {

    private final RepositorioMapa repositorio;
    private final GestionarControlSimulacion control;

    public GestionarMapa(
            RepositorioMapa repositorio,
            GestionarControlSimulacion control
    ) {
        this.repositorio = repositorio;
        this.control = control;
    }

    public MapaCiudad obtener() {
        return repositorio.obtener();
    }

    public MapaCiudad cambiarDimensiones(
            String token,
            int ancho,
            int alto
    ) {

        control.verificarControlador(token);

        if (ancho <= 0 || alto <= 0) {
            throw new IllegalArgumentException(
                    "El ancho y alto deben ser mayores que cero"
            );
        }

        return repositorio.cambiarDimensiones(
                ancho,
                alto
        );
    }
}