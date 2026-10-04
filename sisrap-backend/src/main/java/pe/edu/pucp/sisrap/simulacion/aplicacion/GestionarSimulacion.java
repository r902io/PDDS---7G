package pe.edu.pucp.sisrap.simulacion.aplicacion;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion;
import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion.AdquisicionControl;
import pe.edu.pucp.sisrap.simulacion.dominio.ConfiguracionSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.SnapshotSimulacion;
import pe.edu.pucp.sisrap.simulacion.infraestructura.SseSimulacion;

@Service
public class GestionarSimulacion {

    private final MotorSimulacion motor;
    private final GestionarControlSimulacion control;
    private final SseSimulacion sse;

    public GestionarSimulacion(
            MotorSimulacion motor,
            GestionarControlSimulacion control,
            SseSimulacion sse
    ) {
        this.motor = motor;
        this.control = control;
        this.sse = sse;
    }

    public SnapshotSimulacion estado() {
        return motor.estadoActual();
    }

    public SseEmitter eventos() {
        return sse.conectar(
                motor.estadoActual()
        );
    }

    /**
     * El primer POST /api/simulacion/iniciar adquiere el control
     * automáticamente. No existe un paso previo de /api/control/adquirir.
     */
    public SnapshotSimulacion iniciar(
            String token,
            ConfiguracionSimulacion configuracion
    ) {

        AdquisicionControl adquisicion =
                control.adquirirParaSimulacion(token);

        try {
            return motor.iniciar(configuracion);
        } catch (RuntimeException e) {
            /*
             * Si esta llamada fue la que creó el control pero la simulación
             * no llegó a iniciar, no dejamos un propietario fantasma.
             *
             * Si la misma sesión ya era propietaria de una simulación activa,
             * no liberamos el control por un segundo intento de inicio.
             */
            if (adquisicion.adquiridoAhora()) {
                control.liberarInternamente();
            }
            throw e;
        }
    }

    public SnapshotSimulacion pausar(
            String token
    ) {
        control.verificarControladorActivo(token);
        return motor.pausar();
    }

    public SnapshotSimulacion reanudar(
            String token
    ) {
        control.verificarControladorActivo(token);
        return motor.reanudar();
    }

    public SnapshotSimulacion detener(
            String token
    ) {
        control.verificarControladorActivo(token);
        return motor.detener();
    }
}
