package pe.edu.pucp.sisrap.configuracionoperativa.aplicacion;

import org.springframework.stereotype.Service;

import pe.edu.pucp.sisrap.configuracionoperativa.dominio.ConfiguracionOperativa;
import pe.edu.pucp.sisrap.configuracionoperativa.infraestructura.JdbcConfiguracionOperativa;
import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion;
import pe.edu.pucp.sisrap.simulacion.aplicacion.MotorSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EstadoSimulacion;

@Service
public class GestionarConfiguracionOperativa {
    private final JdbcConfiguracionOperativa repositorio;
    private final GestionarControlSimulacion control;
    private final MotorSimulacion motor;

    public GestionarConfiguracionOperativa(
            JdbcConfiguracionOperativa repositorio,
            GestionarControlSimulacion control,
            MotorSimulacion motor) {
        this.repositorio = repositorio;
        this.control = control;
        this.motor = motor;
    }

    public ConfiguracionOperativa consultar() {
        return repositorio.consultar();
    }

    public ConfiguracionOperativa actualizar(
            String token,
            ConfiguracionOperativa configuracion) {

        control.verificarControlador(token);

        synchronized (motor) {
            EstadoSimulacion estado = motor.estadoActual().estado();

            if (estado == EstadoSimulacion.EJECUTANDO
                    || estado == EstadoSimulacion.PAUSADA) {
                throw new IllegalStateException(
                    "No se puede cambiar la flota o los almacenes durante la simulación"
                );
            }

            return repositorio.actualizar(configuracion);
        }
    }
}
