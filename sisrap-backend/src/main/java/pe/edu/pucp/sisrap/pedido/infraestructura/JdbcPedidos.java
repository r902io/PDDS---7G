package pe.edu.pucp.sisrap.pedido.infraestructura;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import pe.edu.pucp.sisrap.pedido.dominio.CargaHistoricaPedidos;
import pe.edu.pucp.sisrap.pedido.dominio.CargaHistoricaPreparada;
import pe.edu.pucp.sisrap.pedido.dominio.EstadoPedido;
import pe.edu.pucp.sisrap.pedido.dominio.PedidoHistoricoImportado;
import pe.edu.pucp.sisrap.pedido.dominio.PedidoOperativo;
import pe.edu.pucp.sisrap.pedido.dominio.RepositorioPedidos;
import pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad;

@Repository
public class JdbcPedidos implements RepositorioPedidos {

    private final DataSource fuente;

    public JdbcPedidos(DataSource fuente) {
        this.fuente = fuente;
    }

    @Override
    public List<PedidoOperativo> listar(
            LocalDateTime desde,
            LocalDateTime hasta,
            EstadoPedido estado,
            int offset,
            int limite
    ) {

        StringBuilder sql =
                new StringBuilder("""
                        SELECT
                            id_pedido,
                            id_cliente,
                            cantidad_qq,
                            prioridad,
                            horas_limite,
                            fecha_llegada,
                            fecha_entrega_real,
                            estado,
                            ubicacion_x,
                            ubicacion_y
                        FROM pedido
                        WHERE 1 = 1
                        """);

        List<Object> parametros =
                new ArrayList<>();

        agregarFiltros(
                sql,
                parametros,
                desde,
                hasta,
                estado
        );

        sql.append("""
                 ORDER BY fecha_llegada, id_pedido
                 LIMIT ? OFFSET ?
                """);

        parametros.add(limite);
        parametros.add(offset);

        List<PedidoOperativo> salida =
                new ArrayList<>();

        try (
                Connection cn =
                        fuente.getConnection();
                PreparedStatement ps =
                        cn.prepareStatement(
                                sql.toString()
                        )
        ) {

            asignarParametros(
                    ps,
                    parametros
            );

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    salida.add(mapear(rs));
                }
            }

            return List.copyOf(salida);

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "No se pudieron consultar los pedidos",
                    e
            );
        }
    }

    @Override
    public long contar(
            LocalDateTime desde,
            LocalDateTime hasta,
            EstadoPedido estado
    ) {

        StringBuilder sql =
                new StringBuilder("""
                        SELECT COUNT(*)
                        FROM pedido
                        WHERE 1 = 1
                        """);

        List<Object> parametros =
                new ArrayList<>();

        agregarFiltros(
                sql,
                parametros,
                desde,
                hasta,
                estado
        );

        try (
                Connection cn =
                        fuente.getConnection();
                PreparedStatement ps =
                        cn.prepareStatement(
                                sql.toString()
                        )
        ) {

            asignarParametros(
                    ps,
                    parametros
            );

            try (ResultSet rs = ps.executeQuery()) {

                rs.next();

                return rs.getLong(1);
            }

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "No se pudo contar los pedidos",
                    e
            );
        }
    }

    @Override
    public Optional<PedidoOperativo> buscar(
            long idPedido
    ) {

        String sql = """
                SELECT
                    id_pedido,
                    id_cliente,
                    cantidad_qq,
                    prioridad,
                    horas_limite,
                    fecha_llegada,
                    fecha_entrega_real,
                    estado,
                    ubicacion_x,
                    ubicacion_y
                FROM pedido
                WHERE id_pedido = ?
                """;

        try (
                Connection cn =
                        fuente.getConnection();
                PreparedStatement ps =
                        cn.prepareStatement(sql)
        ) {

            ps.setLong(
                    1,
                    idPedido
            );

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                        mapear(rs)
                );
            }

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "No se pudo consultar el pedido",
                    e
            );
        }
    }

    @Override
    public List<CargaHistoricaPedidos>
    listarCargasHistoricas() {

        String sql = """
                SELECT
                    huella,
                    anio,
                    mes,
                    filas,
                    fecha
                FROM carga_pedidos_archivo
                ORDER BY anio DESC,
                         mes DESC,
                         fecha DESC
                """;

        List<CargaHistoricaPedidos> salida =
                new ArrayList<>();

        try (
                Connection cn =
                        fuente.getConnection();
                PreparedStatement ps =
                        cn.prepareStatement(sql);
                ResultSet rs =
                        ps.executeQuery()
        ) {

            while (rs.next()) {

                salida.add(
                        new CargaHistoricaPedidos(
                                rs.getString("huella"),
                                rs.getInt("anio"),
                                rs.getInt("mes"),
                                rs.getInt("filas"),
                                rs.getTimestamp("fecha")
                                        .toLocalDateTime()
                        )
                );
            }

            return List.copyOf(salida);

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "No se pudieron consultar "
                            + "las cargas históricas",
                    e
            );
        }
    }

    @Override
    public boolean existeCargaPeriodo(
            int anio,
            int mes
    ) {

        String sql = """
                SELECT 1
                FROM carga_pedidos_archivo
                WHERE anio = ?
                  AND mes = ?
                LIMIT 1
                """;

        try (
                Connection cn =
                        fuente.getConnection();
                PreparedStatement ps =
                        cn.prepareStatement(sql)
        ) {

            ps.setInt(1, anio);
            ps.setInt(2, mes);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "No se pudo verificar "
                            + "la carga histórica",
                    e
            );
        }
    }

    @Override
    public List<CargaHistoricaPedidos> guardarCargasHistoricas(
            List<CargaHistoricaPreparada> cargas
    ) {

        if (cargas == null || cargas.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debe existir al menos una carga para guardar"
            );
        }

        try (Connection cn = fuente.getConnection()) {

            cn.setAutoCommit(false);

            try {

                /*
                 * Los archivos de ventas son mensuales. Antes de insertar,
                 * bloqueamos/verificamos todos los periodos para evitar que
                 * exista más de un archivo oficial para el mismo mes.
                 */
                for (CargaHistoricaPreparada carga : cargas) {

                    try (PreparedStatement ps =
                                 cn.prepareStatement("""
                                         SELECT huella
                                         FROM carga_pedidos_archivo
                                         WHERE anio = ?
                                           AND mes = ?
                                         LIMIT 1
                                         FOR UPDATE
                                         """)) {

                        ps.setInt(1, carga.anio());
                        ps.setInt(2, carga.mes());

                        try (ResultSet rs = ps.executeQuery()) {

                            if (rs.next()) {
                                throw new IllegalArgumentException(
                                        "Ya existen pedidos históricos "
                                                + "cargados para "
                                                + carga.anio()
                                                + "-"
                                                + String.format(
                                                        "%02d",
                                                        carga.mes()
                                                )
                                );
                            }
                        }
                    }
                }

                String insertarCarga = """
                        INSERT INTO carga_pedidos_archivo(
                            huella,
                            anio,
                            mes,
                            filas
                        )
                        VALUES (?, ?, ?, ?)
                        """;

                String insertarPedido = """
                        INSERT INTO pedido(
                            id_cliente,
                            cantidad_qq,
                            prioridad,
                            horas_limite,
                            fecha_llegada,
                            ubicacion_x,
                            ubicacion_y
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """;

                for (CargaHistoricaPreparada carga : cargas) {

                    try (PreparedStatement ps =
                                 cn.prepareStatement(insertarCarga)) {

                        ps.setString(1, carga.huella());
                        ps.setInt(2, carga.anio());
                        ps.setInt(3, carga.mes());
                        ps.setInt(4, carga.pedidos().size());
                        ps.executeUpdate();
                    }

                    try (PreparedStatement ps =
                                 cn.prepareStatement(insertarPedido)) {

                        for (PedidoHistoricoImportado pedido :
                                carga.pedidos()) {

                            ps.setString(
                                    1,
                                    pedido.cliente()
                            );

                            ps.setInt(
                                    2,
                                    pedido.cantidad()
                            );

                            ps.setString(
                                    3,
                                    pedido.prioridad().name()
                            );

                            ps.setInt(
                                    4,
                                    pedido.horasLimite()
                            );

                            ps.setTimestamp(
                                    5,
                                    Timestamp.valueOf(
                                            pedido.fechaLlegada()
                                    )
                            );

                            ps.setInt(
                                    6,
                                    pedido.x()
                            );

                            ps.setInt(
                                    7,
                                    pedido.y()
                            );

                            ps.addBatch();
                        }

                        ps.executeBatch();
                    }
                }

                List<CargaHistoricaPedidos> resultado =
                        new ArrayList<>();

                for (CargaHistoricaPreparada carga : cargas) {

                    try (PreparedStatement ps =
                                 cn.prepareStatement("""
                                         SELECT fecha
                                         FROM carga_pedidos_archivo
                                         WHERE huella = ?
                                         """)) {

                        ps.setString(1, carga.huella());

                        try (ResultSet rs = ps.executeQuery()) {

                            if (!rs.next()) {
                                throw new IllegalStateException(
                                        "No se pudo recuperar la carga "
                                                + carga.nombreArchivo()
                                );
                            }

                            resultado.add(
                                    new CargaHistoricaPedidos(
                                            carga.huella(),
                                            carga.anio(),
                                            carga.mes(),
                                            carga.pedidos().size(),
                                            rs.getTimestamp("fecha")
                                                    .toLocalDateTime()
                                    )
                            );
                        }
                    }
                }

                cn.commit();
                return List.copyOf(resultado);

            } catch (RuntimeException | SQLException e) {

                cn.rollback();

                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }

                throw new IllegalStateException(
                        "No se pudieron importar los archivos históricos",
                        e
                );
            }

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "No se pudieron importar los archivos históricos",
                    e
            );
        }
    }

    private void agregarFiltros(
            StringBuilder sql,
            List<Object> parametros,
            LocalDateTime desde,
            LocalDateTime hasta,
            EstadoPedido estado
    ) {

        if (desde != null) {

            sql.append(
                    " AND fecha_llegada >= ?"
            );

            parametros.add(desde);
        }

        if (hasta != null) {

            sql.append(
                    " AND fecha_llegada < ?"
            );

            parametros.add(hasta);
        }

        if (estado != null) {

            sql.append(
                    " AND estado = ?"
            );

            parametros.add(
                    estado.name()
            );
        }
    }

    private void asignarParametros(
            PreparedStatement ps,
            List<Object> parametros
    ) throws SQLException {

        for (int i = 0;
             i < parametros.size();
             i++) {

            Object valor =
                    parametros.get(i);

            int indice =
                    i + 1;

            if (valor instanceof LocalDateTime fecha) {

                ps.setTimestamp(
                        indice,
                        Timestamp.valueOf(fecha)
                );

            } else if (valor instanceof Integer numero) {

                ps.setInt(
                        indice,
                        numero
                );

            } else if (valor instanceof String texto) {

                ps.setString(
                        indice,
                        texto
                );

            } else {

                throw new IllegalArgumentException(
                        "Tipo de parámetro SQL no soportado"
                );
            }
        }
    }

    private PedidoOperativo mapear(
            ResultSet rs
    ) throws SQLException {

        Timestamp fechaEntrega =
                rs.getTimestamp(
                        "fecha_entrega_real"
                );

        return new PedidoOperativo(
                rs.getLong("id_pedido"),
                rs.getString("id_cliente"),
                rs.getInt("cantidad_qq"),
                TipoPrioridad.valueOf(
                        rs.getString("prioridad")
                ),
                rs.getInt("horas_limite"),
                rs.getTimestamp("fecha_llegada")
                        .toLocalDateTime(),
                fechaEntrega == null
                        ? null
                        : fechaEntrega.toLocalDateTime(),
                EstadoPedido.valueOf(
                        rs.getString("estado")
                ),
                rs.getInt("ubicacion_x"),
                rs.getInt("ubicacion_y")
        );
    }
}