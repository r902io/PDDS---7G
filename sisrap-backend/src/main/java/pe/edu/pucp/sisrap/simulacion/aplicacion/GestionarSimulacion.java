package pe.edu.pucp.sisrap.simulacion.aplicacion;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.ConfiguracionSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.SnapshotSimulacion;
import pe.edu.pucp.sisrap.simulacion.infraestructura.SseSimulacion;

@Service
public final class GestionarSimulacion {

    private final MotorSimulacion motor;
    private final GestionarControlSimulacion control;
    private final SseSimulacion sse;

    public GestionarSimulacion(
            MotorSimulacion motor,
            GestionarControlSimulacion control,
            SseSimulacion sse) {
        this.motor = motor;
        this.control = control;
        this.sse = sse;
    }

    public SnapshotSimulacion estado() {
        return motor.estadoActual();
    }

    public SseEmitter eventos() {
        return sse.conectar(motor.estadoActual());
    }

    public SnapshotSimulacion iniciar(
            String token,
            ConfiguracionSimulacion configuracion) {
        control.verificarControlador(token);
        return motor.iniciar(configuracion);
    }

    public SnapshotSimulacion pausar(String token) {
        control.verificarControlador(token);
        return motor.pausar();
    }

    public SnapshotSimulacion reanudar(String token) {
        control.verificarControlador(token);
        return motor.reanudar();
    }

    public SnapshotSimulacion detener(String token) {
        control.verificarControlador(token);
        return motor.detener();
    }

    public SnapshotSimulacion cambiarVelocidad(
            String token,
            double velocidad) {
        control.verificarControlador(token);
        return motor.cambiarVelocidad(velocidad);
    }
}
