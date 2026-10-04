package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RepositorioSimulacion {

    void prepararEjecucion(ConfiguracionSimulacion configuracion);

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

    void marcarPedidosEnRuta(Collection<Long> idsPedidos);

    void reencolarPedidos(Collection<Long> idsPedidos, LocalDateTime reloj);

    void entregarPedido(long idPedido, LocalDateTime fechaEntrega);

    void marcarPedidosRetrasados(LocalDateTime reloj);

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
