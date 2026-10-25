package pe.edu.pucp.sisrap.pedido.infraestructura;

import java.sql.Connection;
import java.sql.SQLException;
import pe.edu.pucp.sisrap.simulacion.dominio.EscenarioSimulacion;
import pe.edu.pucp.sisrap.simulacion.infraestructura.EsquemaDatosSimulacion;

/** Históricos de cada escenario, separados de la operación y de la experimentación. */
public final class EsquemaHistoricosEscenario {
    private static boolean inicializado;

    private EsquemaHistoricosEscenario() { }

    public static synchronized void asegurar(Connection cn) throws SQLException {
        if (inicializado) return;
        if (!cn.getAutoCommit())
            throw new IllegalStateException("Los históricos deben prepararse antes de iniciar una transacción");
        EsquemaDatosSimulacion.asegurar(cn);
        try (var st = cn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS historico_escenario (
                    escenario VARCHAR(40) PRIMARY KEY,
                    inicializado BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS carga_pedidos_escenario (
                    escenario VARCHAR(40) NOT NULL,
                    huella CHAR(64) NOT NULL,
                    anio INT NOT NULL, mes INT NOT NULL, filas INT NOT NULL,
                    fecha DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (escenario,huella),
                    UNIQUE KEY uk_carga_escenario_periodo (escenario,anio,mes)
                )
                """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pedido_historico_escenario (
                    id_historico BIGINT AUTO_INCREMENT PRIMARY KEY,
                    escenario VARCHAR(40) NOT NULL, huella CHAR(64) NOT NULL,
                    id_cliente VARCHAR(20), cantidad_qq INT NOT NULL,
                    prioridad VARCHAR(20) NOT NULL, horas_limite INT NOT NULL,
                    fecha_llegada DATETIME(6) NOT NULL,
                    ubicacion_x INT NOT NULL, ubicacion_y INT NOT NULL,
                    INDEX idx_historico_escenario_fecha (escenario,fecha_llegada),
                    CONSTRAINT fk_historico_carga_escenario FOREIGN KEY (escenario,huella)
                        REFERENCES carga_pedidos_escenario(escenario,huella) ON DELETE CASCADE
                )
                """);
        }

        cn.setAutoCommit(false);
        try {
            for (var escenario : EscenarioSimulacion.values()) {
                try (var ps = cn.prepareStatement(
                        "INSERT IGNORE INTO historico_escenario(escenario) VALUES (?)")) {
                    ps.setString(1, escenario.name()); ps.executeUpdate();
                }
                boolean migrado;
                try (var ps = cn.prepareStatement(
                        "SELECT inicializado FROM historico_escenario WHERE escenario=? FOR UPDATE")) {
                    ps.setString(1, escenario.name());
                    try (var rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("No se pudo preparar el histórico del escenario");
                        migrado = rs.getBoolean(1);
                    }
                }
                if (migrado) continue;
                try (var ps = cn.prepareStatement("""
                    INSERT INTO carga_pedidos_escenario(escenario,huella,anio,mes,filas,fecha)
                    SELECT ?,huella,anio,mes,filas,fecha FROM carga_pedidos_archivo
                    """)) {
                    ps.setString(1, escenario.name()); ps.executeUpdate();
                }
                try (var ps = cn.prepareStatement("""
                    INSERT INTO pedido_historico_escenario
                        (escenario,huella,id_cliente,cantidad_qq,prioridad,horas_limite,
                         fecha_llegada,ubicacion_x,ubicacion_y)
                    SELECT ?,c.huella,p.id_cliente,p.cantidad_qq,p.prioridad,p.horas_limite,
                           p.fecha_llegada,p.ubicacion_x,p.ubicacion_y
                    FROM pedido_historico p JOIN carga_pedidos_archivo c
                      ON YEAR(p.fecha_llegada)=c.anio AND MONTH(p.fecha_llegada)=c.mes
                    """)) {
                    ps.setString(1, escenario.name()); ps.executeUpdate();
                }
                try (var ps = cn.prepareStatement(
                        "UPDATE historico_escenario SET inicializado=TRUE WHERE escenario=?")) {
                    ps.setString(1, escenario.name()); ps.executeUpdate();
                }
            }
            cn.commit();
            inicializado = true;
        } catch (SQLException | RuntimeException e) {
            cn.rollback(); throw e;
        } finally {
            cn.setAutoCommit(true);
        }
    }

    public static void bloquear(Connection cn, EscenarioSimulacion escenario) throws SQLException {
        try (var ps = cn.prepareStatement(
                "SELECT escenario FROM historico_escenario WHERE escenario=? FOR UPDATE")) {
            ps.setString(1, escenario.name());
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("No existe el histórico del escenario seleccionado");
            }
        }
    }
}
