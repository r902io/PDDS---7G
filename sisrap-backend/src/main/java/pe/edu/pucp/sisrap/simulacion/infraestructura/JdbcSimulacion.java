package pe.edu.pucp.sisrap.simulacion.infraestructura;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
import pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion;
import pe.edu.pucp.sisrap.parametros.infraestructura.JdbcParametros;
import pe.edu.pucp.sisrap.pedido.dominio.EstadoPedido;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.simulacion.dominio.ConfiguracionSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.ContextoOperativo;
import pe.edu.pucp.sisrap.simulacion.dominio.EstadoSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EscenarioSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.PuntoSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.RepositorioSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.ResumenPedidosSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.VehiculoPersistido;
import pe.edu.pucp.sisrap.simulacion.dominio.VehiculoSnapshot;

@Repository
public class JdbcSimulacion implements RepositorioSimulacion {

    private static final int INCREMENTO_COLAPSO_POR_DIA = 12;
    private static final long BLOQUE_IDS_SINTETICOS = 1_000_000L;

    private final DataSource fuente;

    /** Pedidos de la ejecución activa. Nunca se persisten en la tabla histórica. */
    private final Map<Long, PedidoSimulado> pedidosSimulados = new LinkedHashMap<>();
    private List<PlantillaHistorica> plantillasHistoricas = List.of();
    private Map<LocalDate, List<PlantillaHistorica>> plantillasPorDia = Map.of();
    private ConfiguracionSimulacion configuracionDemanda;
    private LocalDate ultimoDiaGenerado;
    private int medianaPedidosDiarios;

    public JdbcSimulacion(DataSource fuente) {
        this.fuente = fuente;
    }

    @Override
    public synchronized void prepararEjecucion(ConfiguracionSimulacion configuracion) {
        if (configuracion == null) {
            throw new IllegalArgumentException("La configuración de simulación es obligatoria");
        }

        try (Connection cn = fuente.getConnection()) {
            cn.setAutoCommit(false);
            try {
                int[] central = leerCentral(cn);

                inicializarModeloDemanda(cn, configuracion);

                try (PreparedStatement ps = cn.prepareStatement("""
                        UPDATE almacen
                        SET stock_actual = capacidad_maxima
                        WHERE tipo = 'INTERMEDIO'
                        """)) {
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = cn.prepareStatement("""
                        UPDATE vehiculo
                        SET estado = 'DISPONIBLE',
                            posicion_x = ?,
                            posicion_y = ?
                        """)) {
                    ps.setInt(1, central[0]);
                    ps.setInt(2, central[1]);
                    ps.executeUpdate();
                }

                cn.commit();
            } catch (RuntimeException | SQLException e) {
                cn.rollback();
                limpiarDemandaSimulada();
                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("No se pudo preparar la simulación", e);
            }
        } catch (SQLException e) {
            limpiarDemandaSimulada();
            throw new IllegalStateException("No se pudo preparar la simulación", e);
        }
    }

    @Override
    public long crearEjecucion(
            ConfiguracionSimulacion configuracion,
            String algoritmo,
            String perfil) {

        String sql = """
                INSERT INTO simulacion(
                    escenario,
                    algoritmo,
                    semilla_aleatoria,
                    fecha_inicio,
                    fecha_fin_programada,
                    estado,
                    reloj_simulado,
                    perfil
                )
                VALUES (
                    ?, ?, ?, ?, ?,
                    'EJECUTANDO', ?, ?
                )
                """;

        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement(
                     sql,
                     Statement.RETURN_GENERATED_KEYS
             )) {

            ps.setString(
                    1,
                    configuracion
                            .escenario()
                            .valorBaseDatos()
            );

            ps.setString(2, algoritmo);
            ps.setLong(3, configuracion.semilla());

            ps.setTimestamp(
                    4,
                    Timestamp.valueOf(
                            configuracion.fechaHoraInicio()
                    )
            );

            ps.setTimestamp(
                    5,
                    Timestamp.valueOf(
                            configuracion.fechaHoraFin()
                    )
            );

            ps.setTimestamp(
                    6,
                    Timestamp.valueOf(
                            configuracion.fechaHoraInicio()
                    )
            );

            ps.setString(7, perfil);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("No se generó id de simulación");
                }
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo registrar la simulación", e);
        }
    }

    @Override
    public void actualizarEjecucion(
            long idSimulacion,
            EstadoSimulacion estado,
            LocalDateTime reloj) {

        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     UPDATE simulacion
                     SET estado = ?,
                         reloj_simulado = ?
                     WHERE id_simulacion = ?
                     """)) {

            ps.setString(1, estado.name());
            ps.setTimestamp(2, reloj == null ? null : Timestamp.valueOf(reloj));
            ps.setLong(3, idSimulacion);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo actualizar la simulación", e);
        }
    }

    @Override
    public void finalizarEjecucion(
            long idSimulacion,
            EstadoSimulacion estado,
            LocalDateTime reloj,
            long tiempoRealMs) {

        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     UPDATE simulacion
                     SET estado = ?,
                         reloj_simulado = ?,
                         fecha_fin = ?,
                         tiempo_ejecucion_ms = ?
                     WHERE id_simulacion = ?
                     """)) {

            ps.setString(1, estado.name());
            ps.setTimestamp(2, reloj == null ? null : Timestamp.valueOf(reloj));
            ps.setTimestamp(3, reloj == null ? null : Timestamp.valueOf(reloj));
            ps.setLong(4, tiempoRealMs);
            ps.setLong(5, idSimulacion);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo finalizar la simulación", e);
        }
    }

    @Override
    public ContextoOperativo cargarContexto(
            String perfil,
            LocalDateTime reloj,
            LocalDateTime finVentana) {

        asegurarDemandaGeneradaHasta(reloj.toLocalDate());

        try (Connection cn = fuente.getConnection()) {
            cn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            cn.setAutoCommit(false);
            try {
                Configuracion configuracion = JdbcParametros.leer(cn, perfil, false);
                JdbcParametros.validar(configuracion);

                int[] dimensiones = leerDimensiones(cn);
                int ancho = dimensiones[0];
                int alto = dimensiones[1];

                List<Almacen> almacenes = leerAlmacenes(cn, ancho, alto);
                List<Vehiculo> vehiculos = leerVehiculos(cn, ancho, alto);
                List<Pedido> pedidos = leerPedidosSimuladosPlanificables(reloj, finVentana, ancho, alto);
                Set<String> bloqueados = leerNodosBloqueados(cn, reloj);

                ReglasPlanificacion reglas = ReglasPlanificacion.desde(
                        configuracion,
                        ancho,
                        alto);

                ContextoPlanificacion contexto = new ContextoPlanificacion(
                        pedidos,
                        vehiculos,
                        almacenes,
                        reloj,
                        reglas,
                        bloqueados,
                        true);

                cn.commit();
                return new ContextoOperativo(configuracion, contexto);
            } catch (RuntimeException | SQLException e) {
                cn.rollback();
                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("No se pudo construir el contexto operativo", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo construir el contexto operativo", e);
        }
    }

    @Override
    public Set<String> cargarNodosBloqueados(LocalDateTime reloj) {
        try (Connection cn = fuente.getConnection()) {
            return leerNodosBloqueados(cn, reloj);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron consultar los bloqueos", e);
        }
    }

    @Override
    public int contarBloqueosActivos(LocalDateTime reloj) {
        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     SELECT COUNT(*)
                     FROM incidencia i
                     JOIN bloqueo b ON b.id_incidencia = i.id_incidencia
                     WHERE i.activa = TRUE
                       AND b.fecha_inicio <= ?
                       AND b.fecha_fin > ?
                     """)) {
            ps.setTimestamp(1, Timestamp.valueOf(reloj));
            ps.setTimestamp(2, Timestamp.valueOf(reloj));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron contar los bloqueos", e);
        }
    }

    @Override
    public Map<String, VehiculoPersistido> cargarVehiculosPersistidos() {
        Map<String, VehiculoPersistido> salida = new HashMap<>();
        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     SELECT id_vehiculo, estado, posicion_x, posicion_y
                     FROM vehiculo
                     ORDER BY id_vehiculo
                     """);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                VehiculoPersistido v = new VehiculoPersistido(
                        rs.getString("id_vehiculo"),
                        rs.getString("estado"),
                        rs.getInt("posicion_x"),
                        rs.getInt("posicion_y"));
                salida.put(v.idVehiculo(), v);
            }
            return Map.copyOf(salida);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consultar el estado de la flota", e);
        }
    }

    @Override
    public List<VehiculoSnapshot> cargarVehiculosSnapshot() {
        List<VehiculoSnapshot> salida = new ArrayList<>();
        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     SELECT id_vehiculo, estado, posicion_x, posicion_y
                     FROM vehiculo
                     ORDER BY id_vehiculo
                     """);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                salida.add(new VehiculoSnapshot(
                        rs.getString("id_vehiculo"),
                        rs.getString("estado"),
                        rs.getInt("posicion_x"),
                        rs.getInt("posicion_y"),
                        null,
                        List.of()));
            }
            return List.copyOf(salida);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consultar la flota", e);
        }
    }

    @Override
    public void sincronizarDisponibilidad(LocalDateTime reloj) {
        try (Connection cn = fuente.getConnection()) {
            cn.setAutoCommit(false);
            try {
                finalizarAveriasConRetornoCumplido(cn, reloj);
                Map<String, PuntoSimulacion> averiados = leerAveriasActivas(cn, reloj);
                Set<String> mantenimientos = leerMantenimientosActivos(cn, reloj);
                Map<String, VehiculoPersistido> vehiculos = leerVehiculosPersistidos(cn);

                try (PreparedStatement psEstado = cn.prepareStatement("""
                             UPDATE vehiculo
                             SET estado = ?
                             WHERE id_vehiculo = ?
                             """);
                     PreparedStatement psAveria = cn.prepareStatement("""
                             UPDATE vehiculo
                             SET estado = 'EN_AVERIA',
                                 posicion_x = ?,
                                 posicion_y = ?
                             WHERE id_vehiculo = ?
                             """)) {

                    for (VehiculoPersistido v : vehiculos.values()) {
                        PuntoSimulacion ubicacionAveria = averiados.get(v.idVehiculo());
                        if (ubicacionAveria != null) {
                            if (!"EN_AVERIA".equals(v.estado())
                                    || v.x() != ubicacionAveria.x()
                                    || v.y() != ubicacionAveria.y()) {
                                psAveria.setInt(1, ubicacionAveria.x());
                                psAveria.setInt(2, ubicacionAveria.y());
                                psAveria.setString(3, v.idVehiculo());
                                psAveria.addBatch();
                            }
                            continue;
                        }

                        if (mantenimientos.contains(v.idVehiculo())) {
                            if (!"EN_MANTENIMIENTO".equals(v.estado())) {
                                psEstado.setString(1, "EN_MANTENIMIENTO");
                                psEstado.setString(2, v.idVehiculo());
                                psEstado.addBatch();
                            }
                            continue;
                        }

                        if ("EN_AVERIA".equals(v.estado())
                                || "EN_MANTENIMIENTO".equals(v.estado())) {
                            psEstado.setString(1, "DISPONIBLE");
                            psEstado.setString(2, v.idVehiculo());
                            psEstado.addBatch();
                        }
                    }

                    psAveria.executeBatch();
                    psEstado.executeBatch();
                }

                cn.commit();
            } catch (RuntimeException | SQLException e) {
                cn.rollback();
                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("No se pudo sincronizar disponibilidad", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo sincronizar disponibilidad", e);
        }
    }

    @Override
    public synchronized void marcarPedidosEnRuta(Collection<Long> idsPedidos) {
        if (idsPedidos == null || idsPedidos.isEmpty()) {
            return;
        }
        for (Long id : idsPedidos) {
            PedidoSimulado pedido = pedidosSimulados.get(id);
            if (pedido != null
                    && (pedido.estado == EstadoPedido.PENDIENTE
                    || pedido.estado == EstadoPedido.REASIGNADO
                    || pedido.estado == EstadoPedido.RETRASADO)) {
                pedido.estado = EstadoPedido.EN_RUTA;
            }
        }
    }

    @Override
    public synchronized void reencolarPedidos(
            Collection<Long> idsPedidos,
            LocalDateTime reloj) {

        if (idsPedidos == null || idsPedidos.isEmpty()) {
            return;
        }

        for (Long id : idsPedidos) {
            PedidoSimulado pedido = pedidosSimulados.get(id);
            if (pedido == null || pedido.estado != EstadoPedido.EN_RUTA) {
                continue;
            }
            pedido.estado = pedido.fechaLimite().isBefore(reloj)
                    ? EstadoPedido.RETRASADO
                    : EstadoPedido.REASIGNADO;
        }
    }

    @Override
    public synchronized void entregarPedido(
            long idPedido,
            LocalDateTime fechaEntrega) {

        PedidoSimulado pedido = pedidosSimulados.get(idPedido);
        if (pedido == null) {
            throw new IllegalArgumentException("Pedido simulado inexistente: " + idPedido);
        }
        if (pedido.estado == EstadoPedido.EN_RUTA) {
            pedido.estado = EstadoPedido.ENTREGADO;
            pedido.fechaEntregaReal = fechaEntrega;
        }
    }

    @Override
    public synchronized void marcarPedidosRetrasados(LocalDateTime reloj) {
        for (PedidoSimulado pedido : pedidosSimulados.values()) {
            if ((pedido.estado == EstadoPedido.PENDIENTE
                    || pedido.estado == EstadoPedido.REASIGNADO)
                    && !pedido.fechaLlegada.isAfter(reloj)
                    && pedido.fechaLimite().isBefore(reloj)) {
                pedido.estado = EstadoPedido.RETRASADO;
            }
        }
    }

    @Override
    public void actualizarPosiciones(Map<String, PuntoSimulacion> posiciones) {
        if (posiciones == null || posiciones.isEmpty()) {
            return;
        }
        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     UPDATE vehiculo
                     SET posicion_x = ?,
                         posicion_y = ?
                     WHERE id_vehiculo = ?
                     """)) {
            for (var entry : posiciones.entrySet()) {
                ps.setInt(1, entry.getValue().x());
                ps.setInt(2, entry.getValue().y());
                ps.setString(3, entry.getKey());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron actualizar posiciones", e);
        }
    }

    @Override
    public void actualizarEstadoVehiculo(String idVehiculo, String estado) {
        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     UPDATE vehiculo
                     SET estado = ?
                     WHERE id_vehiculo = ?
                     """)) {
            ps.setString(1, estado);
            ps.setString(2, idVehiculo);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Vehículo inexistente: " + idVehiculo);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo actualizar el vehículo", e);
        }
    }

    @Override
    public boolean consumirStock(String idAlmacen, int cantidad) {
        if (cantidad <= 0) {
            return true;
        }

        try (Connection cn = fuente.getConnection()) {
            cn.setAutoCommit(false);
            try {
                Integer capacidad;
                try (PreparedStatement ps = cn.prepareStatement("""
                        SELECT capacidad_maxima
                        FROM almacen
                        WHERE id_almacen = ?
                        FOR UPDATE
                        """)) {
                    ps.setString(1, idAlmacen);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Almacén inexistente: " + idAlmacen);
                        }
                        capacidad = (Integer) rs.getObject(1);
                    }
                }

                if (capacidad == null) {
                    cn.commit();
                    return true;
                }

                try (PreparedStatement ps = cn.prepareStatement("""
                        UPDATE almacen
                        SET stock_actual = stock_actual - ?
                        WHERE id_almacen = ?
                          AND stock_actual >= ?
                        """)) {
                    ps.setInt(1, cantidad);
                    ps.setString(2, idAlmacen);
                    ps.setInt(3, cantidad);
                    boolean consumido = ps.executeUpdate() == 1;
                    cn.commit();
                    return consumido;
                }
            } catch (RuntimeException | SQLException e) {
                cn.rollback();
                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("No se pudo consumir stock", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consumir stock", e);
        }
    }

    @Override
    public void devolverStock(String idAlmacen, int cantidad) {
        if (cantidad <= 0) {
            return;
        }
        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     UPDATE almacen
                     SET stock_actual = LEAST(capacidad_maxima, stock_actual + ?)
                     WHERE id_almacen = ?
                       AND capacidad_maxima IS NOT NULL
                     """)) {
            ps.setInt(1, cantidad);
            ps.setString(2, idAlmacen);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo devolver stock", e);
        }
    }

    @Override
    public void recargarAlmacenesSiCorresponde(
            LocalDateTime desde,
            LocalDateTime hasta) {

        if (desde == null || hasta == null || !hasta.isAfter(desde)) {
            return;
        }

        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     SELECT id_almacen, hora_recarga
                     FROM almacen
                     WHERE tipo = 'INTERMEDIO'
                       AND hora_recarga IS NOT NULL
                     """);
             ResultSet rs = ps.executeQuery()) {

            List<String> recargar = new ArrayList<>();
            while (rs.next()) {
                Time t = rs.getTime("hora_recarga");
                if (t != null && cruzaHora(desde, hasta, t.toLocalTime())) {
                    recargar.add(rs.getString("id_almacen"));
                }
            }

            if (recargar.isEmpty()) {
                return;
            }

            try (PreparedStatement update = cn.prepareStatement("""
                    UPDATE almacen
                    SET stock_actual = capacidad_maxima
                    WHERE id_almacen = ?
                    """)) {
                for (String id : recargar) {
                    update.setString(1, id);
                    update.addBatch();
                }
                update.executeBatch();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo recargar almacenes", e);
        }
    }

    @Override
    public synchronized ResumenPedidosSimulacion resumirPedidos(
            LocalDateTime inicio,
            LocalDateTime fin,
            LocalDateTime reloj) {

        int total = 0;
        int futuros = 0;
        int pendientes = 0;
        int enRuta = 0;
        int reasignados = 0;
        int retrasados = 0;
        int entregados = 0;

        for (PedidoSimulado pedido : pedidosSimulados.values()) {
            if (pedido.fechaLlegada.isBefore(inicio)
                    || !pedido.fechaLlegada.isBefore(fin)) {
                continue;
            }

            total++;

            if (pedido.fechaLlegada.isAfter(reloj)) {
                futuros++;
                continue;
            }

            switch (pedido.estado) {
                case PENDIENTE -> pendientes++;
                case EN_RUTA -> enRuta++;
                case REASIGNADO -> reasignados++;
                case RETRASADO -> retrasados++;
                case ENTREGADO -> entregados++;
            }
        }

        return new ResumenPedidosSimulacion(
                total,
                futuros,
                pendientes,
                enRuta,
                reasignados,
                retrasados,
                entregados
        );
    }

    private List<Almacen> leerAlmacenes(Connection cn, int ancho, int alto) throws SQLException {
        List<Almacen> salida = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement("""
                SELECT id_almacen, ubicacion_x, ubicacion_y, capacidad_maxima, stock_actual
                FROM almacen
                ORDER BY id_almacen
                """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Integer capacidad = (Integer) rs.getObject("capacidad_maxima");
                Integer stock = (Integer) rs.getObject("stock_actual");
                if (capacidad != null && (capacidad <= 0 || stock == null || stock < 0 || stock > capacidad)) {
                    throw new IllegalArgumentException("Stock/capacidad inválidos en " + rs.getString("id_almacen"));
                }
                salida.add(new Almacen(
                        rs.getString("id_almacen"),
                        nodo(rs.getInt("ubicacion_x"), rs.getInt("ubicacion_y"), ancho, alto),
                        capacidad,
                        stock));
            }
        }
        long centrales = salida.stream().filter(a -> a.getCapacidadMaxima() == null).count();
        if (centrales != 1) {
            throw new IllegalArgumentException("Se requiere exactamente un almacén central");
        }
        return List.copyOf(salida);
    }

    private List<Vehiculo> leerVehiculos(Connection cn, int ancho, int alto) throws SQLException {
        List<Vehiculo> salida = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement("""
                SELECT id_vehiculo, capacidad_paquetes, velocidad_kmh, costo_por_km,
                       estado, posicion_x, posicion_y
                FROM vehiculo
                ORDER BY id_vehiculo
                """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Vehiculo v = new Vehiculo(
                        rs.getString("id_vehiculo"),
                        rs.getInt("capacidad_paquetes"),
                        rs.getDouble("velocidad_kmh"),
                        rs.getDouble("costo_por_km"),
                        nodo(rs.getInt("posicion_x"), rs.getInt("posicion_y"), ancho, alto));
                v.setDisponible("DISPONIBLE".equals(rs.getString("estado")));
                salida.add(v);
            }
        }
        if (salida.isEmpty()) {
            throw new IllegalArgumentException("Flota vacía");
        }
        return List.copyOf(salida);
    }

    private synchronized List<Pedido> leerPedidosSimuladosPlanificables(
            LocalDateTime reloj,
            LocalDateTime finVentana,
            int ancho,
            int alto) {

        List<Pedido> salida = new ArrayList<>();

        for (PedidoSimulado pedido : pedidosSimulados.values()) {
            if (pedido.fechaLlegada.isAfter(reloj)
                    || !pedido.fechaLlegada.isBefore(finVentana)) {
                continue;
            }

            if (pedido.estado != EstadoPedido.PENDIENTE
                    && pedido.estado != EstadoPedido.REASIGNADO
                    && pedido.estado != EstadoPedido.RETRASADO) {
                continue;
            }

            salida.add(pedido.aPedido(ancho, alto));
        }

        salida.sort(
                Comparator.comparing(Pedido::getFechaLlegada)
                        .thenComparing(Pedido::getIdPedido)
        );

        return List.copyOf(salida);
    }

    /**
     * Carga el histórico persistido y lo deja únicamente como fuente estadística.
     * Los pedidos de la simulación se mantienen separados, en memoria.
     */
    private void inicializarModeloDemanda(
            Connection cn,
            ConfiguracionSimulacion configuracion) throws SQLException {

        Map<LocalDate, List<PlantillaHistorica>> porDia = new LinkedHashMap<>();
        List<PlantillaHistorica> todas = new ArrayList<>();

        try (PreparedStatement ps = cn.prepareStatement("""
                SELECT id_cliente,
                       cantidad_qq,
                       prioridad,
                       horas_limite,
                       fecha_llegada,
                       ubicacion_x,
                       ubicacion_y
                FROM pedido
                ORDER BY fecha_llegada, id_pedido
                """);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                LocalDateTime llegada =
                        rs.getTimestamp("fecha_llegada").toLocalDateTime();

                PlantillaHistorica plantilla = new PlantillaHistorica(
                        rs.getString("id_cliente"),
                        rs.getInt("cantidad_qq"),
                        TipoPrioridad.valueOf(rs.getString("prioridad")),
                        rs.getInt("horas_limite"),
                        llegada.toLocalTime(),
                        rs.getInt("ubicacion_x"),
                        rs.getInt("ubicacion_y")
                );

                todas.add(plantilla);
                porDia.computeIfAbsent(
                                llegada.toLocalDate(),
                                ignorado -> new ArrayList<>()
                        )
                        .add(plantilla);
            }
        }

        if (todas.isEmpty()) {
            throw new IllegalArgumentException(
                    "No existen pedidos históricos. Cargue al menos un archivo de ventas antes de simular"
            );
        }

        List<Integer> cantidadesDiarias = porDia.values().stream()
                .map(List::size)
                .sorted()
                .toList();

        int mediana = cantidadesDiarias.get(cantidadesDiarias.size() / 2);
        if (cantidadesDiarias.size() % 2 == 0) {
            int inferior = cantidadesDiarias.get(cantidadesDiarias.size() / 2 - 1);
            mediana = Math.max(1, (inferior + mediana) / 2);
        }

        Map<LocalDate, List<PlantillaHistorica>> inmutable = new LinkedHashMap<>();
        porDia.forEach((fecha, pedidos) ->
                inmutable.put(fecha, List.copyOf(pedidos))
        );

        pedidosSimulados.clear();
        plantillasHistoricas = List.copyOf(todas);
        plantillasPorDia = new LinkedHashMap<>(inmutable);
        configuracionDemanda = configuracion;
        ultimoDiaGenerado = null;
        medianaPedidosDiarios = Math.max(1, mediana);
    }

    private synchronized void limpiarDemandaSimulada() {
        pedidosSimulados.clear();
        plantillasHistoricas = List.of();
        plantillasPorDia = Map.of();
        configuracionDemanda = null;
        ultimoDiaGenerado = null;
        medianaPedidosDiarios = 0;
    }

    /** Genera, de forma determinista, los días que el reloj ya alcanzó. */
    private synchronized void asegurarDemandaGeneradaHasta(LocalDate fechaObjetivo) {
        if (configuracionDemanda == null || fechaObjetivo == null) {
            return;
        }

        LocalDate inicio = configuracionDemanda.fechaInicio();
        LocalDate ultimoPermitido =
                configuracionDemanda.fechaHoraFin().toLocalDate().minusDays(1);

        if (fechaObjetivo.isBefore(inicio)) {
            return;
        }

        LocalDate limite = fechaObjetivo.isAfter(ultimoPermitido)
                ? ultimoPermitido
                : fechaObjetivo;

        LocalDate siguiente = ultimoDiaGenerado == null
                ? inicio
                : ultimoDiaGenerado.plusDays(1);

        while (!siguiente.isAfter(limite)) {
            generarDiaSimulado(siguiente);
            ultimoDiaGenerado = siguiente;
            siguiente = siguiente.plusDays(1);
        }
    }

    private void generarDiaSimulado(LocalDate fecha) {
        long indiceDia = ChronoUnit.DAYS.between(
                configuracionDemanda.fechaInicio(),
                fecha
        );

        Random random = new Random(
                mezclarSemilla(configuracionDemanda.semilla(), fecha.toEpochDay())
        );

        List<PlantillaHistorica> seleccionadas;

        if (configuracionDemanda.escenario()
                == EscenarioSimulacion.COLAPSO_LOGISTICO) {

            long cantidadObjetivoLong = Math.addExact(
                    medianaPedidosDiarios,
                    Math.multiplyExact(indiceDia, INCREMENTO_COLAPSO_POR_DIA)
            );

            if (cantidadObjetivoLong > Integer.MAX_VALUE) {
                throw new IllegalStateException("La demanda de colapso excede el límite soportado");
            }

            int cantidadObjetivo = (int) cantidadObjetivoLong;
            List<PlantillaHistorica> muestras = new ArrayList<>(cantidadObjetivo);

            for (int i = 0; i < cantidadObjetivo; i++) {
                muestras.add(
                        plantillasHistoricas.get(
                                random.nextInt(plantillasHistoricas.size())
                        )
                );
            }
            seleccionadas = muestras;

        } else {
            List<Map.Entry<LocalDate, List<PlantillaHistorica>>> mismoDiaSemana =
                    plantillasPorDia.entrySet().stream()
                            .filter(entry -> entry.getKey().getDayOfWeek() == fecha.getDayOfWeek())
                            .toList();

            List<Map.Entry<LocalDate, List<PlantillaHistorica>>> candidatos =
                    mismoDiaSemana.isEmpty()
                            ? new ArrayList<>(plantillasPorDia.entrySet())
                            : mismoDiaSemana;

            Map.Entry<LocalDate, List<PlantillaHistorica>> diaPlantilla =
                    candidatos.get(random.nextInt(candidatos.size()));

            seleccionadas = diaPlantilla.getValue();
        }

        for (int i = 0; i < seleccionadas.size(); i++) {
            PlantillaHistorica plantilla = seleccionadas.get(i);
            LocalTime hora = plantilla.horaLlegada();

            // La ejecución comienza vacía exactamente a las 00:00:00.
            if (hora.equals(LocalTime.MIDNIGHT)) {
                hora = LocalTime.of(0, 0, 1);
            }

            long id = -Math.addExact(
                    Math.multiplyExact(indiceDia + 1L, BLOQUE_IDS_SINTETICOS),
                    i + 1L
            );

            PedidoSimulado simulado = new PedidoSimulado(
                    id,
                    plantilla.cliente(),
                    plantilla.cantidad(),
                    plantilla.prioridad(),
                    plantilla.horasLimite(),
                    fecha.atTime(hora),
                    plantilla.x(),
                    plantilla.y()
            );

            pedidosSimulados.put(id, simulado);
        }
    }

    private static long mezclarSemilla(long semilla, long valor) {
        long z = semilla ^ (valor + 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private record PlantillaHistorica(
            String cliente,
            int cantidad,
            TipoPrioridad prioridad,
            int horasLimite,
            LocalTime horaLlegada,
            int x,
            int y
    ) {
    }

    private static final class PedidoSimulado {
        private final long id;
        private final String cliente;
        private final int cantidad;
        private final TipoPrioridad prioridad;
        private final int horasLimite;
        private final LocalDateTime fechaLlegada;
        private final int x;
        private final int y;
        private EstadoPedido estado = EstadoPedido.PENDIENTE;
        private LocalDateTime fechaEntregaReal;

        private PedidoSimulado(
                long id,
                String cliente,
                int cantidad,
                TipoPrioridad prioridad,
                int horasLimite,
                LocalDateTime fechaLlegada,
                int x,
                int y
        ) {
            this.id = id;
            this.cliente = cliente;
            this.cantidad = cantidad;
            this.prioridad = prioridad;
            this.horasLimite = horasLimite;
            this.fechaLlegada = fechaLlegada;
            this.x = x;
            this.y = y;
        }

        private LocalDateTime fechaLimite() {
            return fechaLlegada.plusHours(horasLimite);
        }

        private Pedido aPedido(int ancho, int alto) {
            return new Pedido(
                    id,
                    cliente,
                    cantidad,
                    prioridad,
                    nodo(x, y, ancho, alto),
                    fechaLlegada,
                    horasLimite
            );
        }
    }

    private Set<String> leerNodosBloqueados(Connection cn, LocalDateTime reloj) throws SQLException {
        Map<Long, List<Nodo>> porBloqueo = new LinkedHashMap<>();
        try (PreparedStatement ps = cn.prepareStatement("""
                SELECT b.id_incidencia, bn.orden, bn.x, bn.y
                FROM incidencia i
                JOIN bloqueo b ON b.id_incidencia = i.id_incidencia
                JOIN bloqueo_nodo bn ON bn.id_incidencia = b.id_incidencia
                WHERE i.activa = TRUE
                  AND b.fecha_inicio <= ?
                  AND b.fecha_fin > ?
                ORDER BY b.id_incidencia, bn.orden
                """)) {
            Timestamp t = Timestamp.valueOf(reloj);
            ps.setTimestamp(1, t);
            ps.setTimestamp(2, t);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    porBloqueo
                            .computeIfAbsent(rs.getLong("id_incidencia"), k -> new ArrayList<>())
                            .add(new Nodo(rs.getInt("x"), rs.getInt("y")));
                }
            }
        }

        Set<String> salida = new HashSet<>();
        for (List<Nodo> vertices : porBloqueo.values()) {
            if (vertices.size() == 1) {
                salida.add(clave(vertices.get(0)));
                continue;
            }
            for (int i = 0; i < vertices.size() - 1; i++) {
                Nodo a = vertices.get(i);
                Nodo b = vertices.get(i + 1);
                if (a.getX() != b.getX() && a.getY() != b.getY()) {
                    throw new IllegalStateException("Bloqueo diagonal inválido en BD");
                }
                int dx = Integer.compare(b.getX(), a.getX());
                int dy = Integer.compare(b.getY(), a.getY());
                int x = a.getX();
                int y = a.getY();
                salida.add(x + "," + y);
                while (x != b.getX() || y != b.getY()) {
                    x += dx;
                    y += dy;
                    salida.add(x + "," + y);
                }
            }
        }
        return Set.copyOf(salida);
    }

    private void finalizarAveriasConRetornoCumplido(
            Connection cn,
            LocalDateTime reloj) throws SQLException {

        try (PreparedStatement ps = cn.prepareStatement("""
                UPDATE incidencia i
                JOIN averia a ON a.id_incidencia = i.id_incidencia
                SET i.activa = FALSE
                WHERE i.activa = TRUE
                  AND i.fecha_ocurrencia <= ?
                  AND a.hora_retorno_estimada IS NOT NULL
                  AND a.hora_retorno_estimada <= ?
                """)) {

            Timestamp t = Timestamp.valueOf(reloj);
            ps.setTimestamp(1, t);
            ps.setTimestamp(2, t);
            ps.executeUpdate();
        }
    }

    private Map<String, PuntoSimulacion> leerAveriasActivas(
            Connection cn,
            LocalDateTime reloj) throws SQLException {

        Map<String, PuntoSimulacion> salida = new HashMap<>();
        try (PreparedStatement ps = cn.prepareStatement("""
                SELECT a.id_vehiculo, a.ubicacion_x, a.ubicacion_y
                FROM incidencia i
                JOIN averia a ON a.id_incidencia = i.id_incidencia
                WHERE i.activa = TRUE
                  AND i.fecha_ocurrencia <= ?
                  AND (a.hora_retorno_estimada IS NULL OR a.hora_retorno_estimada > ?)
                ORDER BY i.fecha_ocurrencia DESC
                """)) {
            Timestamp t = Timestamp.valueOf(reloj);
            ps.setTimestamp(1, t);
            ps.setTimestamp(2, t);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.putIfAbsent(
                            rs.getString("id_vehiculo"),
                            new PuntoSimulacion(rs.getInt("ubicacion_x"), rs.getInt("ubicacion_y")));
                }
            }
        }
        return salida;
    }

    private Set<String> leerMantenimientosActivos(
            Connection cn,
            LocalDateTime reloj) throws SQLException {

        Set<String> salida = new HashSet<>();
        try (PreparedStatement ps = cn.prepareStatement("""
                SELECT id_vehiculo
                FROM mantenimiento_vehiculo
                WHERE activo = TRUE
                  AND fecha_inicio <= ?
                  AND fecha_fin > ?
                """)) {
            Timestamp t = Timestamp.valueOf(reloj);
            ps.setTimestamp(1, t);
            ps.setTimestamp(2, t);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(rs.getString("id_vehiculo"));
                }
            }
        }
        return salida;
    }

    private Map<String, VehiculoPersistido> leerVehiculosPersistidos(Connection cn) throws SQLException {
        Map<String, VehiculoPersistido> salida = new HashMap<>();
        try (PreparedStatement ps = cn.prepareStatement("""
                SELECT id_vehiculo, estado, posicion_x, posicion_y
                FROM vehiculo
                ORDER BY id_vehiculo
                """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                VehiculoPersistido v = new VehiculoPersistido(
                        rs.getString("id_vehiculo"),
                        rs.getString("estado"),
                        rs.getInt("posicion_x"),
                        rs.getInt("posicion_y"));
                salida.put(v.idVehiculo(), v);
            }
        }
        return salida;
    }

    private int[] leerDimensiones(Connection cn) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("""
                SELECT ancho_km, alto_km
                FROM ciudad
                ORDER BY id_ciudad
                """);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalArgumentException("Falta ciudad");
            }
            int ancho = rs.getInt("ancho_km");
            int alto = rs.getInt("alto_km");
            if (rs.next()) {
                throw new IllegalArgumentException("El sistema requiere una sola ciudad");
            }
            return new int[]{ancho, alto};
        }
    }

    private int[] leerCentral(Connection cn) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("""
                SELECT ubicacion_x, ubicacion_y
                FROM almacen
                WHERE tipo = 'CENTRAL'
                ORDER BY id_almacen
                """);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalArgumentException("Falta almacén central");
            }
            int x = rs.getInt(1);
            int y = rs.getInt(2);
            if (rs.next()) {
                throw new IllegalArgumentException("Debe existir un único almacén central");
            }
            return new int[]{x, y};
        }
    }

    private static Nodo nodo(int x, int y, int ancho, int alto) {
        if (x < 0 || x > ancho || y < 0 || y > alto) {
            throw new IllegalArgumentException("Coordenada fuera de ciudad: " + x + "," + y);
        }
        return new Nodo(x, y);
    }

    private static String clave(Nodo nodo) {
        return nodo.getX() + "," + nodo.getY();
    }


    private static boolean cruzaHora(
            LocalDateTime desde,
            LocalDateTime hasta,
            LocalTime hora) {

        LocalDate dia = desde.toLocalDate();
        LocalDate fin = hasta.toLocalDate();

        while (!dia.isAfter(fin)) {
            LocalDateTime instante = dia.atTime(hora);
            if (instante.isAfter(desde) && !instante.isAfter(hasta)) {
                return true;
            }
            dia = dia.plusDays(1);
        }
        return false;
    }
}
