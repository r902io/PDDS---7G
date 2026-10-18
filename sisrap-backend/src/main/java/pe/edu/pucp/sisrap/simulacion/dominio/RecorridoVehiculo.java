package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.LocalDateTime;
import java.util.List;

/** Trayectoria ejecutada y archivada, independiente de las rutas propuestas por el planificador. */
public final class RecorridoVehiculo {
    private RecorridoVehiculo() { }

    public record Resumen(String idVehiculo, String tipo, int puntos,
                          double distanciaRecorridaKm, LocalDateTime primerRegistro,
                          LocalDateTime ultimoRegistro) { }

    public record Punto(LocalDateTime instante, int x, int y, String estado,
                        String fase, String evento, int cargaPaquetes,
                        Long pedidoObjetivo, double distanciaTramoKm) {
        public Punto {
            if (instante == null || x < 0 || y < 0 || cargaPaquetes < 0
                    || !Double.isFinite(distanciaTramoKm) || distanciaTramoKm < 0) {
                throw new IllegalArgumentException("Punto de recorrido inválido");
            }
        }
    }

    public record Detalle(long idSimulacion, String idVehiculo, String tipo,
                          double distanciaRecorridaKm, List<Punto> puntos) {
        public Detalle {
            puntos = List.copyOf(puntos);
        }
    }

    public record Registro(String idVehiculo, Punto punto) { }
}
