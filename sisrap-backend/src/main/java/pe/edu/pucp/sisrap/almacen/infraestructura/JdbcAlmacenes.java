package pe.edu.pucp.sisrap.almacen.infraestructura;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import pe.edu.pucp.sisrap.almacen.dominio.AlmacenOperativo;
import pe.edu.pucp.sisrap.almacen.dominio.ConflictoAlmacenException;
import pe.edu.pucp.sisrap.almacen.dominio.RepositorioAlmacenes;
import pe.edu.pucp.sisrap.almacen.dominio.TipoAlmacen;

@Repository
public class JdbcAlmacenes implements RepositorioAlmacenes {

    private final DataSource fuente;

    public JdbcAlmacenes(DataSource fuente) {
        this.fuente = fuente;
    }

    @Override
    public List<AlmacenOperativo> listar() {

        String sql = """
                SELECT
                    id_almacen,
                    nombre,
                    tipo,
                    ubicacion_x,
                    ubicacion_y,
                    capacidad_maxima,
                    stock_actual,
                    hora_recarga
                FROM almacen
                ORDER BY
                    CASE WHEN tipo = 'CENTRAL' THEN 0 ELSE 1 END,
                    id_almacen
                """;

        List<AlmacenOperativo> salida = new ArrayList<>();

        try (
                Connection cn = fuente.getConnection();
                PreparedStatement ps = cn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
        ) {

            while (rs.next()) {
                salida.add(mapear(rs));
            }

            return List.copyOf(salida);

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudieron consultar los almacenes",
                    e
            );
        }
    }

    @Override
    public Optional<AlmacenOperativo> buscar(
            String idAlmacen
    ) {

        String sql = """
                SELECT
                    id_almacen,
                    nombre,
                    tipo,
                    ubicacion_x,
                    ubicacion_y,
                    capacidad_maxima,
                    stock_actual,
                    hora_recarga
                FROM almacen
                WHERE id_almacen = ?
                """;

        try (
                Connection cn = fuente.getConnection();
                PreparedStatement ps = cn.prepareStatement(sql)
        ) {

            ps.setString(1, idAlmacen);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapear(rs));
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo consultar el almacén",
                    e
            );
        }
    }

    @Override
    public AlmacenOperativo crear(
            String idAlmacen,
            String nombre,
            TipoAlmacen tipo,
            int x,
            int y,
            Integer capacidad
    ) {

        try (Connection cn = fuente.getConnection()) {

            cn.setAutoCommit(false);

            try {

                validarCoordenadas(cn, x, y);

                if (tipo == TipoAlmacen.CENTRAL) {
                    validarNoExisteCentral(cn);
                }

                Integer stock =
                        tipo == TipoAlmacen.INTERMEDIO
                                ? capacidad
                                : null;

                LocalTime horaRecarga =
                        tipo == TipoAlmacen.INTERMEDIO
                                ? LocalTime.of(23, 59, 59)
                                : null;

                String sql = """
                        INSERT INTO almacen(
                            id_almacen,
                            nombre,
                            tipo,
                            ubicacion_x,
                            ubicacion_y,
                            capacidad_maxima,
                            stock_actual,
                            hora_recarga
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """;

                try (PreparedStatement ps =
                             cn.prepareStatement(sql)) {

                    ps.setString(1, idAlmacen);
                    ps.setString(2, nombre);
                    ps.setString(3, tipo.name());
                    ps.setInt(4, x);
                    ps.setInt(5, y);

                    if (capacidad == null) {
                        ps.setNull(
                                6,
                                java.sql.Types.INTEGER
                        );
                    } else {
                        ps.setInt(6, capacidad);
                    }

                    if (stock == null) {
                        ps.setNull(
                                7,
                                java.sql.Types.INTEGER
                        );
                    } else {
                        ps.setInt(7, stock);
                    }

                    if (horaRecarga == null) {
                        ps.setNull(
                                8,
                                java.sql.Types.TIME
                        );
                    } else {
                        ps.setTime(
                                8,
                                Time.valueOf(horaRecarga)
                        );
                    }

                    ps.executeUpdate();
                }

                cn.commit();

                return new AlmacenOperativo(
                        idAlmacen,
                        nombre,
                        tipo,
                        x,
                        y,
                        capacidad,
                        stock,
                        horaRecarga
                );

            } catch (RuntimeException | SQLException e) {

                cn.rollback();

                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }

                if (e instanceof SQLException sql
                        && sql.getErrorCode() == 1062) {

                    throw new ConflictoAlmacenException(
                            "Ya existe un almacén con ese identificador"
                    );
                }

                throw new IllegalStateException(
                        "No se pudo crear el almacén",
                        e
                );
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo crear el almacén",
                    e
            );
        }
    }

    @Override
    public AlmacenOperativo actualizar(
            String idAlmacen,
            String nombre,
            int x,
            int y,
            Integer capacidad
    ) {

        try (Connection cn = fuente.getConnection()) {

            cn.setAutoCommit(false);

            try {

                AlmacenOperativo actual =
                        buscarParaActualizar(
                                cn,
                                idAlmacen
                        );

                validarCoordenadas(cn, x, y);

                if (actual.tipo() == TipoAlmacen.CENTRAL) {

                    if (capacidad != null) {
                        throw new IllegalArgumentException(
                                "El almacén central tiene capacidad ilimitada"
                        );
                    }

                } else {

                    if (capacidad == null
                            || capacidad <= 0) {

                        throw new IllegalArgumentException(
                                "La capacidad del almacén intermedio "
                                        + "debe ser mayor que cero"
                        );
                    }

                    if (actual.stockActual() != null
                            && actual.stockActual() > capacidad) {

                        throw new ConflictoAlmacenException(
                                "La nueva capacidad es menor "
                                        + "que el stock actual"
                        );
                    }
                }

                String sql = """
                        UPDATE almacen
                        SET
                            nombre = ?,
                            ubicacion_x = ?,
                            ubicacion_y = ?,
                            capacidad_maxima = ?
                        WHERE id_almacen = ?
                        """;

                try (PreparedStatement ps =
                             cn.prepareStatement(sql)) {

                    ps.setString(1, nombre);
                    ps.setInt(2, x);
                    ps.setInt(3, y);

                    if (capacidad == null) {
                        ps.setNull(
                                4,
                                java.sql.Types.INTEGER
                        );
                    } else {
                        ps.setInt(4, capacidad);
                    }

                    ps.setString(5, idAlmacen);

                    ps.executeUpdate();
                }

                /*
                 * Si movemos el almacén central,
                 * también movemos los vehículos disponibles
                 * que estaban físicamente en la posición
                 * anterior del central.
                 */
                if (actual.tipo() == TipoAlmacen.CENTRAL
                        && (actual.ubicacionX() != x
                        || actual.ubicacionY() != y)) {

                    try (PreparedStatement ps =
                                 cn.prepareStatement("""
                                         UPDATE vehiculo
                                         SET posicion_x = ?,
                                             posicion_y = ?
                                         WHERE estado = 'DISPONIBLE'
                                           AND posicion_x = ?
                                           AND posicion_y = ?
                                         """)) {

                        ps.setInt(1, x);
                        ps.setInt(2, y);
                        ps.setInt(
                                3,
                                actual.ubicacionX()
                        );
                        ps.setInt(
                                4,
                                actual.ubicacionY()
                        );

                        ps.executeUpdate();
                    }
                }

                cn.commit();

                return new AlmacenOperativo(
                        actual.idAlmacen(),
                        nombre,
                        actual.tipo(),
                        x,
                        y,
                        capacidad,
                        actual.stockActual(),
                        actual.horaRecarga()
                );

            } catch (RuntimeException | SQLException e) {

                cn.rollback();

                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }

                throw new IllegalStateException(
                        "No se pudo actualizar el almacén",
                        e
                );
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo actualizar el almacén",
                    e
            );
        }
    }

    @Override
    public void eliminar(
            String idAlmacen
    ) {

        try (Connection cn = fuente.getConnection()) {

            cn.setAutoCommit(false);

            try {

                AlmacenOperativo almacen =
                        buscarParaActualizar(
                                cn,
                                idAlmacen
                        );

                if (almacen.tipo() == TipoAlmacen.CENTRAL) {

                    throw new ConflictoAlmacenException(
                            "El almacén central no puede eliminarse. "
                                    + "Puede modificar su ubicación."
                    );
                }

                try (PreparedStatement ps =
                             cn.prepareStatement("""
                                     DELETE FROM almacen
                                     WHERE id_almacen = ?
                                     """)) {

                    ps.setString(1, idAlmacen);
                    ps.executeUpdate();
                }

                cn.commit();

            } catch (RuntimeException e) {

                cn.rollback();
                throw e;

            } catch (SQLException e) {

                cn.rollback();

                if ("23000".equals(e.getSQLState())) {
                    throw new ConflictoAlmacenException(
                            "El almacén está siendo utilizado "
                                    + "por información histórica del sistema"
                    );
                }

                throw new IllegalStateException(
                        "No se pudo eliminar el almacén",
                        e
                );
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo eliminar el almacén",
                    e
            );
        }
    }

    private AlmacenOperativo buscarParaActualizar(
            Connection cn,
            String idAlmacen
    ) throws SQLException {

        String sql = """
                SELECT
                    id_almacen,
                    nombre,
                    tipo,
                    ubicacion_x,
                    ubicacion_y,
                    capacidad_maxima,
                    stock_actual,
                    hora_recarga
                FROM almacen
                WHERE id_almacen = ?
                FOR UPDATE
                """;

        try (PreparedStatement ps =
                     cn.prepareStatement(sql)) {

            ps.setString(1, idAlmacen);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    throw new NoSuchElementException(
                            "No existe el almacén "
                                    + idAlmacen
                    );
                }

                return mapear(rs);
            }
        }
    }

    private void validarCoordenadas(
            Connection cn,
            int x,
            int y
    ) throws SQLException {

        String sql = """
                SELECT ancho_km, alto_km
                FROM ciudad
                ORDER BY id_ciudad
                """;

        try (
                PreparedStatement ps =
                        cn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
        ) {

            if (!rs.next()) {
                throw new IllegalStateException(
                        "No existe configuración de mapa"
                );
            }

            int ancho = rs.getInt("ancho_km");
            int alto = rs.getInt("alto_km");

            if (rs.next()) {
                throw new IllegalStateException(
                        "Debe existir una sola ciudad"
                );
            }

            if (x < 0
                    || x > ancho
                    || y < 0
                    || y > alto) {

                throw new IllegalArgumentException(
                        "La ubicación está fuera del mapa"
                );
            }
        }
    }

    private void validarNoExisteCentral(
            Connection cn
    ) throws SQLException {

        try (
                PreparedStatement ps =
                        cn.prepareStatement("""
                                SELECT id_almacen
                                FROM almacen
                                WHERE tipo = 'CENTRAL'
                                FOR UPDATE
                                """);
                ResultSet rs = ps.executeQuery()
        ) {

            if (rs.next()) {
                throw new ConflictoAlmacenException(
                        "Ya existe un almacén central"
                );
            }
        }
    }

    private AlmacenOperativo mapear(
            ResultSet rs
    ) throws SQLException {

        Integer capacidad =
                (Integer) rs.getObject(
                        "capacidad_maxima"
                );

        Integer stock =
                (Integer) rs.getObject(
                        "stock_actual"
                );

        Time hora =
                rs.getTime("hora_recarga");

        return new AlmacenOperativo(
                rs.getString("id_almacen"),
                rs.getString("nombre"),
                TipoAlmacen.valueOf(
                        rs.getString("tipo")
                ),
                rs.getInt("ubicacion_x"),
                rs.getInt("ubicacion_y"),
                capacidad,
                stock,
                hora == null
                        ? null
                        : hora.toLocalTime()
        );
    }
}