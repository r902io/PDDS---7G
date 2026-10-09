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
import pe.edu.pucp.sisrap.simulacion.aplicacion.MotorSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EstadoSimulacion;

@Service
public final class GestionarVehiculos {

    private final RepositorioVehiculos repositorio;
    private final GestionarSesiones sesiones;
    private final MotorSimulacion motor;

    public GestionarVehiculos(RepositorioVehiculos repositorio,
                             GestionarSesiones sesiones, MotorSimulacion motor) {
        this.repositorio = repositorio;
        this.sesiones = sesiones;
        this.motor = motor;
    }

    private LocalDateTime exigirSimulacionActiva() {
        var s = motor.estadoActual();
        if (s.estado() != EstadoSimulacion.EJECUTANDO
                && s.estado() != EstadoSimulacion.PAUSADA) {
            throw new IllegalArgumentException("Las incidencias de vehículos se registran durante la simulación");
        }
        return s.relojSimulado();
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

        // La avería ocurre AHORA en el reloj de la simulación. Si el cliente
        // envió una duración basada en su reloj local, se conserva la duración.
        LocalDateTime instanteSolicitado = fechaOcurrencia;
        fechaOcurrencia = exigirSimulacionActiva();
        if (instanteSolicitado != null && horaRetornoEstimada != null
                && horaRetornoEstimada.isAfter(instanteSolicitado)) {
            horaRetornoEstimada = fechaOcurrencia.plus(
                    java.time.Duration.between(instanteSolicitado, horaRetornoEstimada));
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
        exigirSimulacionActiva();

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

        LocalDateTime reloj = exigirSimulacionActiva();
        if (fechaInicio == null) fechaInicio = reloj;
        if (fechaFin == null) fechaFin = fechaInicio.plusHours(1);
        if (fechaInicio.isBefore(reloj) ||
                !fechaInicio.isBefore(motor.estadoActual().fechaHoraFin())) {
            // Los clientes antiguos envían el reloj real; se normaliza su
            // duración a la fecha/hora virtual de la simulación activa.
            java.time.Duration duracion = java.time.Duration.between(fechaInicio, fechaFin);
            fechaInicio = reloj;
            fechaFin = duracion.isNegative() || duracion.isZero()
                    ? fechaInicio.plusHours(1) : fechaInicio.plus(duracion);
        }
        if (fechaInicio.isBefore(reloj) ||
                !fechaInicio.isBefore(motor.estadoActual().fechaHoraFin())) {
            throw new IllegalArgumentException("El mantenimiento debe programarse dentro de la simulación");
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
        exigirSimulacionActiva();

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