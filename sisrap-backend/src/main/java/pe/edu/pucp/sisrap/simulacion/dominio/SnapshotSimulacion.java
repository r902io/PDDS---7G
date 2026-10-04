package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.LocalDateTime;
import java.util.List;

public record SnapshotSimulacion(
        Long idSimulacion,
        EstadoSimulacion estado,
        EscenarioSimulacion escenario,
        LocalDateTime relojSimulado,
        LocalDateTime fechaHoraInicio,
        LocalDateTime fechaHoraFin,
        String algoritmo,
        String perfil,
        ResumenPedidosSimulacion pedidos,
        int bloqueosActivos,
        List<VehiculoSnapshot> vehiculos,
        String mensaje
) {

    public SnapshotSimulacion {
        vehiculos =
                vehiculos == null
                        ? List.of()
                        : List.copyOf(vehiculos);
    }

    public static SnapshotSimulacion detenida(
            String algoritmo,
            String perfil
    ) {
        return new SnapshotSimulacion(
                null,
                EstadoSimulacion.DETENIDA,
                null,
                null,
                null,
                null,
                algoritmo,
                perfil,
                new ResumenPedidosSimulacion(
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0
                ),
                0,
                List.of(),
                null
        );
    }
}
