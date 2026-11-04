package pe.edu.pucp.sisrap.simulacion.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pe.edu.pucp.sisrap.pedido.dominio.EstadoPedido;
import pe.edu.pucp.sisrap.simulacion.dominio.EnvioSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EnvioSimulacion.Asignacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EnvioSimulacion.EstadoAsignacion;
import pe.edu.pucp.sisrap.simulacion.dominio.PaginaEnvios;
import pe.edu.pucp.sisrap.simulacion.dominio.PuntoSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.VentanaEnvios;
import pe.edu.pucp.sisrap.simulacion.dominio.VistaEnvios;

class ConsultaEnviosTest {

    private static final LocalDateTime RELOJ = LocalDateTime.of(2026, 10, 1, 12, 0);

    private static Asignacion linea(String unidad, EstadoAsignacion estado, int cantidad,
                                    LocalDateTime llegada, LocalDateTime entrega) {
        return new Asignacion(unidad, estado, cantidad, List.of(), List.of(), List.of(), List.of(),
                null, llegada, entrega != null, entrega);
    }

    private static EnvioSimulacion envio(long id, int cantidad, LocalDateTime limite, Asignacion... lineas) {
        int entregada = 0;
        for (Asignacion a : lineas) if (a.estado() == EstadoAsignacion.ENTREGADA) entregada += a.cantidad();
        return new EnvioSimulacion(id, "c" + id, new PuntoSimulacion(1, 1), 8, RELOJ.minusHours(8), limite,
                EstadoPedido.EN_RUTA, cantidad, entregada, entregada > 0 && entregada < cantidad, List.of(lineas));
    }

    private static PaginaEnvios consultar(VentanaEnvios ventana, int horas, EnvioSimulacion... envios) {
        return ConsultaEnvios.consultar(new VistaEnvios(1L, RELOJ, List.of(envios)), ventana, horas, null, 0, 100);
    }

    private static List<Long> ids(PaginaEnvios pagina) {
        return pagina.envios().stream().map(EnvioSimulacion::idPedido).toList();
    }

    @Test
    void sinVentanaMuestraTodosLosPedidosYSusProductosCompletos() {
        PaginaEnvios pagina = consultar(VentanaEnvios.TODOS, 4,
                envio(2, 5, RELOJ, linea(null, EstadoAsignacion.SIN_ASIGNAR, 5, null, null)),
                envio(1, 3, RELOJ, linea("TA01", EstadoAsignacion.ENTREGADA, 2, RELOJ, RELOJ)));

        assertEquals(2, pagina.totalPedidos());
        assertEquals(8, pagina.totalProductos());
    }

    @Test
    void entregadosIncluyeLosLimitesDeLaVentana() {
        PaginaEnvios pagina = consultar(VentanaEnvios.ENTREGADOS, 4,
                envio(1, 1, RELOJ, linea("TA01", EstadoAsignacion.ENTREGADA, 1, null, RELOJ.minusHours(4))),
                envio(2, 1, RELOJ, linea("TA02", EstadoAsignacion.ENTREGADA, 1, null, RELOJ)),
                envio(3, 1, RELOJ, linea("TA03", EstadoAsignacion.ENTREGADA, 1, null, RELOJ.minusHours(4).minusSeconds(1))));

        assertEquals(List.of(2L, 1L), ids(pagina));
    }

    @Test
    void entregadosCuentaSoloLosProductosEntregadosEnLaVentana() {
        EnvioSimulacion parcial = envio(1, 30, RELOJ,
                linea("TA01", EstadoAsignacion.ENTREGADA, 24, null, RELOJ.minusHours(1)),
                linea("TM01", EstadoAsignacion.EN_RUTA, 6, RELOJ.plusHours(1), null));

        PaginaEnvios pagina = consultar(VentanaEnvios.ENTREGADOS, 4, parcial);

        assertEquals(1, pagina.totalPedidos());
        assertEquals(24, pagina.totalProductos());
        assertTrue(pagina.envios().get(0).entregaParcial());
        assertEquals(1, pagina.envios().get(0).asignaciones().size());
    }

    @Test
    void unaFraccionEntregadaFueraDeLaVentanaNoSumaYNoEsParcialSiYaTermino() {
        EnvioSimulacion completo = envio(1, 10, RELOJ,
                linea("TA01", EstadoAsignacion.ENTREGADA, 6, null, RELOJ.minusHours(9)),
                linea("TB01", EstadoAsignacion.ENTREGADA, 4, null, RELOJ.minusHours(1)));

        PaginaEnvios pagina = consultar(VentanaEnvios.ENTREGADOS, 4, completo);

        assertEquals(4, pagina.totalProductos());
        assertFalse(pagina.envios().get(0).entregaParcial());
    }

    @Test
    void porEntregarUsaLaLlegadaEstimadaYSinUnidadElPlazo() {
        PaginaEnvios pagina = consultar(VentanaEnvios.POR_ENTREGAR, 4,
                envio(1, 2, RELOJ.plusHours(30), linea("TA01", EstadoAsignacion.EN_RUTA, 2, RELOJ.plusHours(3), null)),
                envio(2, 2, RELOJ.plusHours(1), linea("TA02", EstadoAsignacion.EN_RUTA, 2, RELOJ.plusHours(5), null)),
                envio(3, 3, RELOJ.plusHours(2), linea(null, EstadoAsignacion.SIN_ASIGNAR, 3, null, null)),
                envio(4, 3, RELOJ.plusHours(9), linea(null, EstadoAsignacion.SIN_ASIGNAR, 3, null, null)));

        assertEquals(List.of(3L, 1L), ids(pagina));
        assertEquals(2, pagina.totalPedidos());
        assertEquals(5, pagina.totalProductos());
    }

    @Test
    void porEntregarExcluyeLoEntregadoLoCanceladoYLoSinHoraEstimada() {
        PaginaEnvios pagina = consultar(VentanaEnvios.POR_ENTREGAR, 4,
                envio(1, 1, RELOJ, linea("TA01", EstadoAsignacion.ENTREGADA, 1, RELOJ.plusHours(1), RELOJ.plusHours(1))),
                envio(2, 1, RELOJ, linea("TA02", EstadoAsignacion.REASIGNADA, 1, RELOJ.plusHours(1), null)),
                envio(3, 1, RELOJ, linea("TA03", EstadoAsignacion.EN_RUTA, 1, null, null)));

        assertEquals(0, pagina.totalPedidos());
    }

    @Test
    void cuentaLasFraccionesDeUnPedidoQueLleganDentroDeLaVentana() {
        PaginaEnvios pagina = consultar(VentanaEnvios.POR_ENTREGAR, 4,
                envio(1, 10, RELOJ.plusHours(20),
                        linea("TA01", EstadoAsignacion.PROGRAMADA, 6, RELOJ.plusHours(2), null),
                        linea("TB01", EstadoAsignacion.EN_RUTA, 4, RELOJ.plusHours(6), null)));

        assertEquals(6, pagina.totalProductos());
        assertEquals(1, pagina.envios().get(0).asignaciones().size());
    }

    @Test
    void laBusquedaEsIndependienteDeLaVentanaYAfectaLosTotales() {
        VistaEnvios vista = new VistaEnvios(1L, RELOJ, List.of(
                envio(10, 2, RELOJ, linea("TA01", EstadoAsignacion.EN_RUTA, 2, RELOJ.plusHours(1), null)),
                envio(25, 3, RELOJ, linea("TM07", EstadoAsignacion.EN_RUTA, 3, RELOJ.plusHours(1), null))));

        assertEquals(List.of(25L), ids(ConsultaEnvios.consultar(vista, VentanaEnvios.POR_ENTREGAR, 4, " tm07 ", 0, 100)));
        assertEquals(List.of(10L), ids(ConsultaEnvios.consultar(vista, VentanaEnvios.TODOS, 4, "c10", 0, 100)));
        assertEquals(3, ConsultaEnvios.consultar(vista, VentanaEnvios.TODOS, 4, "25", 0, 100).totalProductos());
    }

    @Test
    void paginaSinPerderElTotal() {
        List<EnvioSimulacion> envios = new ArrayList<>();
        for (long id = 1; id <= 870; id++) {
            envios.add(envio(id, 1, RELOJ, linea(null, EstadoAsignacion.SIN_ASIGNAR, 1, null, null)));
        }
        VistaEnvios vista = new VistaEnvios(1L, RELOJ, envios);

        PaginaEnvios ultima = ConsultaEnvios.consultar(vista, VentanaEnvios.TODOS, 4, null, 8, 100);
        assertEquals(870, ultima.totalPedidos());
        assertEquals(70, ultima.envios().size());
        assertTrue(ConsultaEnvios.consultar(vista, VentanaEnvios.TODOS, 4, null, 9, 100).envios().isEmpty());
        assertEquals(870, ConsultaEnvios.consultar(vista, VentanaEnvios.TODOS, 4, null, 0, 2000).envios().size());
    }

    @Test
    void unPedidoSeConsultaCompletoSinImportarLaVentana() {
        VistaEnvios vista = new VistaEnvios(1L, RELOJ, List.of(
                envio(12, 1, RELOJ, linea("TA01", EstadoAsignacion.EN_RUTA, 1, RELOJ, null)),
                envio(123, 30, RELOJ,
                        linea("TA02", EstadoAsignacion.ENTREGADA, 24, null, RELOJ.minusHours(9)),
                        linea("TM01", EstadoAsignacion.EN_RUTA, 6, RELOJ.plusHours(1), null))));

        PaginaEnvios pagina = ConsultaEnvios.consultarPedido(vista, 123);

        assertEquals(List.of(123L), ids(pagina));
        assertEquals(2, pagina.envios().get(0).asignaciones().size());
        assertEquals(30, pagina.totalProductos());
        assertEquals(0, ConsultaEnvios.consultarPedido(vista, 999).totalPedidos());
    }

    @Test
    void sinCorridaLasVentanasQuedanVacias() {
        VistaEnvios vacia = new VistaEnvios(null, null, List.of());
        assertEquals(0, ConsultaEnvios.consultar(vacia, VentanaEnvios.ENTREGADOS, 4, null, 0, 10).totalPedidos());
        assertEquals(0, ConsultaEnvios.consultar(vacia, VentanaEnvios.TODOS, 4, null, 0, 10).totalPedidos());
    }

    @Test
    void rechazaParametrosInvalidos() {
        VistaEnvios vista = new VistaEnvios(1L, RELOJ, List.of());
        assertThrows(IllegalArgumentException.class,
                () -> ConsultaEnvios.consultar(vista, VentanaEnvios.ENTREGADOS, 0, null, 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> ConsultaEnvios.consultar(vista, VentanaEnvios.ENTREGADOS, 4, null, -1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> ConsultaEnvios.consultar(vista, VentanaEnvios.ENTREGADOS, 4, null, 0, 2001));
    }
}
