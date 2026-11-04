package pe.edu.pucp.sisrap.simulacion.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.simulacion.aplicacion.EstimadorRuta.Parada;
import pe.edu.pucp.sisrap.simulacion.aplicacion.EstimadorRuta.ParadaEstimada;
import pe.edu.pucp.sisrap.simulacion.dominio.PuntoSimulacion;

class EstimadorRutaTest {

    private static final double KM_NODO = 1.0;
    private static final double VELOCIDAD = 10.0;
    private static final double SERVICIO = 0.5;

    private static PuntoSimulacion p(int x, int y) {
        return new PuntoSimulacion(x, y);
    }

    @Test
    void guardaSoloLasEsquinasDeUnTrayecto() {
        List<PuntoSimulacion> ruta = new ArrayList<>();
        for (PuntoSimulacion nodo : List.of(p(0, 0), p(1, 0), p(2, 0), p(2, 1), p(2, 2), p(3, 2))) {
            EstimadorRuta.agregarNodo(ruta, nodo);
        }
        assertEquals(List.of(p(0, 0), p(2, 0), p(2, 2), p(3, 2)), ruta);
    }

    @Test
    void conservaLaVueltaEnU() {
        List<PuntoSimulacion> ruta = new ArrayList<>();
        for (PuntoSimulacion nodo : List.of(p(0, 0), p(1, 0), p(2, 0), p(1, 0))) {
            EstimadorRuta.agregarNodo(ruta, nodo);
        }
        assertEquals(List.of(p(0, 0), p(2, 0), p(1, 0)), ruta);
    }

    @Test
    void unTrazadoEstimadoNuncaEsDiagonal() {
        List<PuntoSimulacion> ruta = new ArrayList<>(List.of(p(3, 4)));
        EstimadorRuta.agregarOrtogonal(ruta, p(7, 9));
        assertEquals(List.of(p(3, 4), p(7, 4), p(7, 9)), ruta);
        assertOrtogonal(ruta);
    }

    @Test
    void conTramoCalculadoUsaLaSalidaDeSuUltimoNodo() {
        LocalDateTime reloj = LocalDateTime.of(2026, 10, 1, 8, 0);
        LocalDateTime ultimaSalida = reloj.plusMinutes(30);
        assertEquals(reloj.plusMinutes(36),
                EstimadorRuta.llegadaAlDestino(reloj, ultimaSalida, 0.1, 0, 99, VELOCIDAD));
    }

    @Test
    void sinTramoCalculadoAvanzaConLaVelocidadDeLaUnidad() {
        LocalDateTime reloj = LocalDateTime.of(2026, 10, 1, 8, 0);
        assertEquals(reloj.plusHours(2),
                EstimadorRuta.llegadaAlDestino(reloj, null, 0.1, 0, 20, VELOCIDAD));
    }

    @Test
    void laEsperaDelTrasvaseSumaALaLlegada() {
        LocalDateTime reloj = LocalDateTime.of(2026, 10, 1, 8, 0);
        assertEquals(reloj.plusMinutes(90),
                EstimadorRuta.llegadaAlDestino(reloj, null, 0.1, 0.5, 10, VELOCIDAD));
    }

    @Test
    void elRefrigerioRetrasaLaLlegadaEstimada() {
        // Turno de 07:00 a 15:00: refrigerio de 11:00 a 12:00.
        LocalDateTime reloj = LocalDateTime.of(2026, 10, 1, 10, 30);
        assertEquals(LocalDateTime.of(2026, 10, 1, 12, 30),
                EstimadorRuta.llegadaAlDestino(reloj, null, 0.1, 0, 10, VELOCIDAD));
    }

    @Test
    void unaSalidaYaPasadaNoProduceUnaLlegadaAnteriorAlReloj() {
        LocalDateTime reloj = LocalDateTime.of(2026, 10, 1, 8, 0);
        LocalDateTime llegada = EstimadorRuta.llegadaAlDestino(reloj, reloj.minusHours(1), 0.1, 0, 10, VELOCIDAD);
        assertEquals(reloj.plusHours(1), llegada);
    }

    @Test
    void estimaCadaParadaConServicioYDistanciaManhattan() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 1, 8, 0);
        Parada actual = new Parada(1, new Nodo(0, 0), "c1");
        List<ParadaEstimada> estimadas = EstimadorRuta.estimarParadas(base, new Nodo(0, 0), actual, SERVICIO,
                List.of(), List.of(new Parada(2, new Nodo(3, 7), "c2"), new Parada(3, new Nodo(3, 12), "c3")),
                SERVICIO, KM_NODO, VELOCIDAD);

        // Del cliente actual: 30 min de descarga y 10 km a 10 km/h.
        assertEquals(base.plusMinutes(90), estimadas.get(0).llegada());
        // Luego otros 30 min de descarga y 5 km.
        assertEquals(base.plusMinutes(150), estimadas.get(1).llegada());
        assertEquals(List.of(p(0, 0), p(3, 0), p(3, 7)), estimadas.get(0).ruta());
        assertEquals(List.of(p(0, 0), p(3, 0), p(3, 12)), estimadas.get(1).ruta());
    }

    @Test
    void lasFraccionesDeUnClienteSeDescarganEnLaMismaParada() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 1, 8, 0);
        List<ParadaEstimada> estimadas = EstimadorRuta.estimarParadas(base, new Nodo(0, 0), null, 0, List.of(),
                List.of(new Parada(1, new Nodo(5, 0), "c1"), new Parada(2, new Nodo(5, 0), "c1")),
                SERVICIO, KM_NODO, VELOCIDAD);
        assertEquals(estimadas.get(0).llegada(), estimadas.get(1).llegada());
    }

    @Test
    void desdeElAlmacenNoSumaDescargaAlPrimerTramo() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 1, 8, 0);
        List<ParadaEstimada> estimadas = EstimadorRuta.estimarParadas(base, new Nodo(0, 0), null, 0, List.of(),
                List.of(new Parada(1, new Nodo(5, 0), "c1")), SERVICIO, KM_NODO, VELOCIDAD);
        assertEquals(base.plusMinutes(30), estimadas.get(0).llegada());
    }

    @Test
    void redondeaLosKilometrosSinValoresNegativos() {
        assertEquals(1.24, EstimadorRuta.redondearKm(1.2449));
        assertEquals(0.0, EstimadorRuta.redondearKm(-0.5));
    }

    private static void assertOrtogonal(List<PuntoSimulacion> ruta) {
        for (int i = 1; i < ruta.size(); i++) {
            PuntoSimulacion a = ruta.get(i - 1), b = ruta.get(i);
            assertTrue(a.x() == b.x() || a.y() == b.y(), "Tramo diagonal: " + a + " a " + b);
        }
    }
}
