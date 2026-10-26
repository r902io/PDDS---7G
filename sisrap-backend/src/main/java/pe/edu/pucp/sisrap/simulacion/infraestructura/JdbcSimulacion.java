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
import org.springframework.beans.factory.annotation.Value;
import pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo;

import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.configuracionoperativa.infraestructura.JdbcConfiguracionOperativa;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.flota.dominio.AveriaVehiculo;
import pe.edu.pucp.sisrap.flota.dominio.TipoAveria;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
import pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion;
import pe.edu.pucp.sisrap.parametros.infraestructura.JdbcParametros;
import pe.edu.pucp.sisrap.pedido.dominio.EstadoPedido;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.pedido.dominio.PedidoOperativo;
import pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Ruta;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;
import pe.edu.pucp.sisrap.simulacion.dominio.ConfiguracionSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.IncumplimientoPedido;
import pe.edu.pucp.sisrap.simulacion.dominio.ContextoOperativo;
import pe.edu.pucp.sisrap.simulacion.dominio.EstadoSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EscenarioSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.PuntoSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.RepositorioSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.ResultadoCorrida;
import pe.edu.pucp.sisrap.simulacion.dominio.DiagnosticoPlanificacion;
import pe.edu.pucp.sisrap.simulacion.dominio.RecorridoVehiculo;
import pe.edu.pucp.sisrap.simulacion.dominio.ResumenPedidosSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.VehiculoPersistido;
import pe.edu.pucp.sisrap.simulacion.dominio.VehiculoSnapshot;

@Repository
public class JdbcSimulacion implements RepositorioSimulacion {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(JdbcSimulacion.class);
    private static final long BLOQUE_IDS_SINTETICOS = 1_000_000L;

    private final DataSource fuente;
    private final pe.edu.pucp.sisrap.configuracionoperativa.infraestructura.JdbcConfiguracionEscenario escenarios;

    @Value("${sisrap.simulacion.archivos-dir:${SISRAP_DATOS_DIR:datos-caso}}")
    private String directorioHistoricos = "";
    private ProgramadorIncidenciasHistoricas programadorIncidencias;
    private final List<Bloqueo> bloqueosProgramados = new ArrayList<>();
    private final List<ProgramadorIncidenciasHistoricas.MantenimientoProgramado>
            mantenimientosProgramados = new ArrayList<>();
    private LocalDate ultimoDiaIncidencias;
    private ConfiguracionSimulacion configuracionIncidencias;
    private int anchoIncidencias;
    private int altoIncidencias;
    private Set<String> idsIncidencias = Set.of();

    /** Pedidos de la ejecución activa. Nunca se persisten en la tabla histórica. */
    private final Map<Long, PedidoSimulado> pedidosSimulados = new LinkedHashMap<>();
    private final Map<Long, FraccionPedido> fracciones = new LinkedHashMap<>();
    private long siguienteFraccion = -1L;
    private List<PlantillaHistorica> plantillasHistoricas = List.of();
    private Map<LocalDate, List<PlantillaHistorica>> plantillasPorDia = Map.of();
    private ConfiguracionSimulacion configuracionDemanda;
    private LocalDate ultimoDiaGenerado;
    private int medianaPedidosDiarios;
    private Map<LocalDate, Integer> demandaHistoricaPorFecha = Map.of();
    private Map<LocalDate, Long> paquetesHistoricosPorFecha = Map.of();
    private pe.edu.pucp.sisrap.simulacion.dominio.ModeloDemandaColapso modeloColapso;

    @Value("${sisrap.colapso.tasa-aumento-respaldo:0.05}")
    private double tasaAumentoRespaldo = 0.05;
    @Value("${sisrap.colapso.probabilidad-aumento-respaldo:0.5}")
    private double probabilidadAumentoRespaldo = 0.5;
    private LocalDateTime turnoAsignado;

    private record ResumenCache(LocalDateTime inicio, LocalDateTime fin,
                                LocalDateTime reloj, ResumenPedidosSimulacion valor) {}
    private ResumenCache resumenCache;

    public JdbcSimulacion(DataSource fuente,
            pe.edu.pucp.sisrap.configuracionoperativa.infraestructura.JdbcConfiguracionEscenario escenarios) {
        this.fuente = fuente;
        this.escenarios = escenarios;
    }

    @Override
    public synchronized void prepararEjecucion(ConfiguracionSimulacion configuracion) {
        resumenCache = null;
        if (configuracion == null) {
            throw new IllegalArgumentException("La configuración de simulación es obligatoria");
        }

        escenarios.asegurar();
        try (Connection cn = fuente.getConnection()) {
            EsquemaDatosSimulacion.asegurar(cn);
            cn.setAutoCommit(false);
            try {
                // El modelo se calcula antes de limpiar; si faltan insumos se aborta sin borrar.
                inicializarModeloDemanda(cn, configuracion);

                // Última ejecución: copia de seguridad idempotente por id_simulacion.
                Long anterior = null;
                try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT id_simulacion FROM simulacion ORDER BY id_simulacion DESC LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) anterior = rs.getLong(1);
                }
                if (anterior != null) {
                    try (PreparedStatement ps = cn.prepareStatement("""
                            UPDATE simulacion SET estado='ERROR', fecha_fin=COALESCE(fecha_fin,reloj_simulado)
                            WHERE id_simulacion=? AND estado IN ('EJECUTANDO','PAUSADA')
                            """)) {
                        ps.setLong(1, anterior);
                        ps.executeUpdate();
                    }
                    EsquemaDatosSimulacion.archivar(cn, anterior);
                }
                EsquemaDatosSimulacion.limpiarOperacion(cn);
                escenarios.aplicar(cn, configuracion.escenario());

                try (PreparedStatement ps = cn.prepareStatement("""
                        UPDATE almacen SET stock_actual=capacidad_maxima WHERE tipo='INTERMEDIO'
                        """)) {
                    ps.executeUpdate();
                }
                // Siempre reconstruye la flota DESDE LA PLANTILLA, nunca desde la
                // tabla vehiculo que pertenece a la corrida anterior.
                JdbcConfiguracionOperativa.regenerarVehiculos(cn);
                prepararModeloColapso(cn);
                prepararProgramadorIncidencias(cn, configuracion);
                cn.commit();
            } catch (RuntimeException | SQLException e) {
                cn.rollback();
                limpiarDemandaSimulada();
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("No se pudo preparar la simulación", e);
            }
        } catch (SQLException e) {
            limpiarDemandaSimulada();
            throw new IllegalStateException("No se pudo preparar la simulación", e);
        }
    }

    private void prepararProgramadorIncidencias(Connection cn,
                                             ConfiguracionSimulacion configuracion) throws SQLException {
        programadorIncidencias = null;
        configuracionIncidencias = configuracion;
        ultimoDiaIncidencias = null;
        bloqueosProgramados.clear();
        mantenimientosProgramados.clear();
        if (directorioHistoricos == null || directorioHistoricos.isBlank()) return;
        programadorIncidencias = ProgramadorIncidenciasHistoricas.cargar(
                pe.edu.pucp.sisrap.configuracionoperativa.aplicacion.CalendariosCasoService
                        .directorioEscenario(directorioHistoricos, configuracion.escenario()));
        try (PreparedStatement ps = cn.prepareStatement("SELECT ancho_km,alto_km FROM ciudad");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) throw new SQLException("No se encontró la ciudad configurada");
            anchoIncidencias = rs.getInt(1);
            altoIncidencias = rs.getInt(2);
        }
        Set<String> ids = new HashSet<>();
        try (PreparedStatement ps = cn.prepareStatement("SELECT id_vehiculo FROM vehiculo");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getString(1));
        }
        idsIncidencias = Set.copyOf(ids);
        programadorIncidencias.validarVehiculos(idsIncidencias,
                configuracion.fechaInicio(), configuracion.fechaHoraFin().toLocalDate());
        programadorIncidencias.validarMapa(anchoIncidencias, altoIncidencias,
                configuracion.fechaInicio().minusDays(programadorIncidencias.diasAntelacionBloqueos()), configuracion.fechaHoraFin().toLocalDate());
        for (LocalDate dia = configuracion.fechaInicio().minusDays(programadorIncidencias.diasAntelacionBloqueos());
             dia.isBefore(configuracion.fechaInicio()); dia = dia.plusDays(1)) {
            bloqueosProgramados.addAll(programadorIncidencias.generarDia(configuracion.fechaInicio(), dia,
                    configuracion.semilla(), anchoIncidencias, altoIncidencias, idsIncidencias)
                    .bloqueos().stream().filter(b -> b.fin().isAfter(configuracion.fechaHoraInicio())).toList());
        }
    }

    @Override
    public synchronized boolean activarIncidenciasProgramadas(LocalDateTime reloj) {
        if (programadorIncidencias == null || configuracionIncidencias == null
                || !reloj.isBefore(configuracionIncidencias.fechaHoraFin())) return false;
        LocalDate dia = reloj.toLocalDate();
        if (!dia.equals(ultimoDiaIncidencias)) {
            LocalDate siguiente = ultimoDiaIncidencias == null
                    ? configuracionIncidencias.fechaInicio() : ultimoDiaIncidencias.plusDays(1);
            while (!siguiente.isAfter(dia)) {
                var programacion = programadorIncidencias.generarDia(
                        configuracionIncidencias.fechaInicio(), siguiente,
                        configuracionIncidencias.semilla(), anchoIncidencias, altoIncidencias,
                        idsIncidencias);
                bloqueosProgramados.addAll(programacion.bloqueos());
                mantenimientosProgramados.addAll(programacion.mantenimientos());
                siguiente = siguiente.plusDays(1);
            }
            ultimoDiaIncidencias = dia;
        }
        List<Bloqueo> bloqueosDebidos = bloqueosProgramados.stream()
                .filter(b -> !b.inicio().isAfter(reloj)).toList();
        List<ProgramadorIncidenciasHistoricas.MantenimientoProgramado> mantenimientosDebidos =
                mantenimientosProgramados.stream()
                        .filter(m -> !m.inicio().isAfter(reloj)).toList();
        if (bloqueosDebidos.isEmpty() && mantenimientosDebidos.isEmpty()) return false;
        try (Connection cn = fuente.getConnection()) {
            cn.setAutoCommit(false);
            try {
                boolean cambios = false;
                List<ProgramadorIncidenciasHistoricas.MantenimientoProgramado> atendidos = new ArrayList<>();
                for (Bloqueo bloqueo : bloqueosDebidos) {
                    if (bloqueo.fin().isAfter(reloj)) {
                        registrarBloqueoHistorico(cn, bloqueo);
                        cambios = true;
                    }
                }
                // Los mantenimientos previstos ocupan el día especificado por el profesor,
                // independientemente de la disponibilidad del vehículo. Las rutas nuevas
                // se validan contra el calendario antes del despacho.
                for (var mantenimiento : mantenimientosDebidos) {
                    if (mantenimiento.fin().isAfter(reloj)) {
                        try (PreparedStatement ps = cn.prepareStatement("""
                                INSERT INTO mantenimiento_vehiculo
                                  (id_vehiculo,tipo,fecha_inicio,fecha_fin,activo)
                                VALUES (?,'PREVENTIVO',?,?,TRUE)
                                """)) {
                            ps.setString(1, mantenimiento.idVehiculo());
                            ps.setTimestamp(2, Timestamp.valueOf(mantenimiento.inicio()));
                            ps.setTimestamp(3, Timestamp.valueOf(mantenimiento.fin()));
                            ps.executeUpdate();
                        }
                        cambios = true;
                    }
                    atendidos.add(mantenimiento);
                }
                cn.commit();
                bloqueosProgramados.removeAll(bloqueosDebidos);
                mantenimientosProgramados.removeAll(atendidos);
                return cambios;
            } catch (RuntimeException | SQLException ex) {
                cn.rollback();
                if (ex instanceof RuntimeException r) throw r;
                throw new IllegalStateException("No se pudieron activar incidencias históricas", ex);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudieron activar incidencias históricas", ex);
        }
    }

    @Override
    public synchronized boolean tieneMantenimientoProgramado(String idVehiculo,
                                                               LocalDateTime desde,
                                                               LocalDateTime hasta) {
        if (idVehiculo == null || desde == null || hasta == null || hasta.isBefore(desde)) return false;
        try(Connection cn=fuente.getConnection(); PreparedStatement ps=cn.prepareStatement("""
                SELECT 1 FROM mantenimiento_vehiculo WHERE id_vehiculo=? AND activo=TRUE
                AND fecha_inicio<? AND fecha_fin>? LIMIT 1
                """)) {
            ps.setString(1,idVehiculo);ps.setTimestamp(2,Timestamp.valueOf(hasta));
            ps.setTimestamp(3,Timestamp.valueOf(desde));
            try(ResultSet rs=ps.executeQuery()){if(rs.next())return true;}
        }catch(SQLException e){throw new IllegalStateException("No se pudo verificar mantenimiento previsto",e);}
        if(programadorIncidencias==null) return false;
        for (LocalDate dia = desde.toLocalDate().minusDays(1);
             !dia.isAfter(hasta.toLocalDate()); dia = dia.plusDays(1)) {
            for (var mantenimiento : programadorIncidencias.generarDia(
                    configuracionIncidencias.fechaInicio(), dia,
                    configuracionIncidencias.semilla(), anchoIncidencias,
                    altoIncidencias, idsIncidencias).mantenimientos()) {
                if (mantenimiento.idVehiculo().equals(idVehiculo)
                        && mantenimiento.inicio().isBefore(hasta)
                        && mantenimiento.fin().isAfter(desde)) return true;
            }
        }
        return false;
    }

    private boolean vehiculoDisponible(Connection cn, String idVehiculo, LocalDateTime reloj) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("""
                SELECT v.estado FROM vehiculo v WHERE v.id_vehiculo=?
                AND NOT EXISTS (SELECT 1 FROM mantenimiento_vehiculo m
                   WHERE m.id_vehiculo=v.id_vehiculo AND m.activo=TRUE
                   AND m.fecha_inicio <= ? AND m.fecha_fin > ?)
                """)) {
            ps.setString(1, idVehiculo);
            ps.setTimestamp(2, Timestamp.valueOf(reloj));
            ps.setTimestamp(3, Timestamp.valueOf(reloj));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "DISPONIBLE".equals(rs.getString(1));
            }
        }
    }

    private void registrarBloqueoHistorico(Connection cn, Bloqueo bloqueo) throws SQLException {
        long id;
        try (PreparedStatement ps = cn.prepareStatement("""
                INSERT INTO incidencia(tipo,fecha_ocurrencia,activa)
                VALUES('BLOQUEO',?,TRUE)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, Timestamp.valueOf(bloqueo.inicio()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("No se generó ID de bloqueo");
                id = rs.getLong(1);
            }
        }
        try (PreparedStatement ps = cn.prepareStatement("""
                INSERT INTO bloqueo(id_incidencia,fecha_inicio,fecha_fin)
                VALUES(?,?,?)
                """)) {
            ps.setLong(1, id);
            ps.setTimestamp(2, Timestamp.valueOf(bloqueo.inicio()));
            ps.setTimestamp(3, Timestamp.valueOf(bloqueo.fin()));
            ps.executeUpdate();
        }
        try (PreparedStatement ps = cn.prepareStatement("""
                INSERT INTO bloqueo_nodo(id_incidencia,orden,x,y)
                VALUES(?,?,?,?)
                """)) {
            for (int i = 0; i < bloqueo.vertices().size(); i++) {
                var n = bloqueo.vertices().get(i);
                ps.setLong(1,id);
                ps.setInt(2,i);
                ps.setInt(3,n.getX());
                ps.setInt(4,n.getY());
                ps.addBatch();
            }
            ps.executeBatch();
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

            cn.setAutoCommit(false);
            try {
                ps.setString(7, perfil);
                ps.executeUpdate();
                long id;
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("No se generó id de simulación");
                    id = rs.getLong(1);
                }
                EsquemaDatosSimulacion.registrarRecursos(cn, id);
                cn.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                cn.rollback();
                throw e;
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

        try (Connection cn = fuente.getConnection()) {
            cn.setAutoCommit(false);
            try (PreparedStatement ps = cn.prepareStatement("""
                    UPDATE simulacion
                    SET estado=?, reloj_simulado=?, fecha_fin=?, tiempo_ejecucion_ms=?
                    WHERE id_simulacion=?
                    """)) {
                ps.setString(1, estado.name());
                ps.setTimestamp(2, reloj == null ? null : Timestamp.valueOf(reloj));
                ps.setTimestamp(3, reloj == null ? null : Timestamp.valueOf(reloj));
                ps.setLong(4, tiempoRealMs);
                ps.setLong(5, idSimulacion);
                ps.executeUpdate();
                EsquemaDatosSimulacion.archivar(cn, idSimulacion);
                archivarFracciones(cn, idSimulacion);
                cn.commit();
            } catch (RuntimeException | SQLException e) {
                cn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo finalizar/archivar la simulación", e);
        }
    }

    @Override
    public synchronized PedidoOperativo registrarPedidoManual(
            String cliente, int cantidad, TipoPrioridad prioridad, int x, int y,
            LocalDateTime fecha) {
        resumenCache = null;
        if (configuracionDemanda == null) {
            throw new IllegalStateException("No existe una simulación preparada");
        }
        if (cliente == null || cliente.isBlank() || cliente.length() > 20) {
            throw new IllegalArgumentException("El identificador del cliente debe tener entre 1 y 20 caracteres");
        }
        if (cantidad <= 0 || cantidad > 100_000) {
            throw new IllegalArgumentException("La cantidad de paquetes debe estar entre 1 y 100000");
        }
        if (prioridad == null || fecha == null) {
            throw new IllegalArgumentException("La prioridad y la fecha de registro son obligatorias");
        }
        int plazo = switch (prioridad) {
            case REGULAR_36H -> 36;
            case PRIORIZADO_18H -> 18;
            case PRIORIZADO_12H -> 12;
            case PRIORIZADO_8H -> 8;
            case PRIORIZADO_4H -> 4;
        };
        try (Connection cn = fuente.getConnection()) {
            int[] dimensiones = leerDimensiones(cn);
            if (x < 0 || y < 0 || x > dimensiones[0] || y > dimensiones[1]) {
                throw new IllegalArgumentException("La ubicación del cliente está fuera de la ciudad");
            }
            try (PreparedStatement ps = cn.prepareStatement("""
                    INSERT INTO pedido(id_cliente,cantidad_qq,prioridad,horas_limite,
                                       fecha_llegada,ubicacion_x,ubicacion_y,estado)
                    VALUES(?,?,?,?,?,?,?,'PENDIENTE')
                    """, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, cliente.trim());
                ps.setInt(2, cantidad);
                ps.setString(3, prioridad.name());
                ps.setInt(4, plazo);
                ps.setTimestamp(5, Timestamp.valueOf(fecha));
                ps.setInt(6, x);
                ps.setInt(7, y);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("No se generó el identificador del pedido");
                    long id = rs.getLong(1);
                    pedidosSimulados.put(id, new PedidoSimulado(id, cliente.trim(), cantidad,
                            prioridad, plazo, fecha, x, y));
                    return new PedidoOperativo(id, cliente.trim(), cantidad, prioridad, plazo,
                            fecha, null, EstadoPedido.PENDIENTE, x, y);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo registrar el pedido", e);
        }
    }

    @Override
    public synchronized List<Pedido> cargarPrevisionDiaria(
            LocalDate fecha, LocalDateTime disponibleDesde) {
        resumenCache = null;
        if (configuracionDemanda == null || fecha == null || disponibleDesde == null) {
            throw new IllegalStateException("La simulación no está preparada");
        }
        asegurarDemandaGeneradaHasta(fecha);
        try (Connection cn = fuente.getConnection()) {
            int[] dimensiones = leerDimensiones(cn);
            int ancho = dimensiones[0];
            int alto = dimensiones[1];
            List<Pedido> previstos = new ArrayList<>();
            for (PedidoSimulado p : pedidosSimulados.values()) {
                if (!p.fechaLlegada.toLocalDate().equals(fecha)
                        || p.id >= 0 || p.fechaLlegada.isBefore(disponibleDesde)) {
                    continue;
                }
                previstos.add(p.aPedido(ancho, alto).disponibleDesde(disponibleDesde));
            }
            previstos.sort(Comparator.comparing(Pedido::getFechaLlegada)
                    .thenComparing(Pedido::getIdPedido));
            return List.copyOf(previstos);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consultar la demanda prevista", e);
        }
    }

    /** Registra únicamente pedidos cuyo instante de llegada ya ocurrió. */
    @Override
    public synchronized void publicarPedidosHasta(LocalDateTime reloj) {
        resumenCache = null;
        asegurarDemandaGeneradaHasta(reloj.toLocalDate());
        List<PedidoSimulado> nuevos = pedidosSimulados.values().stream()
                .filter(p -> p.id < 0 && !p.fechaLlegada.isAfter(reloj))
                .sorted(Comparator.comparing((PedidoSimulado p) -> p.fechaLlegada)
                        .thenComparingLong(p -> p.id))
                .toList();
        if (nuevos.isEmpty()) return;
        try (Connection cn = fuente.getConnection()) {
            cn.setAutoCommit(false);
            try (PreparedStatement ps = cn.prepareStatement("""
                    INSERT INTO pedido(id_cliente,cantidad_qq,prioridad,horas_limite,
                                       fecha_llegada,ubicacion_x,ubicacion_y,estado)
                    VALUES (?,?,?,?,?,?,?,'PENDIENTE')
                    """, Statement.RETURN_GENERATED_KEYS)) {
                for (PedidoSimulado pedido : nuevos) {
                    ps.setString(1, pedido.cliente);
                    ps.setInt(2, pedido.cantidad);
                    ps.setString(3, pedido.prioridad.name());
                    ps.setInt(4, pedido.horasLimite);
                    ps.setTimestamp(5, Timestamp.valueOf(pedido.fechaLlegada));
                    ps.setInt(6, pedido.x);
                    ps.setInt(7, pedido.y);
                    ps.addBatch();
                }
                ps.executeBatch();
                List<Long> claves = new ArrayList<>(nuevos.size());
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    while (rs.next()) claves.add(rs.getLong(1));
                }
                if (claves.size() != nuevos.size()) {
                    throw new SQLException("No se generaron todos los identificadores de pedidos");
                }
                cn.commit();
                for (int i = 0; i < nuevos.size(); i++) {
                    PedidoSimulado pedido = nuevos.get(i);
                    pedidosSimulados.remove(pedido.id);
                    pedido.id = claves.get(i);
                    pedidosSimulados.put(pedido.id, pedido);
                }
            } catch (SQLException | RuntimeException e) {
                cn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron registrar los pedidos sintéticos", e);
        }
    }

    @Override
    public void guardarPlan(long idSimulacion, LocalDateTime reloj, Solucion solucion,
                            List<Ruta> rutasAceptadas, double beta1, double beta2, double beta3) {
        if (rutasAceptadas == null || rutasAceptadas.isEmpty()) return;
        try (Connection cn = fuente.getConnection()) {
            cn.setAutoCommit(false);
            try {
                long idSolucion;
                try (PreparedStatement ps = cn.prepareStatement("""
                        INSERT INTO solucion(
                          id_simulacion,valor_funcion_objetivo,costo_transporte,valor_r,
                          valor_u,valor_v,lambda1,lambda2,lambda3,es_factible,fecha_generacion)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1,idSimulacion);
                    ps.setDouble(2,solucion.getValorFuncionObjetivo());
                    ps.setDouble(3,solucion.getCostoTransporte());
                    ps.setDouble(4,solucion.getValorR());
                    ps.setDouble(5,solucion.getValorN());
                    ps.setDouble(6,solucion.getValorV());
                    ps.setDouble(7,beta1);
                    ps.setDouble(8,beta2);
                    ps.setDouble(9,beta3);
                    ps.setBoolean(10,solucion.isEsFactible());
                    ps.setTimestamp(11,Timestamp.valueOf(reloj));
                    ps.executeUpdate();
                    try(ResultSet rs=ps.getGeneratedKeys()) {
                        if(!rs.next()) throw new SQLException("No se creó id_solucion");
                        idSolucion=rs.getLong(1);
                    }
                }
                try (PreparedStatement psRuta = cn.prepareStatement("""
                        INSERT INTO ruta(id_solucion,id_vehiculo,id_almacen_origen,
                                         distancia_total_km,costo_total,hora_inicio)
                        VALUES(?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement psPedido = cn.prepareStatement("""
                        INSERT INTO ruta_pedido(id_ruta,id_pedido,orden_visita,hora_entrega_estimada)
                        VALUES(?,?,?,?)
                        """)) {
                    for (Ruta ruta : rutasAceptadas) {
                        psRuta.setLong(1,idSolucion);
                        psRuta.setString(2,ruta.getVehiculo().getIdVehiculo());
                        psRuta.setString(3,ruta.getAlmacenOrigen().getIdAlmacen());
                        psRuta.setDouble(4,ruta.getDistanciaTotalKm());
                        psRuta.setDouble(5,ruta.getCostoTotal());
                        psRuta.setTimestamp(6,Timestamp.valueOf(reloj));
                        psRuta.executeUpdate();
                        long idRuta;
                        try (ResultSet rs = psRuta.getGeneratedKeys()) {
                            if (!rs.next()) throw new SQLException("No se creó id_ruta");
                            idRuta = rs.getLong(1);
                        }
                        for (int i=0; i<ruta.getSecuenciaPedidos().size(); i++) {
                            psPedido.setLong(1,idRuta);
                            long idPedido = ruta.getSecuenciaPedidos().get(i).getIdPedido();
                            FraccionPedido fraccion = fracciones.get(idPedido);
                            psPedido.setLong(2, fraccion == null ? idPedido : fraccion.idPedido);
                            if (fraccion != null) fraccion.idVehiculo = ruta.getVehiculo().getIdVehiculo();
                            psPedido.setInt(3,i+1);
                            psPedido.setTimestamp(4,Timestamp.valueOf(reloj.plusNanos(
                                    Math.round(ruta.horaEntregaDe(i)*3_600_000_000_000.0))));
                            psPedido.addBatch();
                        }
                    }
                    psPedido.executeBatch();
                }
                cn.commit();
            } catch(SQLException | RuntimeException e) {
                cn.rollback();
                throw e;
            }
        } catch(SQLException e) {
            throw new IllegalStateException("No se pudo almacenar el plan generado",e);
        }
    }

    @Override
    public void registrarDiagnosticos(long idSimulacion,
                                     List<DiagnosticoPlanificacion> diagnosticos) {
        if (diagnosticos == null || diagnosticos.isEmpty()) return;
        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     INSERT IGNORE INTO simulacion_diagnostico
                       (id_simulacion,instante,id_pedido,motivo) VALUES (?,?,?,?)
                     """)) {
            for (var diagnostico : diagnosticos) {
                ps.setLong(1, idSimulacion);
                ps.setTimestamp(2, Timestamp.valueOf(diagnostico.instante()));
                ps.setLong(3, diagnostico.idPedido());
                ps.setString(4, diagnostico.motivo());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo archivar el diagnóstico de planificación", e);
        }
    }

    @Override
    public void guardarRecorridos(long idSimulacion, List<RecorridoVehiculo.Registro> registros) {
        if (registros == null || registros.isEmpty()) return;
        try (Connection cn = fuente.getConnection()) {
            cn.setAutoCommit(false);
            try (PreparedStatement ps = cn.prepareStatement("""
                    INSERT INTO simulacion_recorrido
                      (id_simulacion,id_vehiculo,instante,ubicacion_x,ubicacion_y,
                       estado,fase,evento,carga_paquetes,id_pedido_objetivo,distancia_tramo_km)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """)) {
                int enLote = 0;
                for (RecorridoVehiculo.Registro registro : registros) {
                    RecorridoVehiculo.Punto punto = registro.punto();
                    ps.setLong(1, idSimulacion);
                    ps.setString(2, registro.idVehiculo());
                    ps.setTimestamp(3, Timestamp.valueOf(punto.instante()));
                    ps.setInt(4, punto.x());
                    ps.setInt(5, punto.y());
                    ps.setString(6, punto.estado());
                    ps.setString(7, punto.fase());
                    ps.setString(8, punto.evento());
                    ps.setInt(9, punto.cargaPaquetes());
                    if (punto.pedidoObjetivo() == null) ps.setNull(10, java.sql.Types.BIGINT);
                    else ps.setLong(10, punto.pedidoObjetivo());
                    ps.setDouble(11, punto.distanciaTramoKm());
                    ps.addBatch();
                    if (++enLote == 200) {
                        ps.executeBatch();
                        enLote = 0;
                    }
                }
                if (enLote != 0) ps.executeBatch();
                cn.commit();
            } catch (SQLException | RuntimeException e) {
                try {
                    cn.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                throw e;
            }
        } catch (SQLException e) {
            // Conservar el SQLState y el código permite localizar la causa en los logs.
            throw new IllegalStateException(
                    "No se pudo archivar el recorrido de los vehículos"
                    + " (SQLState=" + e.getSQLState() + ", código=" + e.getErrorCode()
                    + "): " + e.getMessage(), e);
        }
    }

    @Override
    public List<RecorridoVehiculo.Resumen> resumirRecorridos(long idSimulacion) {
        try (Connection cn = fuente.getConnection()) {
            EsquemaDatosSimulacion.asegurar(cn);
            try (PreparedStatement comprobar = cn.prepareStatement(
                    "SELECT 1 FROM simulacion WHERE id_simulacion=?")) {
                comprobar.setLong(1, idSimulacion);
                try (ResultSet rs = comprobar.executeQuery()) {
                    if (!rs.next()) throw new java.util.NoSuchElementException("Simulación no encontrada: " + idSimulacion);
                }
            }
            List<RecorridoVehiculo.Resumen> resumen = new ArrayList<>();
            try (PreparedStatement ps = cn.prepareStatement("""
                    SELECT v.id_vehiculo, v.tipo, COUNT(r.secuencia),
                           COALESCE(SUM(r.distancia_tramo_km),0),
                           MIN(r.instante), MAX(r.instante)
                    FROM simulacion_vehiculo v
                    LEFT JOIN simulacion_recorrido r
                      ON r.id_simulacion=v.id_simulacion AND r.id_vehiculo=v.id_vehiculo
                    WHERE v.id_simulacion=?
                    GROUP BY v.id_vehiculo,v.tipo ORDER BY v.id_vehiculo
                    """)) {
                ps.setLong(1, idSimulacion);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp inicio = rs.getTimestamp(5), fin = rs.getTimestamp(6);
                        resumen.add(new RecorridoVehiculo.Resumen(rs.getString(1), rs.getString(2),
                                rs.getInt(3), rs.getDouble(4),
                                inicio == null ? null : inicio.toLocalDateTime(),
                                fin == null ? null : fin.toLocalDateTime()));
                    }
                }
            }
            return resumen;
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consultar el resumen de recorridos", e);
        }
    }

    @Override
    public RecorridoVehiculo.Detalle consultarRecorrido(long idSimulacion, String idVehiculo) {
        if (idVehiculo == null || !idVehiculo.matches("[A-Za-z0-9_-]{1,20}")) {
            throw new IllegalArgumentException("Código de vehículo inválido");
        }
        try (Connection cn = fuente.getConnection()) {
            EsquemaDatosSimulacion.asegurar(cn);
            String tipo;
            try (PreparedStatement ps = cn.prepareStatement("""
                    SELECT tipo FROM simulacion_vehiculo WHERE id_simulacion=? AND id_vehiculo=?
                    """)) {
                ps.setLong(1, idSimulacion);
                ps.setString(2, idVehiculo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new java.util.NoSuchElementException(
                            "Vehículo no encontrado en la corrida: " + idVehiculo);
                    tipo = rs.getString(1);
                }
            }
            List<RecorridoVehiculo.Punto> puntos = new ArrayList<>();
            double distancia = 0;
            try (PreparedStatement ps = cn.prepareStatement("""
                    SELECT instante,ubicacion_x,ubicacion_y,estado,fase,evento,
                           carga_paquetes,id_pedido_objetivo,distancia_tramo_km
                    FROM simulacion_recorrido
                    WHERE id_simulacion=? AND id_vehiculo=? ORDER BY secuencia
                    """)) {
                ps.setLong(1, idSimulacion);
                ps.setString(2, idVehiculo);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long objetivo = rs.getLong(8);
                        Long pedido = rs.wasNull() ? null : objetivo;
                        double tramo = rs.getDouble(9);
                        distancia += tramo;
                        puntos.add(new RecorridoVehiculo.Punto(rs.getTimestamp(1).toLocalDateTime(),
                                rs.getInt(2), rs.getInt(3), rs.getString(4), rs.getString(5),
                                rs.getString(6), rs.getInt(7), pedido, tramo));
                    }
                }
            }
            return new RecorridoVehiculo.Detalle(idSimulacion, idVehiculo, tipo, distancia, puntos);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consultar el trayecto del vehículo", e);
        }
    }

    @Override
    public ResultadoCorrida consultarResultado(long idSimulacion) {
        try (Connection cn=fuente.getConnection()) {
            EsquemaDatosSimulacion.asegurar(cn);
            String escenario, estado;
            LocalDateTime inicio, fin;
            long tiempo;
            try (PreparedStatement ps=cn.prepareStatement("""
                    SELECT escenario,estado,fecha_inicio,fecha_fin,tiempo_ejecucion_ms
                    FROM simulacion WHERE id_simulacion=?
                    """)) {
                ps.setLong(1,idSimulacion);
                try(ResultSet rs=ps.executeQuery()) {
                    if(!rs.next()) throw new java.util.NoSuchElementException(
                            "Simulación no encontrada: " + idSimulacion);
                    escenario=rs.getString(1);
                    estado=rs.getString(2);
                    inicio=rs.getTimestamp(3).toLocalDateTime();
                    fin=rs.getTimestamp(4)==null?null:rs.getTimestamp(4).toLocalDateTime();
                    tiempo=rs.getLong(5);
                }
            }
            List<ResultadoCorrida.PedidoRegistrado> pedidos=new ArrayList<>();
            int entregados=0,retrasados=0;
            try(PreparedStatement ps=cn.prepareStatement("""
                    SELECT id_pedido,id_cliente,cantidad_qq,prioridad,fecha_llegada,
                           fecha_entrega_real,estado,ubicacion_x,ubicacion_y,horas_limite,fecha_arribo
                    FROM simulacion_pedido WHERE id_simulacion=? ORDER BY id_pedido
                    """)) {
                ps.setLong(1,idSimulacion);
                try(ResultSet rs=ps.executeQuery()) {
                    while(rs.next()) {
                        LocalDateTime llegada=rs.getTimestamp(5).toLocalDateTime();
                        Timestamp entregaSql=rs.getTimestamp(6);
                        LocalDateTime entrega=entregaSql==null?null:entregaSql.toLocalDateTime();
                        String e=rs.getString(7);
                        Timestamp arriboSql=rs.getTimestamp(11);
                        LocalDateTime arribo=arriboSql==null?null:arriboSql.toLocalDateTime();
                        if("ENTREGADO".equals(e)) entregados++;
                        LocalDateTime limite = llegada.plusHours(rs.getInt(10));
                        boolean arriboFueraDePlazo = arribo != null && arribo.isAfter(limite);
                        boolean vencidoSinArribo = (arribo == null || arriboFueraDePlazo)
                                && fin != null && limite.isBefore(fin);
                        if (arriboFueraDePlazo || vencidoSinArribo) {
                            retrasados++;
                        }
                        pedidos.add(new ResultadoCorrida.PedidoRegistrado(
                                rs.getLong(1),rs.getString(2),rs.getInt(3),rs.getString(4),
                                llegada,arribo,entrega,e,rs.getInt(8),rs.getInt(9), limite,
                                Math.max(0.0, java.time.Duration.between(limite,
                                    arribo != null ? arribo : (fin != null ? fin : llegada)).toMillis()/60000.0)));
                    }
                }
            }
            Map<Long,List<ResultadoCorrida.Vertice>> nodos=new HashMap<>();
            try(PreparedStatement ps=cn.prepareStatement("""
                    SELECT id_incidencia,orden,x,y FROM simulacion_bloqueo_nodo
                    WHERE id_simulacion=? ORDER BY id_incidencia,orden
                    """)) {
                ps.setLong(1,idSimulacion);
                try(ResultSet rs=ps.executeQuery()) {
                    while(rs.next()) nodos.computeIfAbsent(rs.getLong(1),k->new ArrayList<>())
                            .add(new ResultadoCorrida.Vertice(rs.getInt(2),rs.getInt(3),rs.getInt(4)));
                }
            }
            List<ResultadoCorrida.IncidenciaRegistrada> incidencias=new ArrayList<>();
            try(PreparedStatement ps=cn.prepareStatement("""
                    SELECT id_incidencia,tipo,fecha_ocurrencia,activa,fecha_inicio,fecha_fin,
                           id_vehiculo,tipo_averia,ubicacion_x,ubicacion_y,hora_retorno_estimada
                    FROM simulacion_incidencia WHERE id_simulacion=? ORDER BY id_incidencia
                    """)) {
                ps.setLong(1,idSimulacion);
                try(ResultSet rs=ps.executeQuery()) {
                    while(rs.next()) {
                        Timestamp ti=rs.getTimestamp(5), tf=rs.getTimestamp(6), tr=rs.getTimestamp(11);
                        Integer x=(Integer)rs.getObject(9),y=(Integer)rs.getObject(10);
                        long id=rs.getLong(1);
                        incidencias.add(new ResultadoCorrida.IncidenciaRegistrada(
                                id,rs.getString(2),rs.getTimestamp(3).toLocalDateTime(),rs.getBoolean(4),
                                ti==null?null:ti.toLocalDateTime(),tf==null?null:tf.toLocalDateTime(),
                                rs.getString(7),rs.getString(8),x,y,
                                tr==null?null:tr.toLocalDateTime(),List.copyOf(nodos.getOrDefault(id,List.of()))));
                    }
                }
            }
            List<ResultadoCorrida.MantenimientoRegistrado> mantenimientos=new ArrayList<>();
            try(PreparedStatement ps=cn.prepareStatement("""
                    SELECT id_mantenimiento,id_vehiculo,tipo,fecha_inicio,fecha_fin,activo
                    FROM simulacion_mantenimiento WHERE id_simulacion=? ORDER BY id_mantenimiento
                    """)) {
                ps.setLong(1,idSimulacion);
                try(ResultSet rs=ps.executeQuery()) {
                    while(rs.next()) mantenimientos.add(new ResultadoCorrida.MantenimientoRegistrado(
                            rs.getLong(1),rs.getString(2),rs.getString(3),
                            rs.getTimestamp(4).toLocalDateTime(),rs.getTimestamp(5).toLocalDateTime(),
                            rs.getBoolean(6)));
                }
            }
            double costo=0,distancia=0;
            try(PreparedStatement ps=cn.prepareStatement("""
                    SELECT COALESCE(SUM(x.costo_total),0),COALESCE(SUM(x.distancia_total_km),0)
                    FROM (
                        SELECT costo_total,distancia_total_km FROM simulacion_ruta
                        WHERE id_simulacion=?
                        UNION ALL
                        SELECT r.costo_total,r.distancia_total_km
                        FROM ruta r JOIN solucion s ON s.id_solucion=r.id_solucion
                        WHERE s.id_simulacion=? AND NOT EXISTS (
                            SELECT 1 FROM simulacion_ruta a
                            WHERE a.id_simulacion=s.id_simulacion AND a.id_ruta=r.id_ruta
                        )
                    ) x
                    """)) {
                ps.setLong(1,idSimulacion);
                ps.setLong(2,idSimulacion);
                try(ResultSet rs=ps.executeQuery()) {
                    if(rs.next()) {costo=rs.getDouble(1);distancia=rs.getDouble(2);}
                }
            }
            List<ResultadoCorrida.VehiculoInicial> flotaInicial = new ArrayList<>();
            try (PreparedStatement ps = cn.prepareStatement("""
                    SELECT id_vehiculo,tipo,capacidad_paquetes,velocidad_kmh,costo_por_km
                    FROM simulacion_vehiculo WHERE id_simulacion=? ORDER BY id_vehiculo
                    """)) {
                ps.setLong(1,idSimulacion);
                try (ResultSet rs=ps.executeQuery()) {
                    while(rs.next()) flotaInicial.add(new ResultadoCorrida.VehiculoInicial(
                            rs.getString(1),rs.getString(2),rs.getInt(3),rs.getDouble(4),rs.getDouble(5)));
                }
            }
            List<ResultadoCorrida.AlmacenInicial> almacenesIniciales = new ArrayList<>();
            try (PreparedStatement ps = cn.prepareStatement("""
                    SELECT id_almacen,nombre,tipo,ubicacion_x,ubicacion_y,capacidad_maxima
                    FROM simulacion_almacen WHERE id_simulacion=? ORDER BY id_almacen
                    """)) {
                ps.setLong(1,idSimulacion);
                try (ResultSet rs=ps.executeQuery()) {
                    while(rs.next()) almacenesIniciales.add(new ResultadoCorrida.AlmacenInicial(
                            rs.getString(1),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getInt(5),
                            (Integer)rs.getObject(6)));
                }
            }
            List<ResultadoCorrida.FraccionRegistrada> fraccionesHistoricas = new ArrayList<>();
            try (PreparedStatement ps = cn.prepareStatement("""
                    SELECT id_pedido,id_fraccion,cantidad_qq,id_vehiculo,estado,fecha_arribo,fecha_entrega
                    FROM simulacion_fraccion WHERE id_simulacion=? ORDER BY id_pedido,id_fraccion
                    """)) {
                ps.setLong(1, idSimulacion);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp arribo = rs.getTimestamp(6), entrega = rs.getTimestamp(7);
                        fraccionesHistoricas.add(new ResultadoCorrida.FraccionRegistrada(
                                rs.getLong(1),rs.getLong(2),rs.getInt(3),rs.getString(4),
                                rs.getString(5),arribo == null ? null : arribo.toLocalDateTime(),
                                entrega == null ? null : entrega.toLocalDateTime()));
                    }
                }
            }
            Long pedidoColapso = null;
            LocalDateTime instanteColapso = null;
            try (PreparedStatement ps = cn.prepareStatement("""
                    SELECT id_pedido,instante FROM simulacion_colapso WHERE id_simulacion=?
                    """)) {
                ps.setLong(1, idSimulacion);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        pedidoColapso = rs.getLong(1);
                        instanteColapso = rs.getTimestamp(2).toLocalDateTime();
                    }
                }
            }
            List<DiagnosticoPlanificacion> diagnosticos = new ArrayList<>();
            try (PreparedStatement ps = cn.prepareStatement("""
                    SELECT instante,id_pedido,motivo FROM simulacion_diagnostico
                    WHERE id_simulacion=? ORDER BY instante,id_pedido LIMIT 10000
                    """)) {
                ps.setLong(1, idSimulacion);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        diagnosticos.add(new DiagnosticoPlanificacion(
                                rs.getTimestamp(1).toLocalDateTime(), rs.getLong(2), rs.getString(3)));
                    }
                }
            }
            return new ResultadoCorrida(idSimulacion,escenario,estado,inicio,fin,tiempo,
                    pedidos.size(),entregados,retrasados,costo,distancia,
                    List.copyOf(pedidos),List.copyOf(incidencias),List.copyOf(mantenimientos),
                    List.copyOf(flotaInicial),List.copyOf(almacenesIniciales),
                    List.copyOf(fraccionesHistoricas), pedidoColapso, instanteColapso,
                    List.copyOf(diagnosticos));
        } catch(SQLException e) {
            throw new IllegalStateException("No se pudo consultar el resultado",e);
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
                int capacidadMaxima = vehiculos.stream().mapToInt(Vehiculo::getCapacidadPaquetes)
                        .max().orElseThrow(() -> new IllegalArgumentException("No hay flota configurada"));
                int capacidadDisponible = vehiculos.stream().filter(Vehiculo::isDisponible)
                        .mapToInt(Vehiculo::getCapacidadPaquetes).min().orElse(0);
                List<Pedido> pedidos = leerPedidosSimuladosPlanificables(
                        reloj, finVentana, ancho, alto, capacidadMaxima, capacidadDisponible);
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
                        true, cargarCalendarioBloqueos(reloj, reloj.plusHours(48)),
                        cargarDisponibilidadHasta(reloj, reloj.plusHours(48)));

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

    private Map<String, LocalDateTime> cargarDisponibilidadHasta(LocalDateTime desde, LocalDateTime hasta) {
        Map<String, LocalDateTime> limites = new HashMap<>();
        if (programadorIncidencias != null) {
            for (LocalDate dia = desde.toLocalDate(); !dia.isAfter(hasta.toLocalDate()); dia = dia.plusDays(1)) {
                for (var m : programadorIncidencias.generarDia(configuracionIncidencias.fechaInicio(), dia,
                        configuracionIncidencias.semilla(), anchoIncidencias, altoIncidencias, idsIncidencias).mantenimientos()) {
                    if (m.fin().isAfter(desde) && !m.inicio().isAfter(hasta))
                        limites.merge(m.idVehiculo(), m.inicio(), (a,b) -> a.isBefore(b) ? a : b);
                }
            }
        }
        try (Connection cn = fuente.getConnection(); PreparedStatement ps = cn.prepareStatement("""
                SELECT id_vehiculo, MIN(fecha_inicio) FROM mantenimiento_vehiculo
                WHERE activo=TRUE AND fecha_fin>? AND fecha_inicio<=? GROUP BY id_vehiculo
                """)) {
            ps.setTimestamp(1,Timestamp.valueOf(desde)); ps.setTimestamp(2,Timestamp.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) limites.merge(rs.getString(1), rs.getTimestamp(2).toLocalDateTime(),
                        (a,b) -> a.isBefore(b) ? a : b);
            }
        } catch(SQLException e) { throw new IllegalStateException("No se pudo consultar la disponibilidad futura", e); }
        return Map.copyOf(limites);
    }

    @Override
    public synchronized List<Bloqueo> cargarCalendarioBloqueos(LocalDateTime desde, LocalDateTime hasta) {
        Map<String,Bloqueo> todos = new LinkedHashMap<>();
        if(programadorIncidencias!=null) {
            for(LocalDate d=desde.toLocalDate().minusDays(programadorIncidencias.diasAntelacionBloqueos());!d.isAfter(hasta.toLocalDate());d=d.plusDays(1)) {
                for(Bloqueo b:programadorIncidencias.generarDia(configuracionIncidencias.fechaInicio(),d,
                        configuracionIncidencias.semilla(),anchoIncidencias,altoIncidencias,idsIncidencias).bloqueos()) {
                    if(b.fin().isAfter(desde) && !b.inicio().isAfter(hasta)) todos.put(claveBloqueo(b),b);
                }
            }
        }
        try(Connection cn=fuente.getConnection(); PreparedStatement ps=cn.prepareStatement("""
                SELECT b.id_incidencia,b.fecha_inicio,b.fecha_fin,bn.x,bn.y
                FROM bloqueo b JOIN incidencia i ON i.id_incidencia=b.id_incidencia
                JOIN bloqueo_nodo bn ON bn.id_incidencia=b.id_incidencia
                WHERE i.activa=TRUE AND b.fecha_inicio<=? AND b.fecha_fin>?
                ORDER BY b.id_incidencia,bn.orden
                """)) {
            ps.setTimestamp(1,Timestamp.valueOf(hasta)); ps.setTimestamp(2,Timestamp.valueOf(desde));
            Map<Long,List<Nodo>> nodos=new LinkedHashMap<>();
            Map<Long,LocalDateTime> inicios=new HashMap<>(), fines=new HashMap<>();
            try(ResultSet rs=ps.executeQuery()) { while(rs.next()) {
                long id=rs.getLong(1); inicios.put(id,rs.getTimestamp(2).toLocalDateTime());
                fines.put(id,rs.getTimestamp(3).toLocalDateTime());
                nodos.computeIfAbsent(id,k->new ArrayList<>()).add(new Nodo(rs.getInt(4),rs.getInt(5)));
            }}
            nodos.forEach((id,vertices)->{
                Bloqueo b=new Bloqueo(inicios.get(id),fines.get(id),vertices);
                todos.put(claveBloqueo(b),b);
            });
        } catch(SQLException e) { throw new IllegalStateException("No se pudo leer el calendario de bloqueos",e); }
        return List.copyOf(todos.values());
    }

    private static String claveBloqueo(Bloqueo b) {
        return b.inicio()+"/"+b.fin()+"/"+b.vertices().stream()
                .map(n->n.getX()+","+n.getY()).collect(java.util.stream.Collectors.joining(";"));
    }

    @Override
    public synchronized LocalDateTime proximoEvento(LocalDateTime desde,LocalDateTime hasta) {
        asegurarDemandaGeneradaHasta(hasta.toLocalDate());
        LocalDateTime siguiente=hasta;
        for(PedidoSimulado p:pedidosSimulados.values()) {
            if(p.fechaLlegada.isAfter(desde) && p.fechaLlegada.isBefore(siguiente)) siguiente=p.fechaLlegada;
            if(p.id>0 && p.fechaArribo==null) {
                LocalDateTime vence=p.fechaLimite().plusNanos(1);
                if(vence.isAfter(desde) && vence.isBefore(siguiente)) siguiente=vence;
            }
        }
        for (var averia : cargarAveriasActivas(desde).values()) {
            LocalDateTime retorno=averia.horaRetornoEstimada();
            if(retorno!=null && retorno.isAfter(desde) && retorno.isBefore(siguiente)) siguiente=retorno;
            if(averia.tipoAveria()!=pe.edu.pucp.sisrap.flota.dominio.TipoAveria.TIPO1_MENOR) {
                LocalDateTime traslado=averia.fechaOcurrencia().plusHours(4);
                if(traslado.isAfter(desde) && traslado.isBefore(siguiente)) siguiente=traslado;
            }
        }
        for(Bloqueo b:cargarCalendarioBloqueos(desde,hasta)) {
            if(b.inicio().isAfter(desde) && b.inicio().isBefore(siguiente)) siguiente=b.inicio();
            if(b.fin().isAfter(desde) && b.fin().isBefore(siguiente)) siguiente=b.fin();
        }
        if(programadorIncidencias!=null) {
            for(LocalDate d=desde.toLocalDate().minusDays(1);!d.isAfter(hasta.toLocalDate());d=d.plusDays(1)) {
                for(var m:programadorIncidencias.generarDia(configuracionIncidencias.fechaInicio(),d,
                        configuracionIncidencias.semilla(),anchoIncidencias,altoIncidencias,idsIncidencias).mantenimientos()) {
                    if(m.inicio().isAfter(desde)&&m.inicio().isBefore(siguiente)) siguiente=m.inicio();
                    if(m.fin().isAfter(desde)&&m.fin().isBefore(siguiente)) siguiente=m.fin();
                }
            }
        }
        try(Connection cn=fuente.getConnection(); PreparedStatement ps=cn.prepareStatement("""
                SELECT fecha_inicio,fecha_fin FROM mantenimiento_vehiculo
                WHERE activo=TRUE AND fecha_inicio<=? AND fecha_fin>?
                """)) {
            ps.setTimestamp(1,Timestamp.valueOf(hasta));ps.setTimestamp(2,Timestamp.valueOf(desde));
            try(ResultSet rs=ps.executeQuery()) {while(rs.next()) for(int i=1;i<=2;i++) {
                LocalDateTime t=rs.getTimestamp(i).toLocalDateTime();
                if(t.isAfter(desde)&&t.isBefore(siguiente)) siguiente=t;
            }}
        }catch(SQLException e){throw new IllegalStateException("No se pudieron consultar eventos operativos",e);}
        return siguiente;
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
                     SELECT id_vehiculo, estado, posicion_x, posicion_y, capacidad_paquetes
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
    public Map<String, AveriaVehiculo> cargarAveriasActivas(LocalDateTime reloj) {
        Map<String, AveriaVehiculo> resultado = new HashMap<>();
        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     SELECT i.id_incidencia, i.fecha_ocurrencia,
                            a.id_vehiculo, a.tipo_averia, a.ubicacion_x,
                            a.ubicacion_y, a.hora_retorno_estimada
                     FROM incidencia i
                     JOIN averia a ON a.id_incidencia = i.id_incidencia
                     WHERE i.activa = TRUE
                       AND i.fecha_ocurrencia <= ?
                       AND (a.hora_retorno_estimada IS NULL
                            OR a.hora_retorno_estimada > ?)
                     ORDER BY i.fecha_ocurrencia DESC
                     """)) {
            Timestamp instante = Timestamp.valueOf(reloj);
            ps.setTimestamp(1, instante);
            ps.setTimestamp(2, instante);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String vehiculo = rs.getString("id_vehiculo");
                    Timestamp retorno = rs.getTimestamp("hora_retorno_estimada");
                    resultado.putIfAbsent(vehiculo, new AveriaVehiculo(
                            rs.getLong("id_incidencia"), vehiculo,
                            TipoAveria.valueOf(rs.getString("tipo_averia")),
                            rs.getInt("ubicacion_x"), rs.getInt("ubicacion_y"),
                            rs.getTimestamp("fecha_ocurrencia").toLocalDateTime(),
                            retorno == null ? null : retorno.toLocalDateTime(), true));
                }
            }
            return Map.copyOf(resultado);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron consultar las averías activas", e);
        }
    }

    @Override
    public List<pe.edu.pucp.sisrap.simulacion.dominio.PerfilFlotaSimulacion> cargarPerfilesFlota() {
        List<pe.edu.pucp.sisrap.simulacion.dominio.PerfilFlotaSimulacion> perfiles = new ArrayList<>();
        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     SELECT id_vehiculo, tipo, costo_por_km
                     FROM vehiculo ORDER BY id_vehiculo
                     """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                perfiles.add(new pe.edu.pucp.sisrap.simulacion.dominio.PerfilFlotaSimulacion(
                        rs.getString(1), rs.getString(2), rs.getDouble(3)));
            }
            return List.copyOf(perfiles);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron cargar los datos de la flota", e);
        }
    }

    @Override
    public List<VehiculoSnapshot> cargarVehiculosSnapshot() {
        List<VehiculoSnapshot> salida = new ArrayList<>();
        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     SELECT id_vehiculo, estado, posicion_x, posicion_y, capacidad_paquetes
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
                        List.of(),
                        rs.getInt("capacidad_paquetes"),
                        0, 0, 0));
            }
            return List.copyOf(salida);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consultar la flota", e);
        }
    }

    @Override
    public synchronized void sincronizarDisponibilidad(LocalDateTime reloj) {
        sincronizarYCargarDisponibilidad(reloj);
    }

    @Override
    public synchronized Map<String, VehiculoPersistido> sincronizarYCargarDisponibilidad(LocalDateTime reloj) {
        try (Connection cn = fuente.getConnection()) {
            cn.setAutoCommit(false);
            try {
                LocalDateTime inicioTurno =
                        pe.edu.pucp.sisrap.planificador.dominio.modelo.JornadaOperativa.inicioTurno(reloj);
                if (!inicioTurno.equals(turnoAsignado)) {
                    int hora = inicioTurno.getHour();
                    int turno = hora == 7 ? 1 : hora == 15 ? 2 : 3;
                    try (PreparedStatement ps = cn.prepareStatement("""
                            UPDATE vehiculo
                            SET id_conductor_actual=CONCAT('SIS-',id_vehiculo,'-',?)
                            """)) {
                        ps.setInt(1, turno);
                        ps.executeUpdate();
                    }
                }
                finalizarAveriasConRetornoCumplido(cn, reloj);
                Map<String, PuntoSimulacion> averiados = leerAveriasActivas(cn, reloj);
                Set<String> mantenimientos = leerMantenimientosActivos(cn, reloj);
                Map<String, VehiculoPersistido> vehiculos = leerVehiculosPersistidos(cn);

                Map<String, VehiculoPersistido> actualizados = new HashMap<>();
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
                        actualizados.put(v.idVehiculo(), v);
                        PuntoSimulacion ubicacionAveria = averiados.get(v.idVehiculo());
                        if (ubicacionAveria != null) {
                            actualizados.put(v.idVehiculo(), new VehiculoPersistido(v.idVehiculo(),
                                    "EN_AVERIA", ubicacionAveria.x(), ubicacionAveria.y()));
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
                            actualizados.put(v.idVehiculo(), new VehiculoPersistido(v.idVehiculo(),
                                    "EN_MANTENIMIENTO", v.x(), v.y()));
                            if (!"EN_MANTENIMIENTO".equals(v.estado())) {
                                psEstado.setString(1, "EN_MANTENIMIENTO");
                                psEstado.setString(2, v.idVehiculo());
                                psEstado.addBatch();
                            }
                            continue;
                        }

                        if ("EN_AVERIA".equals(v.estado())
                                || "EN_MANTENIMIENTO".equals(v.estado())) {
                            actualizados.put(v.idVehiculo(), new VehiculoPersistido(v.idVehiculo(),
                                    "DISPONIBLE", v.x(), v.y()));
                            psEstado.setString(1, "DISPONIBLE");
                            psEstado.setString(2, v.idVehiculo());
                            psEstado.addBatch();
                        }
                    }

                    psAveria.executeBatch();
                    psEstado.executeBatch();
                }

                cn.commit();
                turnoAsignado = pe.edu.pucp.sisrap.planificador.dominio.modelo.JornadaOperativa.inicioTurno(reloj);
                return Map.copyOf(actualizados);
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
    public synchronized long idPedidoVisible(long idPedidoPlanificado) {
        FraccionPedido fraccion = fracciones.get(idPedidoPlanificado);
        return fraccion == null ? idPedidoPlanificado : fraccion.idPedido;
    }

    @Override
    public synchronized List<Pedido> dividirPedidoEnRuta(Pedido pedido, int capacidadPorVehiculo) {
        resumenCache = null;
        if (pedido == null || capacidadPorVehiculo <= 0) {
            throw new IllegalArgumentException("Pedido y capacidad de trasvase inválidos");
        }
        if (pedido.getCantidadQq() <= capacidadPorVehiculo) return List.of(pedido);

        FraccionPedido anterior = fracciones.get(pedido.getIdPedido());
        PedidoSimulado padre = pedidosSimulados.get(
                anterior == null ? pedido.getIdPedido() : anterior.idPedido);
        if (padre == null || padre.id < 0) {
            throw new IllegalArgumentException("Pedido de trasvase inexistente: "
                    + pedido.getIdPedido());
        }
        int cantidadOriginal = anterior == null ? padre.cantidad : anterior.cantidad;
        EstadoPedido estadoOriginal = anterior == null ? padre.estado : anterior.estado;
        if (cantidadOriginal != pedido.getCantidadQq()
                || (estadoOriginal != EstadoPedido.EN_RUTA
                    && estadoOriginal != EstadoPedido.RETRASADO)) {
            throw new IllegalStateException(
                    "Solo es posible fraccionar paquetes que permanecen en ruta");
        }
        if (anterior == null && !padre.fracciones.isEmpty()) {
            throw new IllegalStateException(
                    "El pedido ya se encuentra dividido en fracciones");
        }

        String vehiculoAnterior = anterior == null ? null : anterior.idVehiculo;
        if (anterior != null) {
            padre.fracciones.remove(anterior);
            fracciones.remove(anterior.id);
        }
        int restante = cantidadOriginal;
        List<Pedido> partes = new ArrayList<>();
        while (restante > 0) {
            int cantidad = Math.min(restante, capacidadPorVehiculo);
            FraccionPedido parte = new FraccionPedido(siguienteFraccion--, padre.id, cantidad);
            parte.estado = estadoOriginal;
            parte.idVehiculo = vehiculoAnterior;
            padre.fracciones.add(parte);
            fracciones.put(parte.id, parte);
            partes.add(new Pedido(
                    parte.id, pedido.getIdCliente(), cantidad,
                    pedido.getPrioridad(), pedido.getUbicacion(),
                    pedido.getFechaLlegada(), pedido.getHorasLimite()));
            restante -= cantidad;
        }
        sincronizarPedidoAgrupado(padre);
        return List.copyOf(partes);
    }

    @Override
    public synchronized List<Pedido> registrarTrasvase(
            List<Pedido> pedidos, String idVehiculo) {
        resumenCache = null;
        if (pedidos == null || idVehiculo == null || idVehiculo.isBlank()) {
            throw new IllegalArgumentException("Datos del trasvase inválidos");
        }
        List<Pedido> resultado = new ArrayList<>();
        for (Pedido pedido : pedidos) {
            FraccionPedido fraccion = fracciones.get(pedido.getIdPedido());
            if (fraccion != null) {
                fraccion.idVehiculo = idVehiculo;
                resultado.add(pedido);
                continue;
            }
            PedidoSimulado padre = pedidosSimulados.get(pedido.getIdPedido());
            if (padre == null || padre.id < 0 || !padre.fracciones.isEmpty()
                    || (padre.estado != EstadoPedido.EN_RUTA
                        && padre.estado != EstadoPedido.RETRASADO)) {
                throw new IllegalStateException(
                        "El pedido no está disponible para su trasvase: "
                                + pedido.getIdPedido());
            }
            FraccionPedido nueva = new FraccionPedido(
                    siguienteFraccion--, padre.id, pedido.getCantidadQq());
            nueva.estado = padre.estado;
            nueva.idVehiculo = idVehiculo;
            padre.fracciones.add(nueva);
            fracciones.put(nueva.id, nueva);
            resultado.add(new Pedido(nueva.id, pedido.getIdCliente(),
                    pedido.getCantidadQq(), pedido.getPrioridad(),
                    pedido.getUbicacion(), pedido.getFechaLlegada(),
                    pedido.getHorasLimite()));
        }
        return List.copyOf(resultado);
    }

    @Override
    public synchronized void marcarPedidosEnRuta(Collection<Long> idsPedidos) {
        resumenCache = null;
        if (idsPedidos == null || idsPedidos.isEmpty()) return;
        for (Long id : idsPedidos) {
            FraccionPedido fraccion = fracciones.get(id);
            if (fraccion != null) {
                if (pendienteParaPlanificar(fraccion.estado)) {
                    fraccion.estado = EstadoPedido.EN_RUTA;
                    sincronizarPedidoAgrupado(pedidosSimulados.get(fraccion.idPedido));
                }
                continue;
            }
            PedidoSimulado pedido = pedidosSimulados.get(id);
            if (pedido != null && pendienteParaPlanificar(pedido.estado)) {
                cambiarEstadoPedido(id, "EN_RUTA", null);
                pedido.estado = EstadoPedido.EN_RUTA;
            }
        }
    }

    @Override
    public synchronized void reencolarPedidos(Collection<Long> idsPedidos, LocalDateTime reloj) {
        resumenCache = null;
        if (idsPedidos == null || idsPedidos.isEmpty()) return;
        for (Long id : idsPedidos) {
            FraccionPedido fraccion = fracciones.get(id);
            if (fraccion != null) {
                if (fraccion.estado == EstadoPedido.EN_RUTA) {
                    PedidoSimulado padre = pedidosSimulados.get(fraccion.idPedido);
                    fraccion.idVehiculo = null;
                    fraccion.estado = padre.fechaLimite().isBefore(reloj)
                            ? EstadoPedido.RETRASADO : EstadoPedido.REASIGNADO;
                    sincronizarPedidoAgrupado(padre);
                }
                continue;
            }
            PedidoSimulado pedido = pedidosSimulados.get(id);
            if (pedido == null || pedido.estado != EstadoPedido.EN_RUTA) continue;
            EstadoPedido nuevo = pedido.fechaLimite().isBefore(reloj)
                    ? EstadoPedido.RETRASADO : EstadoPedido.REASIGNADO;
            cambiarEstadoPedido(id, nuevo.name(), null);
            pedido.estado = nuevo;
        }
    }

    @Override
    public synchronized void entregarPedido(long idPedido, LocalDateTime fechaEntrega) {
        resumenCache = null;
        FraccionPedido fraccion = fracciones.get(idPedido);
        if (fraccion != null) {
            if (fraccion.estado == EstadoPedido.EN_RUTA || fraccion.estado == EstadoPedido.RETRASADO) {
                fraccion.estado = EstadoPedido.ENTREGADO;
                fraccion.fechaEntrega = fechaEntrega;
                sincronizarPedidoAgrupado(pedidosSimulados.get(fraccion.idPedido));
            }
            return;
        }
        PedidoSimulado pedido = pedidosSimulados.get(idPedido);
        if (pedido == null || pedido.id < 0) {
            throw new IllegalArgumentException("Pedido simulado inexistente: " + idPedido);
        }
        if (pedido.estado == EstadoPedido.EN_RUTA || pedido.estado == EstadoPedido.RETRASADO) {
            cambiarEstadoPedido(idPedido, "ENTREGADO", fechaEntrega);
            pedido.estado = EstadoPedido.ENTREGADO;
            pedido.fechaEntregaReal = fechaEntrega;
        }
    }

    @Override
    public synchronized void registrarArribo(long idPedido, LocalDateTime fechaArribo) {
        resumenCache = null;
        FraccionPedido fraccion = fracciones.get(idPedido);
        PedidoSimulado pedido = fraccion != null
                ? pedidosSimulados.get(fraccion.idPedido) : pedidosSimulados.get(idPedido);
        if (pedido == null || pedido.id < 0) {
            throw new IllegalArgumentException("Pedido no disponible: " + idPedido);
        }
        if (fraccion != null && (fraccion.fechaArribo == null || fechaArribo.isBefore(fraccion.fechaArribo)))
            fraccion.fechaArribo = fechaArribo;
        LocalDateTime arriboPadre = fraccion == null ? fechaArribo :
                pedido.fracciones.stream().allMatch(f -> f.fechaArribo != null)
                ? pedido.fracciones.stream().map(f -> f.fechaArribo)
                        .max(LocalDateTime::compareTo).orElseThrow() : null;
        if (arriboPadre == null) return;
        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     INSERT INTO pedido_arribo(id_pedido,fecha_arribo) VALUES(?,?)
                     ON DUPLICATE KEY UPDATE fecha_arribo=LEAST(fecha_arribo,VALUES(fecha_arribo))
                     """)) {
            ps.setLong(1,pedido.id);
            ps.setTimestamp(2,Timestamp.valueOf(arriboPadre));
            ps.executeUpdate();
            if (pedido.fechaArribo == null || pedido.fechaArribo.isAfter(arriboPadre)) {
                pedido.fechaArribo = arriboPadre;
            }
        } catch(SQLException e) {
            throw new IllegalStateException("No se pudo registrar el arribo del pedido",e);
        }
    }

    @Override
    public synchronized void marcarPedidosRetrasados(LocalDateTime reloj) {
        resumenCache = null;
        for (PedidoSimulado pedido : pedidosSimulados.values()) {
            if (pedido.id < 0 || !pedido.fechaLimite().isBefore(reloj) ||
                    (pedido.fechaArribo != null && !pedido.fechaArribo.isAfter(pedido.fechaLimite()))) {
                continue;
            }
            if (!pedido.fracciones.isEmpty()) {
                for (FraccionPedido fraccion : pedido.fracciones) {
                    // Conservar EN_RUTA: el vencimiento contractual no descarga ni
                    // retira físicamente la mercancía del vehículo.
                    if (fraccion.fechaArribo == null
                            && fraccion.estado != EstadoPedido.ENTREGADO
                            && fraccion.estado != EstadoPedido.EN_RUTA) {
                        fraccion.estado = EstadoPedido.RETRASADO;
                    }
                }
                sincronizarPedidoAgrupado(pedido);
            } else if (pedido.estado != EstadoPedido.ENTREGADO
                    && pedido.estado != EstadoPedido.EN_RUTA
                    && pedido.estado != EstadoPedido.RETRASADO) {
                cambiarEstadoPedido(pedido.id, "RETRASADO", null);
                pedido.estado = EstadoPedido.RETRASADO;
            }
        }
    }

    @Override
    public synchronized java.util.Optional<IncumplimientoPedido> primerVencimientoEntre(
            LocalDateTime desde, LocalDateTime hasta) {
        return pedidosSimulados.values().stream()
                .filter(p -> p.id > 0 && !p.fechaLlegada.isAfter(hasta))
                .filter(p -> p.fechaLimite().isAfter(desde) && !p.fechaLimite().isAfter(hasta))
                .filter(p -> p.fechaArribo == null || p.fechaArribo.isAfter(p.fechaLimite()))
                .map(p -> new IncumplimientoPedido(p.id, p.fechaLimite().plusNanos(1)))
                .min(Comparator.comparing(IncumplimientoPedido::instante)
                        .thenComparingLong(IncumplimientoPedido::idPedido));
    }

    @Override
    public synchronized java.util.Optional<IncumplimientoPedido> primerIncumplimiento(
            LocalDateTime reloj) {
        return pedidosSimulados.values().stream()
                .filter(p -> p.id > 0 && p.fechaLimite().isBefore(reloj))
                .filter(p -> p.fechaArribo == null || p.fechaArribo.isAfter(p.fechaLimite()))
                .map(p -> new IncumplimientoPedido(p.id, p.fechaLimite().plusNanos(1)))
                .min(Comparator.comparing(IncumplimientoPedido::instante)
                        .thenComparingLong(IncumplimientoPedido::idPedido));
    }

    @Override
    public void guardarColapso(long idSimulacion, IncumplimientoPedido incumplimiento) {
        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     INSERT INTO simulacion_colapso (id_simulacion,id_pedido,instante)
                     VALUES(?,?,?)
                     ON DUPLICATE KEY UPDATE id_pedido=VALUES(id_pedido),instante=VALUES(instante)
                     """)) {
            ps.setLong(1, idSimulacion);
            ps.setLong(2, incumplimiento.idPedido());
            ps.setTimestamp(3, Timestamp.valueOf(incumplimiento.instante()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo registrar el colapso logístico", e);
        }
    }

    private void archivarFracciones(Connection cn, long idSimulacion) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("""
                INSERT INTO simulacion_fraccion
                  (id_simulacion,id_pedido,id_fraccion,cantidad_qq,id_vehiculo,
                   estado,fecha_arribo,fecha_entrega)
                VALUES(?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE estado=VALUES(estado),
                  id_vehiculo=VALUES(id_vehiculo),fecha_arribo=VALUES(fecha_arribo),
                  fecha_entrega=VALUES(fecha_entrega)
                """)) {
            for (FraccionPedido fraccion : fracciones.values()) {
                ps.setLong(1, idSimulacion);
                ps.setLong(2, fraccion.idPedido);
                ps.setLong(3, fraccion.id);
                ps.setInt(4, fraccion.cantidad);
                ps.setString(5, fraccion.idVehiculo);
                ps.setString(6, fraccion.estado.name());
                ps.setTimestamp(7, fraccion.fechaArribo == null ? null
                        : Timestamp.valueOf(fraccion.fechaArribo));
                ps.setTimestamp(8, fraccion.fechaEntrega == null ? null
                        : Timestamp.valueOf(fraccion.fechaEntrega));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void cambiarEstadoPedido(long id, String estado, LocalDateTime entrega) {
        try (Connection cn = fuente.getConnection();
             PreparedStatement ps = cn.prepareStatement("""
                     UPDATE pedido SET estado=?,fecha_entrega_real=COALESCE(?,fecha_entrega_real)
                     WHERE id_pedido=?
                     """)) {
            ps.setString(1, estado);
            ps.setTimestamp(2, entrega == null ? null : Timestamp.valueOf(entrega));
            ps.setLong(3, id);
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("Pedido operativo no encontrado: " + id);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo actualizar el pedido " + id, e);
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

        if (resumenCache != null && resumenCache.inicio().equals(inicio)
                && resumenCache.fin().equals(fin) && resumenCache.reloj().equals(reloj))
            return resumenCache.valor();

        int total = 0;
        int futuros = 0;
        int pendientes = 0;
        int enRuta = 0;
        int reasignados = 0;
        int retrasados = 0;
        int entregados = 0;
        int retrasadosSinEntregar = 0;
        int retrasadosEntregados = 0;
        int retrasadosEnRuta = 0;
        int arribadosEnPlazo = 0;
        int sinArriboVigentes = 0;

        for (PedidoSimulado pedido : pedidosSimulados.values()) {
            if (pedido.id < 0 || pedido.fechaLlegada.isBefore(inicio)
                    || !pedido.fechaLlegada.isBefore(fin)) {
                continue;
            }
            total++;
            if (pedido.fechaLlegada.isAfter(reloj)) {
                futuros++;
                continue;
            }

            // El estado físico y el SLA son dos indicadores distintos.
            // Un pedido entregado tarde suma tanto en ENTREGADOS como en
            // FUERA DE PLAZO: no vuelve a figurar como pendiente de reparto.
            boolean fueraDePlazo = (pedido.fechaArribo != null
                    && pedido.fechaArribo.isAfter(pedido.fechaLimite()))
                    || (pedido.fechaLimite().isBefore(reloj)
                    && (pedido.fechaArribo == null
                    || pedido.fechaArribo.isAfter(pedido.fechaLimite())));
            if (pedido.fechaArribo != null && !pedido.fechaArribo.isAfter(pedido.fechaLimite())) arribadosEnPlazo++;
            else if (pedido.fechaArribo == null && !pedido.fechaLimite().isBefore(reloj)) sinArriboVigentes++;
            boolean entregado = pedido.estado == EstadoPedido.ENTREGADO;
            boolean enTraslado = pedido.estado == EstadoPedido.EN_RUTA
                    || pedido.fracciones.stream()
                            .anyMatch(f -> f.estado == EstadoPedido.EN_RUTA);

            if (entregado) entregados++;
            else switch (pedido.estado) {
                case PENDIENTE, RETRASADO -> {
                    if (enTraslado) enRuta++;
                    else pendientes++;
                }
                case EN_RUTA -> enRuta++;
                case REASIGNADO -> reasignados++;
                case ENTREGADO -> { /* ya contabilizado */ }
            }

            if (fueraDePlazo) {
                retrasados++;
                if (entregado) retrasadosEntregados++;
                else {
                    retrasadosSinEntregar++;
                    if (enTraslado) retrasadosEnRuta++;
                }
            }
        }

        var resumen = new ResumenPedidosSimulacion(
                total, futuros, pendientes, enRuta, reasignados,
                retrasados, entregados, retrasadosSinEntregar,
                retrasadosEntregados, retrasadosEnRuta, arribadosEnPlazo, sinArriboVigentes);
        resumenCache = new ResumenCache(inicio, fin, reloj, resumen);
        return resumen;
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
            int alto,
            int capacidadMaxima,
            int capacidadDisponible) {
        resumenCache = null;

        List<Pedido> salida = new ArrayList<>();
        for (PedidoSimulado pedido : pedidosSimulados.values()) {
            if (pedido.id < 0 || pedido.fechaLlegada.isAfter(reloj)
                    || !pedido.fechaLlegada.isBefore(finVentana)) {
                continue;
            }
            int capacidadParte = capacidadDisponible > 0 ? capacidadDisponible : capacidadMaxima;
            if (capacidadParte <= 0) throw new IllegalStateException("No hay capacidad de carga válida");
            if (pedido.fracciones.isEmpty() && pendienteParaPlanificar(pedido.estado)
                    && pedido.fechaArribo == null && pedido.cantidad > capacidadParte) {
                crearFraccionesPendientes(pedido, pedido.cantidad, capacidadParte, pedido.estado);
            } else if (!pedido.fracciones.isEmpty()) {
                for (FraccionPedido f : new ArrayList<>(pedido.fracciones)) {
                    if (pendienteParaPlanificar(f.estado) && f.fechaArribo == null
                            && f.idVehiculo == null && f.cantidad > capacidadParte) {
                        pedido.fracciones.remove(f); fracciones.remove(f.id);
                        crearFraccionesPendientes(pedido, f.cantidad, capacidadParte, f.estado);
                    }
                }
            }
            if (pedido.fracciones.isEmpty()) {
                if (pendienteParaPlanificar(pedido.estado)) salida.add(pedido.aPedido(ancho, alto));
            } else {
                for (FraccionPedido fraccion : pedido.fracciones) {
                    if (pendienteParaPlanificar(fraccion.estado)) {
                        salida.add(new Pedido(fraccion.id, pedido.cliente, fraccion.cantidad,
                                pedido.prioridad, nodo(pedido.x, pedido.y, ancho, alto),
                                pedido.fechaLlegada, pedido.horasLimite));
                    }
                }
            }
        }
        salida.sort(Comparator.comparing(Pedido::getFechaLimite)
                .thenComparing(Pedido::getIdPedido));
        return List.copyOf(salida);
    }

    private void crearFraccionesPendientes(PedidoSimulado pedido, int cantidad,
                                           int capacidad, EstadoPedido estado) {
        for (int restante=cantidad; restante>0;) {
            int unidades=Math.min(restante,capacidad);
            FraccionPedido f=new FraccionPedido(siguienteFraccion--,pedido.id,unidades);
            f.estado=estado;
            pedido.fracciones.add(f); fracciones.put(f.id,f);
            restante-=unidades;
        }
    }

    private static boolean pendienteParaPlanificar(EstadoPedido estado) {
        return estado == EstadoPedido.PENDIENTE || estado == EstadoPedido.REASIGNADO
                || estado == EstadoPedido.RETRASADO;
    }

    private void sincronizarPedidoAgrupado(PedidoSimulado pedido) {
        if (pedido.fracciones.isEmpty()) return;
        boolean todasEntregadas = pedido.fracciones.stream()
                .allMatch(f -> f.estado == EstadoPedido.ENTREGADO);
        EstadoPedido nuevo;
        if (todasEntregadas) nuevo = EstadoPedido.ENTREGADO;
        else if (pedido.fracciones.stream().anyMatch(f -> f.estado == EstadoPedido.RETRASADO))
            nuevo = EstadoPedido.RETRASADO;
        else if (pedido.fracciones.stream().anyMatch(f -> f.estado == EstadoPedido.EN_RUTA))
            nuevo = EstadoPedido.EN_RUTA;
        else if (pedido.fracciones.stream().anyMatch(f -> f.estado == EstadoPedido.REASIGNADO))
            nuevo = EstadoPedido.REASIGNADO;
        else nuevo = EstadoPedido.PENDIENTE;

        LocalDateTime ultimaEntrega = todasEntregadas ? pedido.fracciones.stream()
                .map(f -> f.fechaEntrega).max(LocalDateTime::compareTo).orElseThrow() : null;
        if (pedido.estado != nuevo || ultimaEntrega != null) {
            cambiarEstadoPedido(pedido.id, nuevo.name(), ultimaEntrega);
            pedido.estado = nuevo;
            if (ultimaEntrega != null) pedido.fechaEntregaReal = ultimaEntrega;
        }
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
                FROM pedido_historico_escenario
                WHERE escenario=?
                ORDER BY fecha_llegada, id_historico
                """)) {
            ps.setString(1, configuracion.escenario().name());
            try (ResultSet rs = ps.executeQuery()) {
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
        }

        if (todas.isEmpty()) {
            throw new IllegalArgumentException(
                    "No existen pedidos históricos para este escenario. Cargue al menos un archivo de ventas en sus Datos del caso"
            );
        }

        List<Integer> cantidadesDiarias = new ArrayList<>();
        Map<LocalDate, Integer> demandaPorFecha = new java.util.TreeMap<>();
        Map<LocalDate, Long> paquetesPorFecha = new java.util.TreeMap<>();
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT anio,mes FROM carga_pedidos_escenario WHERE escenario=? ORDER BY anio,mes")) {
            ps.setString(1, configuracion.escenario().name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.time.YearMonth periodo = java.time.YearMonth.of(rs.getInt(1), rs.getInt(2));
                    for (int d = 1; d <= periodo.lengthOfMonth(); d++) {
                        LocalDate fecha = periodo.atDay(d);
                        List<PlantillaHistorica> pedidosDia = porDia.getOrDefault(fecha, List.of());
                        cantidadesDiarias.add(pedidosDia.size());
                        demandaPorFecha.put(fecha, pedidosDia.size());
                        paquetesPorFecha.put(fecha,
                                pedidosDia.stream().mapToLong(PlantillaHistorica::cantidad).sum());
                    }
                }
            }
        }
        if (cantidadesDiarias.isEmpty()) {
            porDia.forEach((fecha, pedidosDia) -> {
                cantidadesDiarias.add(pedidosDia.size());
                demandaPorFecha.put(fecha, pedidosDia.size());
                paquetesPorFecha.put(fecha,
                        pedidosDia.stream().mapToLong(PlantillaHistorica::cantidad).sum());
            });
        }

        cantidadesDiarias.sort(Integer::compare);

        int mediana = cantidadesDiarias.get(cantidadesDiarias.size() / 2);
        if (cantidadesDiarias.size() % 2 == 0) {
            int inferior = cantidadesDiarias.get(cantidadesDiarias.size() / 2 - 1);
            mediana = (inferior + mediana) / 2;
        }

        Map<LocalDate, List<PlantillaHistorica>> inmutable = new LinkedHashMap<>();
        porDia.forEach((fecha, pedidos) ->
                inmutable.put(fecha, List.copyOf(pedidos))
        );

        pedidosSimulados.clear();
        fracciones.clear();
        siguienteFraccion = -1L;
        turnoAsignado = null;
        plantillasHistoricas = List.copyOf(todas);
        plantillasPorDia = new LinkedHashMap<>(inmutable);
        configuracionDemanda = configuracion;
        ultimoDiaGenerado = null;
        medianaPedidosDiarios = mediana;
        demandaHistoricaPorFecha = Map.copyOf(demandaPorFecha);
        paquetesHistoricosPorFecha = Map.copyOf(paquetesPorFecha);
        modeloColapso = null;
    }

    /** Calibración analítica inicial usando la flota ya aplicada del escenario. */
    private void prepararModeloColapso(Connection cn) throws SQLException {
        if (configuracionDemanda.escenario() != EscenarioSimulacion.COLAPSO_LOGISTICO) return;
        List<int[]> almacenes = new ArrayList<>();
        try (var ps = cn.prepareStatement("SELECT ubicacion_x,ubicacion_y FROM almacen");
             var rs = ps.executeQuery()) {
            while (rs.next()) almacenes.add(new int[]{rs.getInt(1), rs.getInt(2)});
        }
        if (almacenes.isEmpty()) throw new IllegalStateException("Se requieren almacenes para estimar la capacidad");
        double distanciaMedia = 0;
        double cantidadMedia = 0;
        for (PlantillaHistorica pedido : plantillasHistoricas) {
            double distancia = Double.POSITIVE_INFINITY;
            for (int[] almacen : almacenes) {
                distancia = Math.min(distancia,
                        Math.abs(pedido.x() - almacen[0]) + Math.abs(pedido.y() - almacen[1]));
            }
            distanciaMedia += distancia / plantillasHistoricas.size();
            cantidadMedia += (double) pedido.cantidad() / plantillasHistoricas.size();
        }
        // Aproximación de viajes de ida y vuelta con una hora por atención.
        // No representa al GA, bloqueos, mantenimiento, SLA ni rutas multi-entrega.
        // Solo orienta la intensidad inicial; nunca decide cuándo hubo colapso.
        double capacidadDiaria = 0;
        double horasEfectivas = pe.edu.pucp.sisrap.planificador.dominio.modelo.JornadaOperativa.horasEfectivas(
                configuracionDemanda.fechaInicio().atStartOfDay(),
                configuracionDemanda.fechaInicio().plusDays(1).atStartOfDay());
        try (var ps = cn.prepareStatement("SELECT capacidad_paquetes,velocidad_kmh FROM vehiculo");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                int capacidad = rs.getInt(1);
                double velocidad = rs.getDouble(2);
                if (capacidad <= 0 || !Double.isFinite(velocidad) || velocidad <= 0) {
                    throw new IllegalStateException("La flota contiene capacidad o velocidad inválida");
                }
                capacidadDiaria += horasEfectivas * Math.min(capacidad, cantidadMedia)
                        / (1 + 2 * distanciaMedia / velocidad);
            }
        }
        modeloColapso = new pe.edu.pucp.sisrap.simulacion.dominio.ModeloDemandaColapso(
                demandaHistoricaPorFecha, paquetesHistoricosPorFecha,
                configuracionDemanda.fechaInicio(), configuracionDemanda.semilla(),
                capacidadDiaria, configuracionDemanda.escenario().diasReferenciaTemporal(),
                tasaAumentoRespaldo, probabilidadAumentoRespaldo);
        LOG.info("Modelo colapso: capacidad aproximada={} qq/día, probabilidad aumento={}, "
                        + "intensidad={}, factor objetivo={}, referencia={} días / {} min reales",
                capacidadDiaria, modeloColapso.probabilidadAumento(), modeloColapso.intensidad(),
                modeloColapso.factorObjetivo(), configuracionDemanda.escenario().diasReferenciaTemporal(),
                configuracionDemanda.duracionRealObjetivo().toMinutes());
        if (!modeloColapso.objetivoAlcanzable()) {
            LOG.warn("Los aumentos históricos son demasiado infrecuentes para alcanzar la presión objetivo "
                    + "en el periodo de referencia sin saltos excesivos; la duración puede ser mayor");
        }
        if (modeloColapso.usaRespaldo()) {
            LOG.warn("El histórico no contiene aumentos semanales comparables: se utiliza el respaldo "
                            + "configurado (tasa={}, probabilidad={}), calibrado como presión de estrés",
                    tasaAumentoRespaldo, probabilidadAumentoRespaldo);
        }
        if (!modeloColapso.usaBloquesCompletos()) {
            LOG.warn("Sin bloques históricos completos de siete días; se remuestrean días compatibles disponibles");
        }
    }

    private synchronized void limpiarDemandaSimulada() {
        pedidosSimulados.clear();
        fracciones.clear();
        siguienteFraccion = -1L;
        plantillasHistoricas = List.of();
        plantillasPorDia = Map.of();
        configuracionDemanda = null;
        ultimoDiaGenerado = null;
        medianaPedidosDiarios = 0;
        demandaHistoricaPorFecha = Map.of();
        paquetesHistoricosPorFecha = Map.of();
        modeloColapso = null;
    }

    /** Genera, de forma determinista, los días que el reloj ya alcanzó. */
    private synchronized void asegurarDemandaGeneradaHasta(LocalDate fechaObjetivo) {
        if (configuracionDemanda == null || fechaObjetivo == null) {
            return;
        }

        LocalDate inicio = configuracionDemanda.fechaInicio();
        LocalDate ultimoPermitido = configuracionDemanda.fechaHoraFin()
                .minusNanos(1).toLocalDate();

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

    /**
     * El histórico sirve EXCLUSIVAMENTE para aprender el comportamiento.
     * Ninguna fila histórica se convierte directamente en un pedido operativo:
     * se muestrean tamaño, prioridad, destino y franja horaria, y se crean
     * clientes sintéticos con instantes nuevos al avanzar el reloj virtual.
     *
     * P50 es la referencia diaria. Para evitar un flujo idéntico todos los días,
     * el volumen varía aleatoriamente alrededor de P50 (semilla reproducible).
     * Colapso remuestrea bloques semanales y aplica una presión creciente
     * probabilística independiente de la estacionalidad histórica.
     */
    private void generarDiaSimulado(LocalDate fecha) {
        long indiceDia = ChronoUnit.DAYS.between(
                configuracionDemanda.fechaInicio(), fecha);
        Random random = new Random(
                mezclarSemilla(configuracionDemanda.semilla(), fecha.toEpochDay()));

        int cantidadObjetivo;
        pe.edu.pucp.sisrap.simulacion.dominio.ModeloDemandaColapso.Dia diaColapso = null;
        if (configuracionDemanda.escenario()
                == EscenarioSimulacion.COLAPSO_LOGISTICO) {
            if (modeloColapso == null) {
                throw new IllegalStateException("El modelo histórico de colapso no está preparado");
            }
            diaColapso = modeloColapso.dia(Math.toIntExact(indiceDia));
            cantidadObjetivo = diaColapso.pedidos();
            if (cantidadObjetivo >= BLOQUE_IDS_SINTETICOS) {
                throw new IllegalStateException("La demanda supera el límite de identificadores diarios de la simulación");
            }
            LOG.info("Colapso día {}: fecha histórica={}, pedidos={}, presión={}",
                    indiceDia + 1, diaColapso.fechaHistorica(), cantidadObjetivo, diaColapso.factorPresion());
        } else {
            // P50 = pronóstico central de pedidos por día, no pedidos precargados.
            // La variación acotada genera jornadas distintas sin perder la referencia P50.
            int variacion = (int) Math.round(
                    random.nextGaussian() * Math.sqrt(medianaPedidosDiarios));
            int minimo = Math.max(0, (int) Math.floor(medianaPedidosDiarios * 0.75));
            int maximo = Math.max(minimo,
                    (int) Math.ceil(medianaPedidosDiarios * 1.25));
            cantidadObjetivo = Math.max(minimo,
                    Math.min(maximo, medianaPedidosDiarios + variacion));
        }

        // Se respeta la distribución de los días de semana que se hayan cargado.
        // Si no hay precedentes de ese día, se usa la muestra histórica completa.
        List<PlantillaHistorica> candidatas = new ArrayList<>();
        if (diaColapso != null) {
            candidatas.addAll(plantillasPorDia.getOrDefault(diaColapso.fechaHistorica(), List.of()));
        } else {
            for (Map.Entry<LocalDate, List<PlantillaHistorica>> entry : plantillasPorDia.entrySet()) {
                if (entry.getKey().getDayOfWeek() == fecha.getDayOfWeek()) {
                    candidatas.addAll(entry.getValue());
                }
            }
            if (candidatas.isEmpty()) candidatas = plantillasHistoricas;
        }

        for (int i = 0; i < cantidadObjetivo; i++) {
            // La base del colapso reproduce todos los pedidos del día elegido.
            // El exceso se remuestrea de ese mismo día con identidades nuevas.
            PlantillaHistorica muestra = diaColapso != null && i < candidatas.size()
                    ? candidatas.get(i) : candidatas.get(random.nextInt(candidatas.size()));

            // Colapso conserva el horario del día remuestreado; los demás
            // escenarios distribuyen las llegadas dentro de su franja histórica.
            LocalTime hora = diaColapso != null ? muestra.horaLlegada() : LocalTime.of(
                    muestra.horaLlegada().getHour(),
                    random.nextInt(60),
                    random.nextInt(60));
            if (hora.equals(LocalTime.MIDNIGHT)) {
                hora = LocalTime.of(0, 0, 1);
            }
            LocalDateTime llegada = fecha.atTime(hora);
            if (llegada.isBefore(configuracionDemanda.fechaHoraInicio())
                    || !llegada.isBefore(configuracionDemanda.fechaHoraFin())) continue;

            long idTemporal = -Math.addExact(
                    Math.multiplyExact(indiceDia + 1L, BLOQUE_IDS_SINTETICOS),
                    i + 1L);
            // No se copia la identidad del cliente de ninguna venta histórica.
            String clienteNuevo = "SIM-" + (indiceDia + 1L) + "-" + (i + 1L);
            PedidoSimulado nuevo = new PedidoSimulado(
                    idTemporal, clienteNuevo, muestra.cantidad(),
                    muestra.prioridad(), muestra.horasLimite(),
                    llegada, muestra.x(), muestra.y());
            pedidosSimulados.put(idTemporal, nuevo);
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
        private long id;
        private final String cliente;
        private final int cantidad;
        private final TipoPrioridad prioridad;
        private final int horasLimite;
        private final LocalDateTime fechaLlegada;
        private final int x;
        private final int y;
        private EstadoPedido estado = EstadoPedido.PENDIENTE;
        private LocalDateTime fechaEntregaReal;
        private LocalDateTime fechaArribo;
        private final List<FraccionPedido> fracciones = new ArrayList<>();

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

    private static final class FraccionPedido {
        private final long id;
        private final long idPedido;
        private final int cantidad;
        private EstadoPedido estado = EstadoPedido.PENDIENTE;
        private String idVehiculo;
        private LocalDateTime fechaArribo;
        private LocalDateTime fechaEntrega;

        private FraccionPedido(long id, long idPedido, int cantidad) {
            this.id = id;
            this.idPedido = idPedido;
            this.cantidad = cantidad;
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
            salida.addAll(pe.edu.pucp.sisrap.planificador.dominio.modelo.BloqueosReticula.cerrarTramos(vertices));
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

        int[] central = leerCentral(cn);
        Map<String, PuntoSimulacion> salida = new HashMap<>();
        try (PreparedStatement ps = cn.prepareStatement("""
                SELECT a.id_vehiculo, a.ubicacion_x, a.ubicacion_y,
                       a.tipo_averia, i.fecha_ocurrencia
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
                    TipoAveria tipo = TipoAveria.valueOf(rs.getString("tipo_averia"));
                    LocalDateTime ocurrencia = rs.getTimestamp("fecha_ocurrencia")
                            .toLocalDateTime();
                    boolean trasladoCentral = tipo != TipoAveria.TIPO1_MENOR
                            && !reloj.isBefore(ocurrencia.plusHours(4));
                    int x = trasladoCentral ? central[0] : rs.getInt("ubicacion_x");
                    int y = trasladoCentral ? central[1] : rs.getInt("ubicacion_y");
                    salida.putIfAbsent(rs.getString("id_vehiculo"),
                            new PuntoSimulacion(x, y));
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
