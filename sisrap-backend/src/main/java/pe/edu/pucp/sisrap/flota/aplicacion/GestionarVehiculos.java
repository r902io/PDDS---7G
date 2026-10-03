package pe.edu.pucp.sisrap.flota.aplicacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import pe.edu.pucp.sisrap.flota.dominio.AveriaVehiculo;
import pe.edu.pucp.sisrap.flota.dominio.ConflictoVehiculoException;
import pe.edu.pucp.sisrap.flota.dominio.EstadoVehiculo;
import pe.edu.pucp.sisrap.flota.dominio.MantenimientoVehiculo;
import pe.edu.pucp.sisrap.flota.dominio.RepositorioVehiculos;
import pe.edu.pucp.sisrap.flota.dominio.TipoAveria;
import pe.edu.pucp.sisrap.flota.dominio.TipoMantenimiento;
import pe.edu.pucp.sisrap.flota.dominio.VehiculoOperativo;
import pe.edu.pucp.sisrap.sesion.aplicacion.GestionarSesiones;

@Service
public final class GestionarVehiculos {

    private final RepositorioVehiculos repositorio;
    private final GestionarSesiones sesiones;

    public GestionarVehiculos(
            RepositorioVehiculos repositorio,
            GestionarSesiones sesiones
    ) {
        this.repositorio = repositorio;
        this.sesiones = sesiones;
    }

    public List<VehiculoOperativo> listar() {
        return repositorio.listar();
    }

    public VehiculoOperativo buscar(
            String idVehiculo
    ) {

        return repositorio.buscar(idVehiculo)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "No existe el vehículo "
                                        + idVehiculo
                        )
                );
    }

    public List<AveriaVehiculo> listarAverias(
            String idVehiculo
    ) {

        buscar(idVehiculo);

        return repositorio.listarAverias(
                idVehiculo
        );
    }

    public AveriaVehiculo registrarAveria(
            String tokenSesion,
            String idVehiculo,
            TipoAveria tipo,
            LocalDateTime fechaOcurrencia,
            LocalDateTime horaRetornoEstimada
    ) {

        validarSesion(tokenSesion);

        if (tipo == null) {
            throw new IllegalArgumentException(
                    "El tipo de avería es obligatorio"
            );
        }

        if (fechaOcurrencia == null) {
            throw new IllegalArgumentException(
                    "La fecha de ocurrencia es obligatoria"
            );
        }

        if (horaRetornoEstimada != null
                && !horaRetornoEstimada
                .isAfter(fechaOcurrencia)) {

            throw new IllegalArgumentException(
                    "La hora de retorno debe ser posterior "
                            + "a la avería"
            );
        }

        return repositorio.registrarAveria(
                idVehiculo,
                tipo,
                fechaOcurrencia,
                horaRetornoEstimada
        );
    }

    public AveriaVehiculo resolverAveria(
            String tokenSesion,
            String idVehiculo,
            long idIncidencia
    ) {

        validarSesion(tokenSesion);

        if (idIncidencia <= 0) {
            throw new IllegalArgumentException(
                    "Id de incidencia inválido"
            );
        }

        return repositorio.resolverAveria(
                idVehiculo,
                idIncidencia
        );
    }

    /*
     * Este método NO se expondrá directamente al frontend.
     * Posteriormente lo llamará MotorSimulacion.
     */
    public void actualizarDesdeSimulacion(
            String idVehiculo,
            int x,
            int y,
            EstadoVehiculo estado
    ) {

        if (estado == null) {
            throw new IllegalArgumentException(
                    "Estado obligatorio"
            );
        }

        repositorio.actualizarPosicionYEstado(
                idVehiculo,
                x,
                y,
                estado
        );
    }

    private void validarSesion(String token) {

        try {
            sesiones.validar(token);
        } catch (IllegalArgumentException e) {
            throw new SesionNoAutorizadaException(
                    e.getMessage()
            );
        }
    }

    public static final class SesionNoAutorizadaException
            extends RuntimeException {

        public SesionNoAutorizadaException(
                String mensaje
        ) {
            super(mensaje);
        }
    }
    public List<MantenimientoVehiculo>
        listarMantenimientos(
                String idVehiculo
        ) {

        buscar(idVehiculo);

        return repositorio
                .listarMantenimientos(
                        idVehiculo
                );
        }

        public MantenimientoVehiculo registrarMantenimiento(
                String tokenSesion,
                String idVehiculo,
                TipoMantenimiento tipo,
                LocalDateTime fechaInicio,
                LocalDateTime fechaFin
        ) {

        validarSesion(tokenSesion);

        VehiculoOperativo vehiculo =
                buscar(idVehiculo);

        if (tipo == null) {

                throw new IllegalArgumentException(
                        "El tipo de mantenimiento "
                                + "es obligatorio"
                );
        }

        if (fechaInicio == null
                || fechaFin == null) {

                throw new IllegalArgumentException(
                        "Debe especificar inicio y fin"
                );
        }

        if (!fechaFin.isAfter(fechaInicio)) {

                throw new IllegalArgumentException(
                        "La fecha de fin debe ser "
                                + "posterior al inicio"
                );
        }

        if (vehiculo.estado()
                == EstadoVehiculo.EN_AVERIA) {

                throw new ConflictoVehiculoException(
                        "No se puede programar mantenimiento "
                                + "sobre un vehículo actualmente averiado"
                );
        }

        return repositorio
                .registrarMantenimiento(
                        idVehiculo,
                        tipo,
                        fechaInicio,
                        fechaFin
                );
        }


        public void cancelarMantenimiento(
                String tokenSesion,
                String idVehiculo,
                long idMantenimiento
        ) {

        validarSesion(tokenSesion);

        buscar(idVehiculo);

        if (idMantenimiento <= 0) {

                throw new IllegalArgumentException(
                        "Id de mantenimiento inválido"
                );
        }

        repositorio.cancelarMantenimiento(
                idVehiculo,
                idMantenimiento
        );
        }
}