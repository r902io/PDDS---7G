package pe.edu.pucp.sisrap.bloqueo.infraestructura;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import pe.edu.pucp.sisrap.bloqueo.dominio.BloqueoOperativo;
import pe.edu.pucp.sisrap.bloqueo.dominio.RepositorioBloqueos;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

@Repository
public class JdbcBloqueos
        implements RepositorioBloqueos {

    private final DataSource fuente;

    public JdbcBloqueos(DataSource fuente) {
        this.fuente = fuente;
    }

    @Override
    public List<BloqueoOperativo> listar() {

        String sql = """
                SELECT
                    i.id_incidencia,
                    i.activa,
                    b.fecha_inicio,
                    b.fecha_fin
                FROM incidencia i
                INNER JOIN bloqueo b
                    ON b.id_incidencia = i.id_incidencia
                ORDER BY b.fecha_inicio DESC,
                         i.id_incidencia DESC
                """;

        List<BloqueoOperativo> salida =
                new ArrayList<>();

        try (Connection cn = fuente.getConnection();
             PreparedStatement ps =
                     cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                long id =
                        rs.getLong("id_incidencia");

                salida.add(
                        new BloqueoOperativo(
                                id,
                                rs.getTimestamp(
                                                "fecha_inicio"
                                        )
                                        .toLocalDateTime(),
                                rs.getTimestamp(
                                                "fecha_fin"
                                        )
                                        .toLocalDateTime(),
                                cargarVertices(cn, id),
                                rs.getBoolean("activa")
                        )
                );
            }

            return List.copyOf(salida);

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "No se pudieron consultar "
                            + "los bloqueos",
                    e
            );
        }
    }

    @Override
    public Optional<BloqueoOperativo> buscar(
            long idIncidencia
    ) {

        String sql = """
                SELECT
                    i.id_incidencia,
                    i.activa,
                    b.fecha_inicio,
                    b.fecha_fin
                FROM incidencia i
                INNER JOIN bloqueo b
                    ON b.id_incidencia = i.id_incidencia
                WHERE i.id_incidencia = ?
                """;

        try (Connection cn = fuente.getConnection();
             PreparedStatement ps =
                     cn.prepareStatement(sql)) {

            ps.setLong(1, idIncidencia);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                        new BloqueoOperativo(
                                idIncidencia,
                                rs.getTimestamp(
                                                "fecha_inicio"
                                        )
                                        .toLocalDateTime(),
                                rs.getTimestamp(
                                                "fecha_fin"
                                        )
                                        .toLocalDateTime(),
                                cargarVertices(
                                        cn,
                                        idIncidencia
                                ),
                                rs.getBoolean("activa")
                        )
                );
            }

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "No se pudo consultar el bloqueo",
                    e
            );
        }
    }

    @Override
    public BloqueoOperativo crear(
            LocalDateTime inicio,
            LocalDateTime fin,
            List<Nodo> vertices
    ) {

        /*
         * Construirlo primero ejecuta las
         * validaciones del dominio.
         */
        new BloqueoOperativo(
                0,
                inicio,
                fin,
                vertices,
                true
        );

        try (Connection cn = fuente.getConnection()) {

            cn.setAutoCommit(false);

            try {

                validarCoordenadas(
                        cn,
                        vertices
                );

                long idIncidencia =
                        insertarIncidencia(
                                cn,
                                inicio
                        );

                insertarBloqueo(
                        cn,
                        idIncidencia,
                        inicio,
                        fin
                );

                insertarVertices(
                        cn,
                        idIncidencia,
                        vertices
                );

                cn.commit();

                return new BloqueoOperativo(
                        idIncidencia,
                        inicio,
                        fin,
                        vertices,
                        true
                );

            } catch (RuntimeException | SQLException e) {

                cn.rollback();

                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }

                throw new IllegalStateException(
                        "No se pudo crear el bloqueo",
                        e
                );
            }

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "No se pudo crear el bloqueo",
                    e
            );
        }
    }

    @Override
    public boolean cancelar(
            long idIncidencia
    ) {

        String buscar = """
                SELECT i.activa
                FROM incidencia i
                INNER JOIN bloqueo b
                    ON b.id_incidencia = i.id_incidencia
                WHERE i.id_incidencia = ?
                FOR UPDATE
                """;

        try (Connection cn = fuente.getConnection()) {

            cn.setAutoCommit(false);

            try {

                boolean existe;
                boolean activa = false;

                try (PreparedStatement ps =
                             cn.prepareStatement(buscar)) {

                    ps.setLong(1, idIncidencia);

                    try (ResultSet rs =
                                 ps.executeQuery()) {

                        existe = rs.next();

                        if (existe) {
                            activa =
                                    rs.getBoolean("activa");
                        }
                    }
                }

                if (!existe) {
                    cn.rollback();
                    return false;
                }

                if (activa) {

                    try (PreparedStatement ps =
                                 cn.prepareStatement("""
                                         UPDATE incidencia
                                         SET activa = FALSE
                                         WHERE id_incidencia = ?
                                         """)) {

                        ps.setLong(
                                1,
                                idIncidencia
                        );

                        ps.executeUpdate();
                    }
                }

                cn.commit();

                return true;

            } catch (SQLException e) {

                cn.rollback();

                throw e;
            }

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "No se pudo cancelar el bloqueo",
                    e
            );
        }
    }

    private long insertarIncidencia(
            Connection cn,
            LocalDateTime inicio
    ) throws SQLException {

        String sql = """
                INSERT INTO incidencia(
                    tipo,
                    fecha_ocurrencia,
                    activa
                )
                VALUES ('BLOQUEO', ?, TRUE)
                """;

        try (PreparedStatement ps =
                     cn.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS
                     )) {

            ps.setTimestamp(
                    1,
                    Timestamp.valueOf(inicio)
            );

            ps.executeUpdate();

            try (ResultSet rs =
                         ps.getGeneratedKeys()) {

                if (!rs.next()) {
                    throw new SQLException(
                            "No se generó id "
                                    + "de incidencia"
                    );
                }

                return rs.getLong(1);
            }
        }
    }

    private void insertarBloqueo(
            Connection cn,
            long id,
            LocalDateTime inicio,
            LocalDateTime fin
    ) throws SQLException {

        String sql = """
                INSERT INTO bloqueo(
                    id_incidencia,
                    fecha_inicio,
                    fecha_fin
                )
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement ps =
                     cn.prepareStatement(sql)) {

            ps.setLong(1, id);
            ps.setTimestamp(
                    2,
                    Timestamp.valueOf(inicio)
            );
            ps.setTimestamp(
                    3,
                    Timestamp.valueOf(fin)
            );

            ps.executeUpdate();
        }
    }

    private void insertarVertices(
            Connection cn,
            long idIncidencia,
            List<Nodo> vertices
    ) throws SQLException {

        String sql = """
                INSERT INTO bloqueo_nodo(
                    id_incidencia,
                    orden,
                    x,
                    y
                )
                VALUES (?, ?, ?, ?)
                """;

        try (PreparedStatement ps =
                     cn.prepareStatement(sql)) {

            for (int i = 0;
                 i < vertices.size();
                 i++) {

                Nodo nodo = vertices.get(i);

                ps.setLong(1, idIncidencia);
                ps.setInt(2, i);
                ps.setInt(3, nodo.getX());
                ps.setInt(4, nodo.getY());

                ps.addBatch();
            }

            ps.executeBatch();
        }
    }

    private List<Nodo> cargarVertices(
            Connection cn,
            long idIncidencia
    ) throws SQLException {

        String sql = """
                SELECT x, y
                FROM bloqueo_nodo
                WHERE id_incidencia = ?
                ORDER BY orden
                """;

        List<Nodo> vertices =
                new ArrayList<>();

        try (PreparedStatement ps =
                     cn.prepareStatement(sql)) {

            ps.setLong(1, idIncidencia);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    vertices.add(
                            new Nodo(
                                    rs.getInt("x"),
                                    rs.getInt("y")
                            )
                    );
                }
            }
        }

        return List.copyOf(vertices);
    }

    private void validarCoordenadas(
            Connection cn,
            List<Nodo> vertices
    ) throws SQLException {

        int ancho;
        int alto;

        try (PreparedStatement ps =
                     cn.prepareStatement("""
                             SELECT ancho_km, alto_km
                             FROM ciudad
                             ORDER BY id_ciudad
                             LIMIT 1
                             """);
             ResultSet rs = ps.executeQuery()) {

            if (!rs.next()) {
                throw new IllegalStateException(
                        "No existe configuración "
                                + "de ciudad"
                );
            }

            ancho = rs.getInt("ancho_km");
            alto = rs.getInt("alto_km");
        }

        for (Nodo nodo : vertices) {

            if (nodo.getX() < 0
                    || nodo.getX() > ancho
                    || nodo.getY() < 0
                    || nodo.getY() > alto) {

                throw new IllegalArgumentException(
                        "El punto ("
                                + nodo.getX()
                                + ","
                                + nodo.getY()
                                + ") está fuera de la ciudad"
                );
            }
        }
    }
}