package pe.edu.pucp.sisrap.control.aplicacion;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import pe.edu.pucp.sisrap.control.dominio.ControlSimulacion;
import pe.edu.pucp.sisrap.control.dominio.RepositorioControlSimulacion;
import pe.edu.pucp.sisrap.sesion.aplicacion.GestionarSesiones;
import pe.edu.pucp.sisrap.sesion.dominio.Sesion;

@Service
public final class GestionarControlSimulacion {

    /*
     * El navegador controlador debe renovar su control
     * antes de que transcurran estos 30 segundos.
     */
    private static final Duration DURACION_CONTROL =
            Duration.ofSeconds(30);

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
     * Devuelve el control actual.
     *
     * Si ya expiró, lo elimina automáticamente.
     */
    public synchronized Optional<ControlSimulacion> obtenerActual() {

        Instant ahora = Instant.now();

        Optional<ControlSimulacion> actual =
                repositorio.obtener();

        if (actual.isEmpty()) {
            return Optional.empty();
        }

        if (actual.get().expirado(ahora)) {
            repositorio.liberar();
            return Optional.empty();
        }

        return actual;
    }

    /**
     * Una sesión intenta convertirse en controladora.
     */
    public synchronized ControlSimulacion adquirir(
            String tokenSesion
    ) {

        Sesion sesion = gestionarSesiones.validar(tokenSesion);

        Instant ahora = Instant.now();

        Optional<ControlSimulacion> actual =
                obtenerActual();

        /*
         * No existe controlador.
         */
        if (actual.isEmpty()) {

            ControlSimulacion nuevo =
                    new ControlSimulacion(
                            sesion.id(),
                            ahora,
                            ahora,
                            ahora.plus(DURACION_CONTROL)
                    );

            repositorio.guardar(nuevo);

            return nuevo;
        }

        ControlSimulacion control = actual.get();

        /*
         * La misma sesión ya tenía el control.
         *
         * Hacemos la operación idempotente:
         * simplemente renovamos su lease.
         */
        if (control.perteneceA(sesion.id())) {

            ControlSimulacion renovado =
                    control.renovar(
                            ahora,
                            DURACION_CONTROL
                    );

            repositorio.guardar(renovado);

            return renovado;
        }

        /*
         * Otra sesión tiene actualmente el control.
         */
        throw new ControlOcupadoException(
                control.expiraEn()
        );
    }

    /**
     * Mantiene vivo el control.
     */
    public synchronized ControlSimulacion renovar(
            String tokenSesion
    ) {

        Sesion sesion = gestionarSesiones.validar(tokenSesion);

        Instant ahora = Instant.now();

        ControlSimulacion control = obtenerActual()
                .orElseThrow(
                        () -> new SinControlException(
                                "No existe un controlador activo"
                        )
                );

        if (!control.perteneceA(sesion.id())) {
            throw new ControlNoAutorizadoException();
        }

        ControlSimulacion renovado =
                control.renovar(
                        ahora,
                        DURACION_CONTROL
                );

        repositorio.guardar(renovado);

        return renovado;
    }

    /**
     * Libera voluntariamente el control.
     */
    public synchronized void liberar(
            String tokenSesion
    ) {

        Sesion sesion = gestionarSesiones.validar(tokenSesion);

        ControlSimulacion control = obtenerActual()
                .orElseThrow(
                        () -> new SinControlException(
                                "No existe un controlador activo"
                        )
                );

        if (!control.perteneceA(sesion.id())) {
            throw new ControlNoAutorizadoException();
        }

        repositorio.liberar();
    }

    /**
     * Se utilizará después desde:
     *
     * - ParametrosController
     * - iniciar simulación
     * - pausar simulación
     * - detener simulación
     *
     * para comprobar que el usuario realmente
     * posee el control.
     */
    public void verificarControlador(
            String tokenSesion
    ) {

        Sesion sesion = gestionarSesiones.validar(tokenSesion);

        ControlSimulacion control = obtenerActual()
                .orElseThrow(
                        () -> new ControlNoAutorizadoException()
                );

        if (!control.perteneceA(sesion.id())) {
            throw new ControlNoAutorizadoException();
        }
    }

    public static final class ControlOcupadoException
            extends RuntimeException {

        private final Instant expiraEn;

        public ControlOcupadoException(
                Instant expiraEn
        ) {
            super("Otra sesión tiene el control de la simulación");
            this.expiraEn = expiraEn;
        }

        public Instant getExpiraEn() {
            return expiraEn;
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

    public static final class SinControlException
            extends RuntimeException {

        public SinControlException(String mensaje) {
            super(mensaje);
        }
    }
}