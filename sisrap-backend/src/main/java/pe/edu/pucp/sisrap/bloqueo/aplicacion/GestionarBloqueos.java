package pe.edu.pucp.sisrap.bloqueo.aplicacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import pe.edu.pucp.sisrap.bloqueo.dominio.BloqueoOperativo;
import pe.edu.pucp.sisrap.bloqueo.dominio.RepositorioBloqueos;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.simulacion.aplicacion.MotorSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EstadoSimulacion;
import pe.edu.pucp.sisrap.sesion.aplicacion.GestionarSesiones;

@Service
public final class GestionarBloqueos {

    private final RepositorioBloqueos repositorio;
    private final GestionarSesiones sesiones;
    private final MotorSimulacion motor;

    public GestionarBloqueos(RepositorioBloqueos repositorio,
                             GestionarSesiones sesiones, MotorSimulacion motor) {
        this.repositorio = repositorio;
        this.sesiones = sesiones;
        this.motor = motor;
    }

    private void exigirSimulacionActiva() {
        var estado = motor.estadoActual().estado();
        if (estado != EstadoSimulacion.EJECUTANDO && estado != EstadoSimulacion.PAUSADA) {
            throw new IllegalArgumentException("Los bloqueos se administran durante una simulación activa");
        }
    }

    public List<BloqueoOperativo> listar() {
        return repositorio.listar();
    }

    public BloqueoOperativo buscar(
            long idIncidencia
    ) {

        return repositorio.buscar(idIncidencia)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "No existe el bloqueo "
                                        + idIncidencia
                        )
                );
    }

    public BloqueoOperativo crear(
            String tokenSesion,
            LocalDateTime inicio,
            LocalDateTime fin,
            List<Nodo> vertices
    ) {

        validarSesion(tokenSesion);
        exigirSimulacionActiva();
        var snapshot = motor.estadoActual();
        if (inicio == null || fin == null || !fin.isAfter(inicio)
                || inicio.isBefore(snapshot.relojSimulado())
                || !inicio.isBefore(snapshot.fechaHoraFin())) {
            throw new IllegalArgumentException("El bloqueo debe comenzar desde el reloj simulado y antes de terminar la ejecución");
        }

        BloqueoOperativo creado = repositorio.crear(inicio, fin, vertices);
        motor.sincronizarBloqueos();
        return creado;
    }

    public void cancelar(
            String tokenSesion,
            long idIncidencia
    ) {

        validarSesion(tokenSesion);
        exigirSimulacionActiva();

        if (idIncidencia <= 0) {
            throw new IllegalArgumentException(
                    "Id de bloqueo inválido"
            );
        }

        if (!repositorio.cancelar(idIncidencia)) {
            throw new NoSuchElementException("No existe el bloqueo " + idIncidencia);
        }
        motor.sincronizarBloqueos();
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
}