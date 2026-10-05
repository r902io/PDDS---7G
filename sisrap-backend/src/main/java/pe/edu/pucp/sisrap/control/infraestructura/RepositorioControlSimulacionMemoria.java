package pe.edu.pucp.sisrap.control.infraestructura;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Repository;

import pe.edu.pucp.sisrap.control.dominio.ControlSimulacion;
import pe.edu.pucp.sisrap.control.dominio.RepositorioControlSimulacion;

@Repository
public class RepositorioControlSimulacionMemoria
        implements RepositorioControlSimulacion {

    private final AtomicReference<ControlSimulacion> controlActual =
            new AtomicReference<>();

    @Override
    public Optional<ControlSimulacion> obtener() {
        return Optional.ofNullable(controlActual.get());
    }

    @Override
    public void guardar(ControlSimulacion control) {
        controlActual.set(control);
    }

    @Override
    public void liberar() {
        controlActual.set(null);
    }
}