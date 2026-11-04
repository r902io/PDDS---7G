package pe.edu.pucp.sisrap.simulacion.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import pe.edu.pucp.sisrap.pedido.dominio.EstadoPedido;
import pe.edu.pucp.sisrap.simulacion.dominio.EnvioSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EnvioSimulacion.Asignacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EnvioSimulacion.EstadoAsignacion;
import pe.edu.pucp.sisrap.simulacion.dominio.PedidoCorrida;
import pe.edu.pucp.sisrap.simulacion.dominio.PuntoSimulacion;

class RegistroEnviosTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 8, 0);
    private static final List<PuntoSimulacion> RUTA = List.of(new PuntoSimulacion(27, 14), new PuntoSimulacion(30, 14));

    private final RegistroEnvios registro = new RegistroEnvios();

    private static PedidoCorrida pedido(long id, int cantidad, EstadoPedido estado) {
        return new PedidoCorrida(id, "c" + id, cantidad, 8, T0, T0.plusHours(8), 30, 14, estado, null);
    }

    private EnvioSimulacion envio(long id, int cantidad, EstadoPedido estado) {
        return registro.construir(List.of(pedido(id, cantidad, estado))).get(0);
    }

    @Test
    void unPedidoSinUnidadApareceSinAsignar() {
        EnvioSimulacion envio = envio(7, 3, EstadoPedido.PENDIENTE);

        assertEquals(1, envio.asignaciones().size());
        Asignacion linea = envio.asignaciones().get(0);
        assertEquals(EstadoAsignacion.SIN_ASIGNAR, linea.estado());
        assertNull(linea.idVehiculo());
        assertEquals(List.of("7-1", "7-2", "7-3"), linea.productos());
    }

    @Test
    void siguePasoAPasoUnEnvioHastaEntregarlo() {
        registro.asignar("TA01", 7, 7, 3);
        assertEquals(EstadoAsignacion.PROGRAMADA, envio(7, 3, EstadoPedido.EN_RUTA).asignaciones().get(0).estado());

        registro.registrarSalida("TA01", List.of(7L), T0);
        registro.estimar("TA01", 7, T0.plusHours(1), RUTA, RUTA, List.of());
        Asignacion enRuta = envio(7, 3, EstadoPedido.EN_RUTA).asignaciones().get(0);
        assertEquals(EstadoAsignacion.EN_RUTA, enRuta.estado());
        assertEquals(T0, enRuta.horaSalida());
        assertEquals(T0.plusHours(1), enRuta.horaLlegada());
        assertFalse(enRuta.llegadaReal());

        registro.registrarLlegada("TA01", 7, T0.plusMinutes(50), RUTA);
        registro.registrarEntrega(7, T0.plusMinutes(60));
        EnvioSimulacion entregado = envio(7, 3, EstadoPedido.ENTREGADO);
        Asignacion linea = entregado.asignaciones().get(0);
        assertEquals(EstadoAsignacion.ENTREGADA, linea.estado());
        assertEquals(T0.plusMinutes(50), linea.horaLlegada());
        assertTrue(linea.llegadaReal());
        assertEquals(3, entregado.cantidadEntregada());
        assertFalse(entregado.entregaParcial());
        assertEquals(1, entregado.asignaciones().size());
    }

    @Test
    void unaEstimacionNoReemplazaLaLlegadaReal() {
        registro.asignar("TA01", 7, 7, 3);
        registro.registrarSalida("TA01", List.of(7L), T0);
        registro.registrarLlegada("TA01", 7, T0.plusMinutes(50), RUTA);

        registro.estimar("TA01", 7, T0.plusHours(5), List.of(), List.of(), RUTA);

        Asignacion linea = envio(7, 3, EstadoPedido.EN_RUTA).asignaciones().get(0);
        assertEquals(EstadoAsignacion.EN_ATENCION, linea.estado());
        assertEquals(T0.plusMinutes(50), linea.horaLlegada());
        assertTrue(linea.rutaEstimada().isEmpty());
    }

    @Test
    void unPedidoEnDosUnidadesTieneUnaLineaPorUnidadConProductosDistintos() {
        registro.asignar("TA01", -1, 7, 24);
        registro.asignar("TM01", -2, 7, 6);

        List<Asignacion> lineas = envio(7, 30, EstadoPedido.EN_RUTA).asignaciones();

        assertEquals(2, lineas.size());
        assertEquals("TA01", lineas.get(0).idVehiculo());
        assertEquals("7-1", lineas.get(0).productos().get(0));
        assertEquals("7-24", lineas.get(0).productos().get(23));
        assertEquals(List.of("7-25", "7-26", "7-27", "7-28", "7-29", "7-30"), lineas.get(1).productos());
    }

    @Test
    void lasFraccionesQueViajanEnUnaUnidadSeMuestranEnUnaSolaLinea() {
        registro.asignar("TA01", -1, 7, 4);
        registro.asignar("TA01", -2, 7, 4);
        registro.asignar("TA01", -3, 7, 2);
        registro.registrarSalida("TA01", List.of(-1L, -2L, -3L), T0);
        for (long id : List.of(-1L, -2L, -3L)) registro.estimar("TA01", id, T0.plusHours(1), RUTA, RUTA, List.of());

        List<Asignacion> lineas = envio(7, 10, EstadoPedido.EN_RUTA).asignaciones();

        assertEquals(1, lineas.size());
        assertEquals(10, lineas.get(0).cantidad());
        assertEquals(10, lineas.get(0).productos().size());
        assertEquals("7-10", lineas.get(0).productos().get(9));
    }

    @Test
    void marcaEntregaParcialMientrasFaltanFracciones() {
        registro.asignar("TA01", -1, 7, 24);
        registro.asignar("TM01", -2, 7, 6);
        registro.registrarSalida("TA01", List.of(-1L), T0);
        registro.registrarLlegada("TA01", -1, T0.plusHours(1), RUTA);
        registro.registrarEntrega(-1, T0.plusHours(1));

        EnvioSimulacion envio = envio(7, 30, EstadoPedido.EN_RUTA);
        assertEquals(24, envio.cantidadEntregada());
        assertTrue(envio.entregaParcial());

        registro.registrarSalida("TM01", List.of(-2L), T0);
        registro.registrarLlegada("TM01", -2, T0.plusHours(2), RUTA);
        registro.registrarEntrega(-2, T0.plusHours(2));

        envio = envio(7, 30, EstadoPedido.ENTREGADO);
        assertEquals(30, envio.cantidadEntregada());
        assertFalse(envio.entregaParcial());
    }

    @Test
    void lasUnidadesFaltantesDeUnPedidoFraccionadoQuedanSinAsignar() {
        registro.asignar("TA01", -1, 7, 24);

        List<Asignacion> lineas = envio(7, 30, EstadoPedido.EN_RUTA).asignaciones();

        assertEquals(2, lineas.size());
        assertEquals(EstadoAsignacion.SIN_ASIGNAR, lineas.get(1).estado());
        assertEquals(6, lineas.get(1).cantidad());
        assertEquals("7-25", lineas.get(1).productos().get(0));
    }

    @Test
    void alReasignarConservaElHistorialYLosNumerosDeProducto() {
        registro.asignar("TA01", 7, 7, 3);
        registro.registrarSalida("TA01", List.of(7L), T0);
        registro.registrarReasignacion("TA01", List.of(7L), RUTA);

        registro.asignar("TM02", 7, 7, 3);

        List<Asignacion> lineas = envio(7, 3, EstadoPedido.EN_RUTA).asignaciones();
        assertEquals(2, lineas.size());
        assertEquals(EstadoAsignacion.REASIGNADA, lineas.get(0).estado());
        assertEquals(RUTA, lineas.get(0).rutaRecorrida());
        assertEquals(EstadoAsignacion.PROGRAMADA, lineas.get(1).estado());
        assertEquals(lineas.get(0).productos(), lineas.get(1).productos());
    }

    @Test
    void unaFraccionNuevaReutilizaLosNumerosDeUnaCancelada() {
        registro.asignar("TA01", -1, 7, 6);
        registro.registrarReasignacion("TA01", List.of(-1L), List.of());

        registro.asignar("TB01", -2, 7, 4);
        registro.asignar("TB02", -3, 7, 2);

        List<Asignacion> lineas = envio(7, 6, EstadoPedido.EN_RUTA).asignaciones();
        assertEquals(List.of("7-1", "7-2", "7-3", "7-4"), lineas.get(1).productos());
        assertEquals(List.of("7-5", "7-6"), lineas.get(2).productos());
    }

    @Test
    void elTrasvasePasaLosMismosProductosALaUnidadDeRescate() {
        registro.asignar("TA01", 7, 7, 3);
        registro.registrarSalida("TA01", List.of(7L), T0);
        registro.estimar("TA01", 7, T0.plusHours(1), RUTA, RUTA, List.of());
        registro.registrarDetencion("TA01", List.of(7L), RUTA);
        assertNull(envio(7, 3, EstadoPedido.EN_RUTA).asignaciones().get(0).horaLlegada());

        registro.asignar("TM03", 7, 7, 3);
        registro.registrarTrasvase("TM03", 7, -9);
        registro.registrarSalida("TM03", List.of(-9L), T0.plusHours(2));
        registro.registrarLlegada("TM03", -9, T0.plusHours(3), RUTA);
        registro.registrarEntrega(-9, T0.plusHours(3));

        EnvioSimulacion envio = envio(7, 3, EstadoPedido.ENTREGADO);
        assertEquals(2, envio.asignaciones().size());
        Asignacion origen = envio.asignaciones().get(0), rescate = envio.asignaciones().get(1);
        assertEquals(EstadoAsignacion.TRASVASADA, origen.estado());
        assertEquals(EstadoAsignacion.ENTREGADA, rescate.estado());
        assertEquals(origen.productos(), rescate.productos());
        assertEquals(3, envio.cantidadEntregada());
    }

    @Test
    void cancelarUnRescateNoAfectaALaUnidadAveriada() {
        registro.asignar("TA01", 7, 7, 3);
        registro.registrarSalida("TA01", List.of(7L), T0);
        registro.asignar("TM03", 7, 7, 3);

        registro.registrarReasignacion("TM03", List.of(7L), List.of());

        List<Asignacion> lineas = envio(7, 3, EstadoPedido.EN_RUTA).asignaciones();
        assertEquals(EstadoAsignacion.EN_RUTA, lineas.get(0).estado());
        assertEquals(EstadoAsignacion.REASIGNADA, lineas.get(1).estado());
    }

    @Test
    void dividirUnaCargaAveriadaRepartiendoSusProductos() {
        registro.asignar("TA01", 7, 7, 10);
        registro.registrarSalida("TA01", List.of(7L), T0);

        registro.dividir(7, List.of(new RegistroEnvios.Parte(-1, 8), new RegistroEnvios.Parte(-2, 2)));

        // Mientras sigan en la misma unidad se ven como un solo viaje.
        List<Asignacion> juntas = envio(7, 10, EstadoPedido.EN_RUTA).asignaciones();
        assertEquals(1, juntas.size());
        assertEquals(10, juntas.get(0).cantidad());

        registro.asignar("TM01", -1, 7, 8);
        registro.registrarTrasvase("TM01", -1, -1);

        List<Asignacion> separadas = envio(7, 10, EstadoPedido.EN_RUTA).asignaciones();
        assertEquals(3, separadas.size());
        Asignacion trasvasada = separadas.get(0), restante = separadas.get(1), rescate = separadas.get(2);
        assertEquals(EstadoAsignacion.TRASVASADA, trasvasada.estado());
        assertEquals("7-8", trasvasada.productos().get(7));
        assertEquals(EstadoAsignacion.EN_RUTA, restante.estado());
        assertEquals(List.of("7-9", "7-10"), restante.productos());
        assertEquals(T0, restante.horaSalida());
        assertEquals("TM01", rescate.idVehiculo());
        assertEquals(trasvasada.productos(), rescate.productos());
    }

    @Test
    void laEntregaEnCursoSeCompletaAunqueLaUnidadSeAverie() {
        registro.asignar("TA01", 7, 7, 3);
        registro.registrarSalida("TA01", List.of(7L), T0);
        registro.registrarLlegada("TA01", 7, T0.plusHours(1), RUTA);
        registro.registrarDetencion("TA01", List.of(7L), RUTA);

        registro.registrarEntrega(7, T0.plusHours(2));

        Asignacion linea = envio(7, 3, EstadoPedido.ENTREGADO).asignaciones().get(0);
        assertEquals(EstadoAsignacion.ENTREGADA, linea.estado());
        assertEquals(T0.plusHours(1), linea.horaLlegada());
    }

    @Test
    void ordenaDelPedidoMasRecienteAlMasAntiguo() {
        List<EnvioSimulacion> envios = registro.construir(List.of(
                pedido(1, 1, EstadoPedido.PENDIENTE), pedido(3, 1, EstadoPedido.PENDIENTE),
                pedido(2, 1, EstadoPedido.PENDIENTE)));
        assertEquals(List.of(3L, 2L, 1L), envios.stream().map(EnvioSimulacion::idPedido).toList());
    }

    @Test
    void reiniciarBorraLaCorridaAnterior() {
        registro.asignar("TA01", 7, 7, 3);
        registro.reiniciar();
        assertEquals(EstadoAsignacion.SIN_ASIGNAR, envio(7, 3, EstadoPedido.PENDIENTE).asignaciones().get(0).estado());
    }
}
