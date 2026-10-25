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
import pe.edu.pucp.sisrap.simulacion.dominio.EscenarioSimulacion;

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
                 ORDER BY fecha_llegada DESC, id_pedido DESC
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
    public List<CargaHistoricaPedidos> listarCargasHistoricas(EscenarioSimulacion escenario) {
        try (Connection cn = fuente.getConnection()) {
            EsquemaHistoricosEscenario.asegurar(cn);
            try (var ps = cn.prepareStatement("""
                    SELECT huella,anio,mes,filas,fecha FROM carga_pedidos_escenario
                    WHERE escenario=? ORDER BY anio DESC,mes DESC,fecha DESC
                    """)) {
                ps.setString(1, escenario.name());
                List<CargaHistoricaPedidos> salida = new ArrayList<>();
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) salida.add(new CargaHistoricaPedidos(rs.getString("huella"),
                            rs.getInt("anio"),rs.getInt("mes"),rs.getInt("filas"),
                            rs.getTimestamp("fecha").toLocalDateTime()));
                }
                return List.copyOf(salida);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron consultar los históricos del escenario", e);
        }
    }

    @Override
    public boolean existeCargaPeriodo(EscenarioSimulacion escenario, int anio, int mes) {
        try (Connection cn = fuente.getConnection()) {
            EsquemaHistoricosEscenario.asegurar(cn);
            return existePeriodo(cn, escenario, anio, mes);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo verificar la carga histórica del escenario", e);
        }
    }

    private boolean existePeriodo(Connection cn, EscenarioSimulacion escenario, int anio, int mes)
            throws SQLException {
        try (var ps = cn.prepareStatement("""
                SELECT 1 FROM carga_pedidos_escenario WHERE escenario=? AND anio=? AND mes=?
                """)) {
            ps.setString(1, escenario.name()); ps.setInt(2, anio); ps.setInt(3, mes);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    @Override
    public void eliminarCargaHistorica(EscenarioSimulacion escenario, String huella) {
        try (Connection cn = fuente.getConnection()) {
            EsquemaHistoricosEscenario.asegurar(cn);
            cn.setAutoCommit(false);
            try {
                EsquemaHistoricosEscenario.bloquear(cn, escenario);
                try (var ps = cn.prepareStatement(
                        "DELETE FROM carga_pedidos_escenario WHERE escenario=? AND huella=?")) {
                    ps.setString(1, escenario.name()); ps.setString(2, huella);
                    if (ps.executeUpdate() != 1)
                        throw new java.util.NoSuchElementException("No existe esa carga en el escenario seleccionado");
                }
                cn.commit();
            } catch (SQLException | RuntimeException e) { cn.rollback(); throw e; }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo eliminar el histórico del escenario", e);
        }
    }

    @Override
    public List<CargaHistoricaPedidos> guardarCargasHistoricas(
            EscenarioSimulacion escenario, List<CargaHistoricaPreparada> cargas) {
        if (cargas == null || cargas.isEmpty())
            throw new IllegalArgumentException("Debe existir al menos una carga para guardar");
        try (Connection cn = fuente.getConnection()) {
            EsquemaHistoricosEscenario.asegurar(cn);
            cn.setAutoCommit(false);
            try {
                EsquemaHistoricosEscenario.bloquear(cn, escenario);
                for (var carga : cargas) {
                    if (existePeriodo(cn, escenario, carga.anio(), carga.mes()))
                        throw new IllegalArgumentException("Ya existen pedidos históricos para "
                                + carga.anio() + "-" + String.format("%02d", carga.mes())
                                + " en este escenario");
                }
                List<CargaHistoricaPedidos> resultado = new ArrayList<>();
                for (var carga : cargas) {
                    try (var ps = cn.prepareStatement("""
                            INSERT INTO carga_pedidos_escenario(escenario,huella,anio,mes,filas)
                            VALUES (?,?,?,?,?)
                            """)) {
                        ps.setString(1, escenario.name()); ps.setString(2, carga.huella());
                        ps.setInt(3, carga.anio()); ps.setInt(4, carga.mes());
                        ps.setInt(5, carga.pedidos().size()); ps.executeUpdate();
                    }
                    try (var ps = cn.prepareStatement("""
                            INSERT INTO pedido_historico_escenario
                                (escenario,huella,id_cliente,cantidad_qq,prioridad,horas_limite,
                                 fecha_llegada,ubicacion_x,ubicacion_y) VALUES (?,?,?,?,?,?,?,?,?)
                            """)) {
                        int lote = 0;
                        for (PedidoHistoricoImportado pedido : carga.pedidos()) {
                            ps.setString(1, escenario.name()); ps.setString(2, carga.huella());
                            ps.setString(3, pedido.cliente()); ps.setInt(4, pedido.cantidad());
                            ps.setString(5, pedido.prioridad().name()); ps.setInt(6, pedido.horasLimite());
                            ps.setTimestamp(7, Timestamp.valueOf(pedido.fechaLlegada()));
                            ps.setInt(8, pedido.x()); ps.setInt(9, pedido.y()); ps.addBatch();
                            if (++lote == 1000) { ps.executeBatch(); ps.clearBatch(); lote = 0; }
                        }
                        if (lote > 0) ps.executeBatch();
                    }
                    try (var ps = cn.prepareStatement(
                            "SELECT fecha FROM carga_pedidos_escenario WHERE escenario=? AND huella=?")) {
                        ps.setString(1, escenario.name()); ps.setString(2, carga.huella());
                        try (var rs = ps.executeQuery()) {
                            if (!rs.next()) throw new SQLException("No se pudo recuperar la carga importada");
                            resultado.add(new CargaHistoricaPedidos(carga.huella(), carga.anio(), carga.mes(),
                                    carga.pedidos().size(), rs.getTimestamp(1).toLocalDateTime()));
                        }
                    }
                }
                cn.commit();
                return List.copyOf(resultado);
            } catch (SQLException | RuntimeException e) { cn.rollback(); throw e; }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron importar los históricos del escenario", e);
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