package pe.edu.pucp.sisrap.mapa.infraestructura;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import pe.edu.pucp.sisrap.mapa.dominio.DimensionMapaInvalidaException;
import pe.edu.pucp.sisrap.mapa.dominio.MapaCiudad;
import pe.edu.pucp.sisrap.mapa.dominio.RepositorioMapa;

@Repository
public class JdbcMapa implements RepositorioMapa {

    private final DataSource fuente;

    public JdbcMapa(DataSource fuente) {
        this.fuente = fuente;
    }

    @Override
    public MapaCiudad obtener() {

        try (Connection cn = fuente.getConnection()) {
            return leerCiudad(cn, false);

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo consultar el mapa",
                    e
            );
        }
    }

    @Override
    public MapaCiudad cambiarDimensiones(
            int ancho,
            int alto
    ) {

        try (Connection cn = fuente.getConnection()) {

            cn.setAutoCommit(false);

            try {

                MapaCiudad actual =
                        leerCiudad(cn, true);

                validarContenido(
                        cn,
                        ancho,
                        alto
                );

                try (PreparedStatement ps =
                             cn.prepareStatement("""
                                     UPDATE ciudad
                                     SET ancho_km = ?,
                                         alto_km = ?
                                     WHERE id_ciudad = ?
                                     """)) {

                    ps.setInt(1, ancho);
                    ps.setInt(2, alto);
                    ps.setInt(
                            3,
                            actual.idCiudad()
                    );

                    ps.executeUpdate();
                }

                cn.commit();

                return new MapaCiudad(
                        actual.idCiudad(),
                        actual.nombre(),
                        ancho,
                        alto
                );

            } catch (RuntimeException | SQLException e) {

                cn.rollback();

                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }

                throw new IllegalStateException(
                        "No se pudo modificar el mapa",
                        e
                );
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo modificar el mapa",
                    e
            );
        }
    }

    private MapaCiudad leerCiudad(
            Connection cn,
            boolean bloquear
    ) throws SQLException {

        String sql = """
                SELECT
                    id_ciudad,
                    nombre,
                    ancho_km,
                    alto_km
                FROM ciudad
                ORDER BY id_ciudad
                """
                + (bloquear ? " FOR UPDATE" : "");

        try (
                PreparedStatement ps =
                        cn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
        ) {

            if (!rs.next()) {
                throw new IllegalStateException(
                        "No existe configuración de ciudad"
                );
            }

            MapaCiudad mapa =
                    new MapaCiudad(
                            rs.getInt("id_ciudad"),
                            rs.getString("nombre"),
                            rs.getInt("ancho_km"),
                            rs.getInt("alto_km")
                    );

            if (rs.next()) {
                throw new IllegalStateException(
                        "El sistema requiere una sola ciudad"
                );
            }

            return mapa;
        }
    }

    private void validarContenido(
            Connection cn,
            int ancho,
            int alto
    ) throws SQLException {

        validarTabla(
                cn,
                """
                SELECT 1
                FROM almacen
                WHERE ubicacion_x < 0
                   OR ubicacion_y < 0
                   OR ubicacion_x > ?
                   OR ubicacion_y > ?
                LIMIT 1
                """,
                ancho,
                alto,
                "Hay almacenes fuera de las nuevas dimensiones"
        );

        validarTabla(
                cn,
                """
                SELECT 1
                FROM vehiculo
                WHERE posicion_x < 0
                   OR posicion_y < 0
                   OR posicion_x > ?
                   OR posicion_y > ?
                LIMIT 1
                """,
                ancho,
                alto,
                "Hay vehículos fuera de las nuevas dimensiones"
        );

        validarTabla(
                cn,
                """
                SELECT 1
                FROM pedido
                WHERE ubicacion_x < 0
                   OR ubicacion_y < 0
                   OR ubicacion_x > ?
                   OR ubicacion_y > ?
                LIMIT 1
                """,
                ancho,
                alto,
                "Hay pedidos fuera de las nuevas dimensiones"
        );

        validarTabla(
                cn,
                """
                SELECT 1
                FROM bloqueo_nodo
                WHERE x < 0
                   OR y < 0
                   OR x > ?
                   OR y > ?
                LIMIT 1
                """,
                ancho,
                alto,
                "Hay bloqueos fuera de las nuevas dimensiones"
        );

        validarTabla(
                cn,
                """
                SELECT 1
                FROM nodo
                WHERE x < 0
                   OR y < 0
                   OR x > ?
                   OR y > ?
                LIMIT 1
                """,
                ancho,
                alto,
                "Hay nodos fuera de las nuevas dimensiones"
        );

        validarVias(
                cn,
                ancho,
                alto
        );
    }

    private void validarTabla(
            Connection cn,
            String sql,
            int ancho,
            int alto,
            String mensaje
    ) throws SQLException {

        try (PreparedStatement ps =
                     cn.prepareStatement(sql)) {

            ps.setInt(1, ancho);
            ps.setInt(2, alto);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    throw new DimensionMapaInvalidaException(
                            mensaje
                    );
                }
            }
        }
    }

    private void validarVias(
            Connection cn,
            int ancho,
            int alto
    ) throws SQLException {

        String sql = """
                SELECT 1
                FROM via
                WHERE origen_x < 0
                   OR origen_y < 0
                   OR destino_x < 0
                   OR destino_y < 0
                   OR origen_x > ?
                   OR origen_y > ?
                   OR destino_x > ?
                   OR destino_y > ?
                LIMIT 1
                """;

        try (PreparedStatement ps =
                     cn.prepareStatement(sql)) {

            ps.setInt(1, ancho);
            ps.setInt(2, alto);
            ps.setInt(3, ancho);
            ps.setInt(4, alto);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    throw new DimensionMapaInvalidaException(
                            "Hay vías fuera de las nuevas dimensiones"
                    );
                }
            }
        }
    }
}