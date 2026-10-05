package pe.edu.pucp.sisrap.flota.infraestructura;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import pe.edu.pucp.sisrap.flota.dominio.AveriaVehiculo;
import pe.edu.pucp.sisrap.flota.dominio.ConflictoVehiculoException;
import pe.edu.pucp.sisrap.flota.dominio.EstadoVehiculo;
import pe.edu.pucp.sisrap.flota.dominio.MantenimientoVehiculo;
import pe.edu.pucp.sisrap.flota.dominio.RepositorioVehiculos;
import pe.edu.pucp.sisrap.flota.dominio.TipoAveria;
import pe.edu.pucp.sisrap.flota.dominio.TipoMantenimiento;
import pe.edu.pucp.sisrap.flota.dominio.VehiculoOperativo;

@Repository
public class JdbcVehiculos implements RepositorioVehiculos {

    private final DataSource fuente;

    public JdbcVehiculos(DataSource fuente) {
        this.fuente = fuente;
    }

    @Override
    public List<VehiculoOperativo> listar() {

        String sql = """
                SELECT
                    id_vehiculo,
                    tipo,
                    capacidad_paquetes,
                    velocidad_kmh,
                    costo_por_km,
                    estado,
                    posicion_x,
                    posicion_y,
                    id_conductor_actual
                FROM vehiculo
                ORDER BY id_vehiculo
                """;

        List<VehiculoOperativo> salida = new ArrayList<>();

        try (
                Connection cn = fuente.getConnection();
                PreparedStatement ps = cn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
        ) {

            while (rs.next()) {
                salida.add(mapearVehiculo(rs));
            }

            return List.copyOf(salida);

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo consultar la flota",
                    e
            );
        }
    }

    @Override
    public Optional<VehiculoOperativo> buscar(String idVehiculo) {

        String sql = """
                SELECT
                    id_vehiculo,
                    tipo,
                    capacidad_paquetes,
                    velocidad_kmh,
                    costo_por_km,
                    estado,
                    posicion_x,
                    posicion_y,
                    id_conductor_actual
                FROM vehiculo
                WHERE id_vehiculo = ?
                """;

        try (
                Connection cn = fuente.getConnection();
                PreparedStatement ps = cn.prepareStatement(sql)
        ) {

            ps.setString(1, idVehiculo);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapearVehiculo(rs));
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo consultar el vehículo",
                    e
            );
        }
    }

    @Override
    public List<AveriaVehiculo> listarAverias(
            String idVehiculo
    ) {

        String sql = """
                SELECT
                    i.id_incidencia,
                    i.fecha_ocurrencia,
                    i.activa,
                    a.id_vehiculo,
                    a.tipo_averia,
                    a.ubicacion_x,
                    a.ubicacion_y,
                    a.hora_retorno_estimada
                FROM incidencia i
                INNER JOIN averia a
                    ON a.id_incidencia = i.id_incidencia
                WHERE a.id_vehiculo = ?
                ORDER BY i.fecha_ocurrencia DESC
                """;

        List<AveriaVehiculo> salida = new ArrayList<>();

        try (
                Connection cn = fuente.getConnection();
                PreparedStatement ps = cn.prepareStatement(sql)
        ) {

            ps.setString(1, idVehiculo);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    salida.add(mapearAveria(rs));
                }
            }

            return List.copyOf(salida);

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudieron consultar las averías",
                    e
            );
        }
    }

    @Override
    public AveriaVehiculo registrarAveria(
            String idVehiculo,
            TipoAveria tipoAveria,
            LocalDateTime fechaOcurrencia,
            LocalDateTime horaRetornoEstimada
    ) {

        try (Connection cn = fuente.getConnection()) {

            cn.setAutoCommit(false);

            try {

                VehiculoOperativo vehiculo =
                        buscarParaActualizar(cn, idVehiculo)
                                .orElseThrow(() ->
                                        new NoSuchElementException(
                                                "No existe el vehículo "
                                                        + idVehiculo
                                        )
                                );

                if (vehiculo.estado() == EstadoVehiculo.EN_AVERIA
                        || vehiculo.estado() == EstadoVehiculo.EN_MANTENIMIENTO
                        || existeAveriaActiva(cn, idVehiculo)) {

                    throw new ConflictoVehiculoException(
                            "El vehículo no está disponible para registrar una nueva avería"
                    );
                }

                long idIncidencia =
                        insertarIncidencia(
                                cn,
                                fechaOcurrencia
                        );

                insertarAveria(
                        cn,
                        idIncidencia,
                        idVehiculo,
                        tipoAveria,
                        vehiculo.posicionX(),
                        vehiculo.posicionY(),
                        horaRetornoEstimada
                );

                actualizarEstado(
                        cn,
                        idVehiculo,
                        EstadoVehiculo.EN_AVERIA
                );

                cn.commit();

                return new AveriaVehiculo(
                        idIncidencia,
                        idVehiculo,
                        tipoAveria,
                        vehiculo.posicionX(),
                        vehiculo.posicionY(),
                        fechaOcurrencia,
                        horaRetornoEstimada,
                        true
                );

            } catch (RuntimeException | SQLException e) {

                cn.rollback();

                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }

                throw new IllegalStateException(
                        "No se pudo registrar la avería",
                        e
                );
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo registrar la avería",
                    e
            );
        }
    }

    @Override
    public AveriaVehiculo resolverAveria(
            String idVehiculo,
            long idIncidencia
    ) {

        String sql = """
                SELECT
                    i.id_incidencia,
                    i.fecha_ocurrencia,
                    i.activa,
                    a.id_vehiculo,
                    a.tipo_averia,
                    a.ubicacion_x,
                    a.ubicacion_y,
                    a.hora_retorno_estimada
                FROM incidencia i
                INNER JOIN averia a
                    ON a.id_incidencia = i.id_incidencia
                WHERE i.id_incidencia = ?
                  AND a.id_vehiculo = ?
                FOR UPDATE
                """;

        try (Connection cn = fuente.getConnection()) {

            cn.setAutoCommit(false);

            try {

                AveriaVehiculo averia;

                try (PreparedStatement ps =
                             cn.prepareStatement(sql)) {

                    ps.setLong(1, idIncidencia);
                    ps.setString(2, idVehiculo);

                    try (ResultSet rs = ps.executeQuery()) {

                        if (!rs.next()) {
                            throw new NoSuchElementException(
                                    "No existe la avería indicada"
                            );
                        }

                        averia = mapearAveria(rs);
                    }
                }

                if (!averia.activa()) {
                    cn.commit();
                    return averia;
                }

                try (PreparedStatement ps =
                             cn.prepareStatement("""
                                     UPDATE incidencia
                                     SET activa = FALSE
                                     WHERE id_incidencia = ?
                                     """)) {

                    ps.setLong(1, idIncidencia);
                    ps.executeUpdate();
                }

                actualizarEstado(
                        cn,
                        idVehiculo,
                        EstadoVehiculo.DISPONIBLE
                );

                cn.commit();

                return new AveriaVehiculo(
                        averia.idIncidencia(),
                        averia.idVehiculo(),
                        averia.tipoAveria(),
                        averia.ubicacionX(),
                        averia.ubicacionY(),
                        averia.fechaOcurrencia(),
                        averia.horaRetornoEstimada(),
                        false
                );

            } catch (RuntimeException | SQLException e) {

                cn.rollback();

                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }

                throw new IllegalStateException(
                        "No se pudo resolver la avería",
                        e
                );
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo resolver la avería",
                    e
            );
        }
    }

    @Override
    public List<MantenimientoVehiculo> listarMantenimientos(
            String idVehiculo
    ) {

        String sql = """
                SELECT
                    id_mantenimiento,
                    id_vehiculo,
                    tipo,
                    fecha_inicio,
                    fecha_fin,
                    activo
                FROM mantenimiento_vehiculo
                WHERE id_vehiculo = ?
                ORDER BY fecha_inicio DESC, id_mantenimiento DESC
                """;

        List<MantenimientoVehiculo> salida = new ArrayList<>();

        try (
                Connection cn = fuente.getConnection();
                PreparedStatement ps = cn.prepareStatement(sql)
        ) {

            ps.setString(1, idVehiculo);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(mapearMantenimiento(rs));
                }
            }

            return List.copyOf(salida);

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudieron consultar los mantenimientos",
                    e
            );
        }
    }

    @Override
    public MantenimientoVehiculo registrarMantenimiento(
            String idVehiculo,
            TipoMantenimiento tipo,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin
    ) {

        if (tipo == null
                || fechaInicio == null
                || fechaFin == null
                || !fechaFin.isAfter(fechaInicio)) {
            throw new IllegalArgumentException(
                    "Datos de mantenimiento inválidos"
            );
        }

        try (Connection cn = fuente.getConnection()) {

            cn.setAutoCommit(false);

            try {
                buscarParaActualizar(cn, idVehiculo)
                        .orElseThrow(() ->
                                new NoSuchElementException(
                                        "No existe el vehículo " + idVehiculo
                                )
                        );

                if (existeMantenimientoSolapado(
                        cn,
                        idVehiculo,
                        fechaInicio,
                        fechaFin
                )) {
                    throw new ConflictoVehiculoException(
                            "El vehículo ya tiene un mantenimiento activo "
                                    + "que se superpone con el intervalo indicado"
                    );
                }

                long idMantenimiento;

                try (PreparedStatement ps =
                             cn.prepareStatement(
                                     """
                                     INSERT INTO mantenimiento_vehiculo(
                                         id_vehiculo,
                                         tipo,
                                         fecha_inicio,
                                         fecha_fin,
                                         activo
                                     )
                                     VALUES (?, ?, ?, ?, TRUE)
                                     """,
                                     Statement.RETURN_GENERATED_KEYS
                             )) {

                    ps.setString(1, idVehiculo);
                    ps.setString(2, tipo.name());
                    ps.setTimestamp(3, Timestamp.valueOf(fechaInicio));
                    ps.setTimestamp(4, Timestamp.valueOf(fechaFin));
                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                    "No se generó id de mantenimiento"
                            );
                        }
                        idMantenimiento = rs.getLong(1);
                    }
                }

                cn.commit();

                return new MantenimientoVehiculo(
                        idMantenimiento,
                        idVehiculo,
                        tipo,
                        fechaInicio,
                        fechaFin,
                        true
                );

            } catch (RuntimeException | SQLException e) {

                cn.rollback();

                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }

                throw new IllegalStateException(
                        "No se pudo registrar el mantenimiento",
                        e
                );
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo registrar el mantenimiento",
                    e
            );
        }
    }

    @Override
    public void cancelarMantenimiento(
            String idVehiculo,
            long idMantenimiento
    ) {

        try (Connection cn = fuente.getConnection()) {

            cn.setAutoCommit(false);

            try {
                boolean existe;
                boolean activo = false;

                try (PreparedStatement ps =
                             cn.prepareStatement("""
                                     SELECT activo
                                     FROM mantenimiento_vehiculo
                                     WHERE id_mantenimiento = ?
                                       AND id_vehiculo = ?
                                     FOR UPDATE
                                     """)) {

                    ps.setLong(1, idMantenimiento);
                    ps.setString(2, idVehiculo);

                    try (ResultSet rs = ps.executeQuery()) {
                        existe = rs.next();
                        if (existe) {
                            activo = rs.getBoolean("activo");
                        }
                    }
                }

                if (!existe) {
                    throw new NoSuchElementException(
                            "No existe el mantenimiento indicado"
                    );
                }

                if (activo) {
                    try (PreparedStatement ps =
                                 cn.prepareStatement("""
                                         UPDATE mantenimiento_vehiculo
                                         SET activo = FALSE
                                         WHERE id_mantenimiento = ?
                                           AND id_vehiculo = ?
                                         """)) {

                        ps.setLong(1, idMantenimiento);
                        ps.setString(2, idVehiculo);
                        ps.executeUpdate();
                    }
                }

                cn.commit();

            } catch (RuntimeException | SQLException e) {

                cn.rollback();

                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }

                throw new IllegalStateException(
                        "No se pudo cancelar el mantenimiento",
                        e
                );
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo cancelar el mantenimiento",
                    e
            );
        }
    }

    @Override
    public void actualizarPosicionYEstado(
            String idVehiculo,
            int x,
            int y,
            EstadoVehiculo estado
    ) {

        String sql = """
                UPDATE vehiculo
                SET posicion_x = ?,
                    posicion_y = ?,
                    estado = ?
                WHERE id_vehiculo = ?
                """;

        try (
                Connection cn = fuente.getConnection();
                PreparedStatement ps = cn.prepareStatement(sql)
        ) {

            ps.setInt(1, x);
            ps.setInt(2, y);
            ps.setString(3, estado.name());
            ps.setString(4, idVehiculo);

            if (ps.executeUpdate() == 0) {
                throw new NoSuchElementException(
                        "No existe el vehículo " + idVehiculo
                );
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo actualizar el vehículo",
                    e
            );
        }
    }

    private Optional<VehiculoOperativo> buscarParaActualizar(
            Connection cn,
            String idVehiculo
    ) throws SQLException {

        String sql = """
                SELECT
                    id_vehiculo,
                    tipo,
                    capacidad_paquetes,
                    velocidad_kmh,
                    costo_por_km,
                    estado,
                    posicion_x,
                    posicion_y,
                    id_conductor_actual
                FROM vehiculo
                WHERE id_vehiculo = ?
                FOR UPDATE
                """;

        try (PreparedStatement ps =
                     cn.prepareStatement(sql)) {

            ps.setString(1, idVehiculo);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapearVehiculo(rs));
            }
        }
    }

    private boolean existeAveriaActiva(
            Connection cn,
            String idVehiculo
    ) throws SQLException {

        String sql = """
                SELECT 1
                FROM incidencia i
                INNER JOIN averia a
                    ON a.id_incidencia = i.id_incidencia
                WHERE a.id_vehiculo = ?
                  AND i.activa = TRUE
                LIMIT 1
                """;

        try (PreparedStatement ps =
                     cn.prepareStatement(sql)) {

            ps.setString(1, idVehiculo);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean existeMantenimientoSolapado(
            Connection cn,
            String idVehiculo,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin
    ) throws SQLException {

        String sql = """
                SELECT 1
                FROM mantenimiento_vehiculo
                WHERE id_vehiculo = ?
                  AND activo = TRUE
                  AND fecha_inicio < ?
                  AND fecha_fin > ?
                LIMIT 1
                FOR UPDATE
                """;

        try (PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1, idVehiculo);
            ps.setTimestamp(2, Timestamp.valueOf(fechaFin));
            ps.setTimestamp(3, Timestamp.valueOf(fechaInicio));

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private long insertarIncidencia(
            Connection cn,
            LocalDateTime fecha
    ) throws SQLException {

        String sql = """
                INSERT INTO incidencia(
                    tipo,
                    fecha_ocurrencia,
                    activa
                )
                VALUES ('AVERIA', ?, TRUE)
                """;

        try (PreparedStatement ps =
                     cn.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS
                     )) {

            ps.setTimestamp(
                    1,
                    Timestamp.valueOf(fecha)
            );

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {

                if (!rs.next()) {
                    throw new SQLException(
                            "No se generó id de incidencia"
                    );
                }

                return rs.getLong(1);
            }
        }
    }

    private void insertarAveria(
            Connection cn,
            long idIncidencia,
            String idVehiculo,
            TipoAveria tipo,
            int x,
            int y,
            LocalDateTime retorno
    ) throws SQLException {

        String sql = """
                INSERT INTO averia(
                    id_incidencia,
                    id_vehiculo,
                    tipo_averia,
                    ubicacion_x,
                    ubicacion_y,
                    hora_retorno_estimada
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps =
                     cn.prepareStatement(sql)) {

            ps.setLong(1, idIncidencia);
            ps.setString(2, idVehiculo);
            ps.setString(3, tipo.name());
            ps.setInt(4, x);
            ps.setInt(5, y);

            if (retorno == null) {
                ps.setNull(
                        6,
                        java.sql.Types.TIMESTAMP
                );
            } else {
                ps.setTimestamp(
                        6,
                        Timestamp.valueOf(retorno)
                );
            }

            ps.executeUpdate();
        }
    }

    private void actualizarEstado(
            Connection cn,
            String idVehiculo,
            EstadoVehiculo estado
    ) throws SQLException {

        try (PreparedStatement ps =
                     cn.prepareStatement("""
                             UPDATE vehiculo
                             SET estado = ?
                             WHERE id_vehiculo = ?
                             """)) {

            ps.setString(1, estado.name());
            ps.setString(2, idVehiculo);

            ps.executeUpdate();
        }
    }

    private VehiculoOperativo mapearVehiculo(
            ResultSet rs
    ) throws SQLException {

        return new VehiculoOperativo(
                rs.getString("id_vehiculo"),
                rs.getString("tipo"),
                rs.getInt("capacidad_paquetes"),
                rs.getDouble("velocidad_kmh"),
                rs.getDouble("costo_por_km"),
                EstadoVehiculo.valueOf(
                        rs.getString("estado")
                ),
                rs.getInt("posicion_x"),
                rs.getInt("posicion_y"),
                rs.getString("id_conductor_actual")
        );
    }

    private AveriaVehiculo mapearAveria(
            ResultSet rs
    ) throws SQLException {

        Timestamp retorno =
                rs.getTimestamp("hora_retorno_estimada");

        return new AveriaVehiculo(
                rs.getLong("id_incidencia"),
                rs.getString("id_vehiculo"),
                TipoAveria.valueOf(
                        rs.getString("tipo_averia")
                ),
                rs.getInt("ubicacion_x"),
                rs.getInt("ubicacion_y"),
                rs.getTimestamp("fecha_ocurrencia")
                        .toLocalDateTime(),
                retorno == null
                        ? null
                        : retorno.toLocalDateTime(),
                rs.getBoolean("activa")
        );
    }

    private MantenimientoVehiculo mapearMantenimiento(
            ResultSet rs
    ) throws SQLException {

        return new MantenimientoVehiculo(
                rs.getLong("id_mantenimiento"),
                rs.getString("id_vehiculo"),
                TipoMantenimiento.valueOf(
                        rs.getString("tipo")
                ),
                rs.getTimestamp("fecha_inicio")
                        .toLocalDateTime(),
                rs.getTimestamp("fecha_fin")
                        .toLocalDateTime(),
                rs.getBoolean("activo")
        );
    }

}