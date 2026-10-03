package pe.edu.pucp.sisrap.flota.dominio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RepositorioVehiculos {

    List<VehiculoOperativo> listar();

    Optional<VehiculoOperativo> buscar(
            String idVehiculo
    );


    // ============================================
    // AVERÍAS
    // ============================================

    List<AveriaVehiculo> listarAverias(
            String idVehiculo
    );

    AveriaVehiculo registrarAveria(
            String idVehiculo,
            TipoAveria tipoAveria,
            LocalDateTime fechaOcurrencia,
            LocalDateTime horaRetornoEstimada
    );

    AveriaVehiculo resolverAveria(
            String idVehiculo,
            long idIncidencia
    );


    // ============================================
    // MANTENIMIENTOS
    // ============================================

    List<MantenimientoVehiculo> listarMantenimientos(
            String idVehiculo
    );

    MantenimientoVehiculo registrarMantenimiento(
            String idVehiculo,
            TipoMantenimiento tipo,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin
    );

    void cancelarMantenimiento(
            String idVehiculo,
            long idMantenimiento
    );


    // ============================================
    // MOTOR DE SIMULACIÓN
    // ============================================

    void actualizarPosicionYEstado(
            String idVehiculo,
            int x,
            int y,
            EstadoVehiculo estado
    );
}