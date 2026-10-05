package pe.edu.pucp.sisrap.control.aplicacion;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import pe.edu.pucp.sisrap.control.dominio.ControlSimulacion;
import pe.edu.pucp.sisrap.control.dominio.RepositorioControlSimulacion;
import pe.edu.pucp.sisrap.sesion.aplicacion.GestionarSesiones;
import pe.edu.pucp.sisrap.sesion.dominio.Sesion;

/**
 * Control exclusivo interno de la simulación.
 *
 * <p>No se expone mediante una API REST. La primera sesión que inicia una
 * simulación adquiere el control y lo conserva durante toda la ejecución.</p>
 */
@Service
public class GestionarControlSimulacion {

    private final RepositorioControlSimulacion repositorio;
    private final GestionarSesiones gestionarSesiones;

    public GestionarControlSimulacion(
            RepositorioControlSimulacion repositorio,
            GestionarSesiones gestionarSesiones
    ) {
        this.repositorio = repositorio;
        this.gestionarSesiones = gestionarSesiones;
    }

    /**
     * Adquiere automáticamente el control al intentar iniciar una simulación.
     *
     * <p>Si otra sesión ya controla la ejecución, la operación se rechaza.
     * Si la misma sesión ya es la propietaria, la operación es idempotente.</p>
     */
    public synchronized AdquisicionControl adquirirParaSimulacion(
            String tokenSesion
    ) {

        Sesion sesion = gestionarSesiones.validar(tokenSesion);

        Optional<ControlSimulacion> actual = repositorio.obtener();

        if (actual.isEmpty()) {
            ControlSimulacion nuevo = new ControlSimulacion(
                    sesion.id(),
                    Instant.now()
            );
            repositorio.guardar(nuevo);
            return new AdquisicionControl(nuevo, true);
        }

        ControlSimulacion control = actual.get();

        if (control.perteneceA(sesion.id())) {
            return new AdquisicionControl(control, false);
        }

        throw new ControlOcupadoException();
    }

    /**
     * Exige que exista una simulación controlada por esta sesión.
     * Se usa para pausar, reanudar y detener.
     */
    public void verificarControladorActivo(
            String tokenSesion
    ) {

        Sesion sesion = gestionarSesiones.validar(tokenSesion);

        ControlSimulacion control = repositorio.obtener()
                .orElseThrow(ControlNoAutorizadoException::new);

        if (!control.perteneceA(sesion.id())) {
            throw new ControlNoAutorizadoException();
        }
    }

    /**
     * Verificación usada por operaciones estructurales existentes
     * (mapa, almacenes y carga histórica).
     *
     * <p>Antes de que exista una simulación activa, cualquier sesión válida
     * puede preparar los datos. Una vez que una sesión inicia la simulación,
     * solo esa sesión puede ejecutar estas operaciones mientras dure.</p>
     */
    public void verificarControlador(
            String tokenSesion
    ) {

        Sesion sesion = gestionarSesiones.validar(tokenSesion);

        Optional<ControlSimulacion> actual = repositorio.obtener();

        if (actual.isPresent()
                && !actual.get().perteneceA(sesion.id())) {
            throw new ControlNoAutorizadoException();
        }
    }

    /**
     * Liberación exclusiva para la lógica interna del backend.
     * No existe endpoint público para realizar esta operación.
     */
    public synchronized void liberarInternamente() {
        repositorio.liberar();
    }

    public Optional<ControlSimulacion> obtenerActual() {
        return repositorio.obtener();
    }

    public record AdquisicionControl(
            ControlSimulacion control,
            boolean adquiridoAhora
    ) {
    }

    public static final class ControlOcupadoException
            extends RuntimeException {

        public ControlOcupadoException() {
            super(
                    "Otra sesión ya controla la simulación activa"
            );
        }
    }

    public static final class ControlNoAutorizadoException
            extends RuntimeException {

        public ControlNoAutorizadoException() {
            super(
                    "La sesión actual no controla la simulación"
            );
        }
    }
}
