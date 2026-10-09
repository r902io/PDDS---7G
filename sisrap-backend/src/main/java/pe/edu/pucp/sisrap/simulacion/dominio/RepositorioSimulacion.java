package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Ruta;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;

public interface RepositorioSimulacion {

    void prepararEjecucion(ConfiguracionSimulacion configuracion);

    void publicarPedidosHasta(LocalDateTime reloj);

    pe.edu.pucp.sisrap.pedido.dominio.PedidoOperativo registrarPedidoManual(
            String cliente, int cantidad, pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad prioridad,
            int x, int y, LocalDateTime fecha);

    void guardarPlan(long idSimulacion, LocalDateTime reloj, Solucion solucion,
                    List<Ruta> rutasAceptadas, double beta1, double beta2, double beta3);

    ResultadoCorrida consultarResultado(long idSimulacion);

    long crearEjecucion(
            ConfiguracionSimulacion configuracion,
            String algoritmo,
            String perfil);

    void actualizarEjecucion(
            long idSimulacion,
            EstadoSimulacion estado,
            LocalDateTime reloj);

    void finalizarEjecucion(
            long idSimulacion,
            EstadoSimulacion estado,
            LocalDateTime reloj,
            long tiempoRealMs);

    ContextoOperativo cargarContexto(
            String perfil,
            LocalDateTime reloj,
            LocalDateTime finVentana);

    Set<String> cargarNodosBloqueados(LocalDateTime reloj);

    int contarBloqueosActivos(LocalDateTime reloj);

    Map<String, VehiculoPersistido> cargarVehiculosPersistidos();

    List<VehiculoSnapshot> cargarVehiculosSnapshot();

    void sincronizarDisponibilidad(LocalDateTime reloj);

    long idPedidoVisible(long idPedidoPlanificado);

    void marcarPedidosEnRuta(Collection<Long> idsPedidos);

    void reencolarPedidos(Collection<Long> idsPedidos, LocalDateTime reloj);

    void entregarPedido(long idPedido, LocalDateTime fechaEntrega);

    void registrarArribo(long idPedido, LocalDateTime fechaArribo);

    void marcarPedidosRetrasados(LocalDateTime reloj);

    java.util.Optional<IncumplimientoPedido> primerVencimientoEntre(
            LocalDateTime desde, LocalDateTime hasta);

    java.util.Optional<IncumplimientoPedido> primerIncumplimiento(LocalDateTime reloj);

    void guardarColapso(long idSimulacion, IncumplimientoPedido incumplimiento);

    void actualizarPosiciones(Map<String, PuntoSimulacion> posiciones);

    void actualizarEstadoVehiculo(String idVehiculo, String estado);

    boolean consumirStock(String idAlmacen, int cantidad);

    void devolverStock(String idAlmacen, int cantidad);

    void recargarAlmacenesSiCorresponde(
            LocalDateTime desde,
            LocalDateTime hasta);

    ResumenPedidosSimulacion resumirPedidos(
            LocalDateTime inicio,
            LocalDateTime fin,
            LocalDateTime reloj);
}
