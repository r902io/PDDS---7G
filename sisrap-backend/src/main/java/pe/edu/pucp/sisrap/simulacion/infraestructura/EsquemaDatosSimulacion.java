package pe.edu.pucp.sisrap.simulacion.infraestructura;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Separación física de insumos históricos, operación vigente y resultados por corrida.
 */
public final class EsquemaDatosSimulacion {
    private static boolean inicializado;

    private EsquemaDatosSimulacion() { }

    public static synchronized void asegurar(Connection cn) throws SQLException {
        if (inicializado) return;
        try (Statement st = cn.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pedido_historico (
                        id_historico BIGINT AUTO_INCREMENT PRIMARY KEY,
                        id_cliente VARCHAR(20), cantidad_qq INT NOT NULL,
                        prioridad VARCHAR(20) NOT NULL, horas_limite INT NOT NULL,
                        fecha_llegada DATETIME(6) NOT NULL, ubicacion_x INT NOT NULL,
                        ubicacion_y INT NOT NULL,
                        INDEX idx_historico_fecha (fecha_llegada)
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pedido_arribo (
                        id_pedido BIGINT NOT NULL PRIMARY KEY,
                        fecha_arribo DATETIME(6) NOT NULL,
                        CONSTRAINT fk_pedido_arribo FOREIGN KEY(id_pedido)
                            REFERENCES pedido(id_pedido) ON DELETE CASCADE
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS simulacion_pedido (
                        id_simulacion BIGINT NOT NULL, id_pedido BIGINT NOT NULL,
                        id_cliente VARCHAR(20), cantidad_qq INT NOT NULL,
                        prioridad VARCHAR(20) NOT NULL, horas_limite INT NOT NULL,
                        fecha_llegada DATETIME(6) NOT NULL,
                        fecha_entrega_real DATETIME(6), fecha_arribo DATETIME(6),
                        estado VARCHAR(20) NOT NULL,
                        ubicacion_x INT NOT NULL, ubicacion_y INT NOT NULL,
                        PRIMARY KEY (id_simulacion,id_pedido)
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS simulacion_colapso (
                        id_simulacion BIGINT NOT NULL PRIMARY KEY,
                        id_pedido BIGINT NOT NULL,
                        instante DATETIME(6) NOT NULL
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS simulacion_fraccion (
                        id_simulacion BIGINT NOT NULL, id_pedido BIGINT NOT NULL,
                        id_fraccion BIGINT NOT NULL, cantidad_qq INT NOT NULL,
                        id_vehiculo VARCHAR(20), estado VARCHAR(20) NOT NULL,
                        fecha_arribo DATETIME(6), fecha_entrega DATETIME(6),
                        PRIMARY KEY(id_simulacion,id_fraccion)
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS simulacion_incidencia (
                        id_simulacion BIGINT NOT NULL, id_incidencia BIGINT NOT NULL,
                        tipo VARCHAR(20) NOT NULL, fecha_ocurrencia DATETIME(6) NOT NULL,
                        activa BOOLEAN NOT NULL,
                        fecha_inicio DATETIME(6), fecha_fin DATETIME(6),
                        id_vehiculo VARCHAR(20), tipo_averia VARCHAR(20),
                        ubicacion_x INT, ubicacion_y INT, hora_retorno_estimada DATETIME(6),
                        PRIMARY KEY (id_simulacion,id_incidencia)
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS simulacion_bloqueo_nodo (
                        id_simulacion BIGINT NOT NULL, id_incidencia BIGINT NOT NULL,
                        orden INT NOT NULL, x INT NOT NULL, y INT NOT NULL,
                        PRIMARY KEY (id_simulacion,id_incidencia,orden)
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS simulacion_mantenimiento (
                        id_simulacion BIGINT NOT NULL, id_mantenimiento BIGINT NOT NULL,
                        id_vehiculo VARCHAR(20) NOT NULL, tipo VARCHAR(20) NOT NULL,
                        fecha_inicio DATETIME(6) NOT NULL, fecha_fin DATETIME(6) NOT NULL,
                        activo BOOLEAN NOT NULL,
                        PRIMARY KEY (id_simulacion,id_mantenimiento)
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS simulacion_ruta_pedido (
                        id_simulacion BIGINT NOT NULL, id_ruta_pedido BIGINT NOT NULL,
                        id_ruta BIGINT NOT NULL, id_pedido BIGINT NOT NULL,
                        orden_visita INT NOT NULL, hora_entrega_estimada DATETIME(6),
                        PRIMARY KEY (id_simulacion,id_ruta_pedido)
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS simulacion_ruta (
                        id_simulacion BIGINT NOT NULL,
                        id_ruta BIGINT NOT NULL, id_solucion BIGINT NOT NULL,
                        id_vehiculo VARCHAR(20) NOT NULL,
                        id_almacen_origen VARCHAR(20) NOT NULL,
                        distancia_total_km DECIMAL(8,2) NOT NULL,
                        costo_total DECIMAL(10,2) NOT NULL,
                        hora_inicio DATETIME(6),
                        PRIMARY KEY (id_simulacion, id_ruta)
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS simulacion_vehiculo (
                        id_simulacion BIGINT NOT NULL,
                        id_vehiculo VARCHAR(20) NOT NULL,
                        tipo VARCHAR(20) NOT NULL,
                        capacidad_paquetes INT NOT NULL,
                        velocidad_kmh DECIMAL(5,2) NOT NULL,
                        costo_por_km DECIMAL(6,2) NOT NULL,
                        PRIMARY KEY (id_simulacion,id_vehiculo)
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS simulacion_almacen (
                        id_simulacion BIGINT NOT NULL,
                        id_almacen VARCHAR(20) NOT NULL,
                        nombre VARCHAR(50) NOT NULL,
                        tipo VARCHAR(20) NOT NULL,
                        ubicacion_x INT NOT NULL, ubicacion_y INT NOT NULL,
                        capacidad_maxima INT,
                        PRIMARY KEY (id_simulacion,id_almacen)
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sisrap_migracion_datos (
                        nombre VARCHAR(100) PRIMARY KEY, aplicado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
        // La antigua versión guardaba el histórico en pedido. Se migra una sola vez,
        // únicamente para meses respaldados por carga_pedidos_archivo.
        boolean migrado;
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT 1 FROM sisrap_migracion_datos WHERE nombre='pedido_historico_v1'" );
             ResultSet rs = ps.executeQuery()) {
            migrado = rs.next();
        }
        if (!migrado) {
            boolean oldAutoCommit = cn.getAutoCommit();
            cn.setAutoCommit(false);
            try (Statement st = cn.createStatement()) {
                st.executeUpdate("""
                        INSERT INTO pedido_historico
                            (id_cliente,cantidad_qq,prioridad,horas_limite,fecha_llegada,ubicacion_x,ubicacion_y)
                        SELECT p.id_cliente,p.cantidad_qq,p.prioridad,p.horas_limite,
                               p.fecha_llegada,p.ubicacion_x,p.ubicacion_y
                        FROM pedido p
                        WHERE EXISTS (
                          SELECT 1 FROM carga_pedidos_archivo c
                          WHERE YEAR(p.fecha_llegada)=c.anio AND MONTH(p.fecha_llegada)=c.mes
                        )
                        """);
                st.executeUpdate("INSERT INTO sisrap_migracion_datos(nombre) VALUES ('pedido_historico_v1')");
                cn.commit();
            } catch (SQLException e) {
                cn.rollback();
                throw e;
            } finally {
                cn.setAutoCommit(oldAutoCommit);
            }
        }
        inicializado = true;
    }

    /** Captura el estado FINAL de cada corrida, incluyendo incidencias canceladas. Idempotente. */
    public static void archivar(Connection cn, long id) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("""
                INSERT INTO simulacion_pedido
                    (id_simulacion,id_pedido,id_cliente,cantidad_qq,prioridad,horas_limite,
                     fecha_llegada,fecha_entrega_real,fecha_arribo,estado,ubicacion_x,ubicacion_y)
                SELECT ?,p.id_pedido,p.id_cliente,p.cantidad_qq,p.prioridad,p.horas_limite,
                       p.fecha_llegada,p.fecha_entrega_real,a.fecha_arribo,p.estado,p.ubicacion_x,p.ubicacion_y
                FROM pedido p LEFT JOIN pedido_arribo a ON a.id_pedido=p.id_pedido WHERE 1=1
                ON DUPLICATE KEY UPDATE
                    fecha_entrega_real=VALUES(fecha_entrega_real),
                    fecha_arribo=VALUES(fecha_arribo),estado=VALUES(estado)
                """)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = cn.prepareStatement("""
                INSERT INTO simulacion_incidencia
                    (id_simulacion,id_incidencia,tipo,fecha_ocurrencia,activa,fecha_inicio,fecha_fin,
                     id_vehiculo,tipo_averia,ubicacion_x,ubicacion_y,hora_retorno_estimada)
                SELECT ?,i.id_incidencia,i.tipo,i.fecha_ocurrencia,i.activa,b.fecha_inicio,b.fecha_fin,
                       a.id_vehiculo,a.tipo_averia,a.ubicacion_x,a.ubicacion_y,a.hora_retorno_estimada
                FROM incidencia i
                LEFT JOIN bloqueo b ON b.id_incidencia=i.id_incidencia
                LEFT JOIN averia a ON a.id_incidencia=i.id_incidencia
                WHERE 1=1
                ON DUPLICATE KEY UPDATE activa=VALUES(activa),hora_retorno_estimada=VALUES(hora_retorno_estimada)
                """)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = cn.prepareStatement("""
                INSERT IGNORE INTO simulacion_bloqueo_nodo
                    (id_simulacion,id_incidencia,orden,x,y)
                SELECT ?,id_incidencia,orden,x,y FROM bloqueo_nodo
                """)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = cn.prepareStatement("""
                INSERT INTO simulacion_mantenimiento
                    (id_simulacion,id_mantenimiento,id_vehiculo,tipo,fecha_inicio,fecha_fin,activo)
                SELECT ?,id_mantenimiento,id_vehiculo,tipo,fecha_inicio,fecha_fin,activo
                FROM mantenimiento_vehiculo WHERE 1=1
                ON DUPLICATE KEY UPDATE activo=VALUES(activo)
                """)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = cn.prepareStatement("""
                INSERT IGNORE INTO simulacion_ruta
                    (id_simulacion,id_ruta,id_solucion,id_vehiculo,id_almacen_origen,
                     distancia_total_km,costo_total,hora_inicio)
                SELECT s.id_simulacion,r.id_ruta,r.id_solucion,r.id_vehiculo,r.id_almacen_origen,
                       r.distancia_total_km,r.costo_total,r.hora_inicio
                FROM ruta r JOIN solucion s ON s.id_solucion=r.id_solucion
                WHERE s.id_simulacion=?
                """)) {
            ps.setLong(1,id);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = cn.prepareStatement("""
                INSERT IGNORE INTO simulacion_ruta_pedido
                    (id_simulacion,id_ruta_pedido,id_ruta,id_pedido,orden_visita,hora_entrega_estimada)
                SELECT s.id_simulacion,rp.id_ruta_pedido,rp.id_ruta,rp.id_pedido,
                       rp.orden_visita,rp.hora_entrega_estimada
                FROM ruta_pedido rp
                JOIN ruta r ON r.id_ruta=rp.id_ruta
                JOIN solucion s ON s.id_solucion=r.id_solucion
                WHERE s.id_simulacion=?
                """)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    /** Foto inmutable de los almacenes y la flota usados por una corrida. */
    public static void registrarRecursos(Connection cn, long id) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("""
                INSERT INTO simulacion_vehiculo
                    (id_simulacion,id_vehiculo,tipo,capacidad_paquetes,velocidad_kmh,costo_por_km)
                SELECT ?,id_vehiculo,tipo,capacidad_paquetes,velocidad_kmh,costo_por_km
                FROM vehiculo
                """)) {
            ps.setLong(1,id);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = cn.prepareStatement("""
                INSERT INTO simulacion_almacen
                    (id_simulacion,id_almacen,nombre,tipo,ubicacion_x,ubicacion_y,capacidad_maxima)
                SELECT ?,id_almacen,nombre,tipo,ubicacion_x,ubicacion_y,capacidad_maxima
                FROM almacen
                """)) {
            ps.setLong(1,id);
            ps.executeUpdate();
        }
    }

    public static void limpiarOperacion(Connection cn) throws SQLException {
        try (Statement st = cn.createStatement()) {
            // Conserva asociaciones históricas de TODAS las simulaciones antes de borrarlas.
            st.executeUpdate("""
                    INSERT IGNORE INTO simulacion_ruta_pedido
                        (id_simulacion,id_ruta_pedido,id_ruta,id_pedido,orden_visita,hora_entrega_estimada)
                    SELECT s.id_simulacion,rp.id_ruta_pedido,rp.id_ruta,rp.id_pedido,
                           rp.orden_visita,rp.hora_entrega_estimada
                    FROM ruta_pedido rp JOIN ruta r ON r.id_ruta=rp.id_ruta
                    JOIN solucion s ON s.id_solucion=r.id_solucion
                    """);
            // Archivamos rutas antes de eliminarlas: FK de ruta a vehiculo impedía
            // regenerar una flota distinta para la próxima corrida.
            st.executeUpdate("""
                    INSERT IGNORE INTO simulacion_ruta
                        (id_simulacion,id_ruta,id_solucion,id_vehiculo,id_almacen_origen,
                         distancia_total_km,costo_total,hora_inicio)
                    SELECT s.id_simulacion,r.id_ruta,r.id_solucion,r.id_vehiculo,r.id_almacen_origen,
                           r.distancia_total_km,r.costo_total,r.hora_inicio
                    FROM ruta r JOIN solucion s ON s.id_solucion=r.id_solucion
                    """);
            st.executeUpdate("DELETE FROM ruta_pedido");
            st.executeUpdate("DELETE FROM ruta");
            st.executeUpdate("DELETE FROM pedido");
            // ON DELETE CASCADE limpia averia, bloqueo y bloqueo_nodo.
            st.executeUpdate("DELETE FROM incidencia");
            st.executeUpdate("DELETE FROM mantenimiento_vehiculo");
        }
        // Condición de independencia: ninguna incidencia ni pedido anterior
        // participa en la nueva ejecución. Los históricos permanecen aislados.
        try (Statement verificacion = cn.createStatement();
             ResultSet rs = verificacion.executeQuery("""
                     SELECT
                       (SELECT COUNT(*) FROM pedido),
                       (SELECT COUNT(*) FROM bloqueo),
                       (SELECT COUNT(*) FROM mantenimiento_vehiculo),
                       (SELECT COUNT(*) FROM averia)
                     """)) {
            rs.next();
            for (int i = 1; i <= 4; i++) {
                if (rs.getLong(i) != 0L) {
                    throw new SQLException(
                            "La simulacion debe iniciar sin pedidos, bloqueos, mantenimientos ni averias");
                }
            }
        }
    }
}
