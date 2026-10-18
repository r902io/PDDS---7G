package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pe.edu.pucp.sisrap.flota.dominio.AveriaVehiculo;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Ruta;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;

public interface RepositorioSimulacion {

    void prepararEjecucion(ConfiguracionSimulacion configuracion);

    void publicarPedidosHasta(LocalDateTime reloj);

    List<Pedido> cargarPrevisionDiaria(LocalDate fecha, LocalDateTime disponibleDesde);


    pe.edu.pucp.sisrap.pedido.dominio.PedidoOperativo registrarPedidoManual(
            String cliente, int cantidad, pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad prioridad,
            int x, int y, LocalDateTime fecha);

    void registrarDiagnosticos(long idSimulacion, List<DiagnosticoPlanificacion> diagnosticos);

    void guardarPlan(long idSimulacion, LocalDateTime reloj, Solucion solucion,
                    List<Ruta> rutasAceptadas, double beta1, double beta2, double beta3);

    ResultadoCorrida consultarResultado(long idSimulacion);

    /** Guarda en lotes nodos recorridos y cambios de fase sin mezclarlos entre corridas. */
    void guardarRecorridos(long idSimulacion, List<RecorridoVehiculo.Registro> registros);

    List<RecorridoVehiculo.Resumen> resumirRecorridos(long idSimulacion);

    RecorridoVehiculo.Detalle consultarRecorrido(long idSimulacion, String idVehiculo);

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

    Map<String, AveriaVehiculo> cargarAveriasActivas(LocalDateTime reloj);

    List<VehiculoSnapshot> cargarVehiculosSnapshot();

    List<PerfilFlotaSimulacion> cargarPerfilesFlota();

    boolean activarIncidenciasProgramadas(LocalDateTime reloj);

    /** True when the vehicle's scheduled maintenance conflicts with this complete trip. */
    boolean tieneMantenimientoProgramado(String idVehiculo, LocalDateTime desde,
                                        LocalDateTime hasta);

    void sincronizarDisponibilidad(LocalDateTime reloj);

    long idPedidoVisible(long idPedidoPlanificado);

    /** Divide una carga ya embarcada en entregas parciales para su trasvase. */
    List<Pedido> dividirPedidoEnRuta(Pedido pedido, int capacidadPorVehiculo);

    /** Cambia la custodia de los paquetes al vehículo que los recoge. */
    List<Pedido> registrarTrasvase(List<Pedido> pedidos, String idVehiculo);

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
