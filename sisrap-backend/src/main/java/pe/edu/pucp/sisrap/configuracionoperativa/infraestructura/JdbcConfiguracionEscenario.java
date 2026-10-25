package pe.edu.pucp.sisrap.configuracionoperativa.infraestructura;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
import pe.edu.pucp.sisrap.configuracionoperativa.dominio.ConfiguracionOperativa;
import pe.edu.pucp.sisrap.configuracionoperativa.dominio.ConfiguracionOperativa.AlmacenConfigurado;
import pe.edu.pucp.sisrap.configuracionoperativa.dominio.ConfiguracionOperativa.TipoFlotaConfigurado;
import pe.edu.pucp.sisrap.simulacion.dominio.EscenarioSimulacion;
import pe.edu.pucp.sisrap.simulacion.infraestructura.EsquemaDatosSimulacion;
import pe.edu.pucp.sisrap.pedido.infraestructura.EsquemaHistoricosEscenario;

/** Plantillas persistentes; solo se copian a la operación al comenzar una corrida. */
@Repository
public class JdbcConfiguracionEscenario {
    public record Plantilla(EscenarioSimulacion escenario, int anchoKm, int altoKm,
                            List<AlmacenConfigurado> almacenes, List<TipoFlotaConfigurado> flota) { }

    private final DataSource fuente;
    private final JsonMapper json;
    private final JdbcConfiguracionOperativa operativa;
    private boolean inicializado;

    public JdbcConfiguracionEscenario(DataSource fuente, JsonMapper json, JdbcConfiguracionOperativa operativa) {
        this.fuente = fuente;
        this.json = json;
        this.operativa = operativa;
    }

    public synchronized void asegurar() {
        if (inicializado) return;
        try (Connection cn = fuente.getConnection()) {
            EsquemaDatosSimulacion.asegurar(cn);
            EsquemaHistoricosEscenario.asegurar(cn);
            try (var st = cn.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS configuracion_escenario (
                        escenario VARCHAR(40) PRIMARY KEY, contenido LONGTEXT NOT NULL
                    )
                    """);
            }
            ConfiguracionOperativa base = operativa.consultar();
            int ancho, alto;
            try (var ps = cn.prepareStatement("SELECT ancho_km,alto_km FROM ciudad"); var rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("No existe una ciudad configurada");
                ancho = rs.getInt(1); alto = rs.getInt(2);
                if (rs.next()) throw new IllegalStateException("Debe existir una sola ciudad");
            }
            cn.setAutoCommit(false);
            try (var ps = cn.prepareStatement("INSERT IGNORE INTO configuracion_escenario VALUES (?,?)")) {
                for (var escenario : EscenarioSimulacion.values()) {
                    ps.setString(1, escenario.name());
                    ps.setString(2, json.writeValueAsString(new Plantilla(escenario, ancho, alto, base.almacenes(), base.flota())));
                    ps.addBatch();
                }
                ps.executeBatch();
                cn.commit();
            } catch (SQLException | RuntimeException e) { cn.rollback(); throw e; }
            inicializado = true;
        } catch (SQLException e) { throw new IllegalStateException("No se pudieron preparar las configuraciones por escenario", e); }
    }

    public synchronized Plantilla consultar(EscenarioSimulacion escenario) {
        asegurar();
        try (Connection cn = fuente.getConnection()) { return leer(cn, escenario); }
        catch (SQLException e) { throw new IllegalStateException("No se pudo consultar el escenario", e); }
    }

    private Plantilla leer(Connection cn, EscenarioSimulacion escenario) throws SQLException {
        try (var ps = cn.prepareStatement("SELECT contenido FROM configuracion_escenario WHERE escenario=?")) {
            ps.setString(1, escenario.name());
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("No existe configuración para " + escenario);
                return json.readValue(rs.getString(1), Plantilla.class);
            }
        }
    }

    public synchronized void validarPlantilla(EscenarioSimulacion escenario, Plantilla plantilla) {
        asegurar();
        if (plantilla == null || plantilla.escenario() != escenario)
            throw new IllegalArgumentException("La configuración no corresponde al escenario seleccionado");
        try (Connection cn = fuente.getConnection()) { validar(cn, plantilla); }
        catch (SQLException e) { throw new IllegalStateException("No se pudo validar la configuración", e); }
    }

    public synchronized Plantilla guardar(EscenarioSimulacion escenario, Plantilla plantilla) {
        asegurar();
        if (plantilla == null || plantilla.escenario() != escenario)
            throw new IllegalArgumentException("La configuración no corresponde al escenario seleccionado");
        try (Connection cn = fuente.getConnection()) {
            validar(cn, plantilla);
            try (var ps = cn.prepareStatement("UPDATE configuracion_escenario SET contenido=? WHERE escenario=?")) {
                ps.setString(1, json.writeValueAsString(plantilla));
                ps.setString(2, escenario.name());
                if (ps.executeUpdate() != 1) throw new IllegalStateException("No existe el escenario");
            }
            return plantilla;
        } catch (SQLException e) { throw new IllegalStateException("No se pudo guardar el escenario", e); }
    }

    /** Participa en la misma transacción que archiva y prepara la nueva corrida. */
    public void aplicar(Connection cn, EscenarioSimulacion escenario) throws SQLException {
        Plantilla p = leer(cn, escenario);
        validar(cn, p);
        try (var ps = cn.prepareStatement("UPDATE ciudad SET ancho_km=?,alto_km=?")) {
            ps.setInt(1, p.anchoKm()); ps.setInt(2, p.altoKm()); ps.executeUpdate();
        }
        try (var ps = cn.prepareStatement("""
            UPDATE almacen SET nombre=?,ubicacion_x=?,ubicacion_y=?,capacidad_maxima=?,stock_actual=?,
            hora_recarga=CASE WHEN tipo='INTERMEDIO' THEN '23:59:59' ELSE NULL END WHERE id_almacen=?
            """)) {
            for (var a : p.almacenes()) {
                ps.setString(1, a.nombre().trim()); ps.setInt(2, a.ubicacionX()); ps.setInt(3, a.ubicacionY());
                if (a.capacidadMaxima() == null) { ps.setNull(4, Types.INTEGER); ps.setNull(5, Types.INTEGER); }
                else { ps.setInt(4, a.capacidadMaxima()); ps.setInt(5, a.capacidadMaxima()); }
                ps.setString(6, a.idAlmacen()); ps.addBatch();
            }
            ps.executeBatch();
        }
        try (var ps = cn.prepareStatement("UPDATE tipo_vehiculo_inicial SET cantidad=?,capacidad=?,velocidad=?,costo=? WHERE tipo=?")) {
            for (var f : p.flota()) {
                ps.setInt(1, f.cantidad()); ps.setInt(2, f.capacidadPaquetes());
                ps.setDouble(3, f.velocidadKmh()); ps.setDouble(4, f.costoPorKm()); ps.setString(5, f.tipo());
                if (ps.executeUpdate() != 1) throw new IllegalStateException("Falta el tipo de flota " + f.tipo());
            }
        }
    }

    private static void validar(Connection cn, Plantilla p) throws SQLException {
        if (p.anchoKm() < 1 || p.altoKm() < 1 || p.anchoKm() > 150 || p.altoKm() > 150)
            throw new IllegalArgumentException("El ancho y alto deben ser enteros entre 1 y 150 km");
        if (p.almacenes() == null || p.flota() == null || p.almacenes().size() != 3 || p.flota().size() != 3)
            throw new IllegalArgumentException("Se requieren tres almacenes y tres tipos de flota");
        Map<String, String> originales = new java.util.HashMap<>();
        try (var ps = cn.prepareStatement("SELECT id_almacen,tipo FROM almacen"); var rs = ps.executeQuery()) {
            while (rs.next()) originales.put(rs.getString(1), rs.getString(2));
        }
        Set<String> ids = new HashSet<>(), posiciones = new HashSet<>();
        int centrales = 0;
        for (var a : p.almacenes()) {
            if (a == null || a.idAlmacen() == null || !ids.add(a.idAlmacen())
                    || !java.util.Objects.equals(originales.get(a.idAlmacen()), a.tipo()) || a.tipo() == null)
                throw new IllegalArgumentException("No se pueden cambiar los identificadores ni tipos de almacén");
            if (a.nombre() == null || a.nombre().isBlank() || a.nombre().length() > 50)
                throw new IllegalArgumentException("Nombre de almacén inválido");
            if (a.ubicacionX() < 0 || a.ubicacionX() > p.anchoKm() || a.ubicacionY() < 0 || a.ubicacionY() > p.altoKm())
                throw new IllegalArgumentException("El almacén " + a.nombre() + " queda fuera del mapa");
            if (!posiciones.add(a.ubicacionX() + ":" + a.ubicacionY()))
                throw new IllegalArgumentException("Los almacenes deben tener ubicaciones diferentes");
            if ("CENTRAL".equals(a.tipo())) {
                centrales++;
                if (a.capacidadMaxima() != null) throw new IllegalArgumentException("El almacén central tiene inventario ilimitado");
            } else if (!"INTERMEDIO".equals(a.tipo()) || a.capacidadMaxima() == null || a.capacidadMaxima() <= 0)
                throw new IllegalArgumentException("La capacidad de los almacenes intermedios debe ser positiva");
        }
        if (centrales != 1 || !ids.equals(originales.keySet()))
            throw new IllegalArgumentException("Se requiere el almacén central y los dos intermedios existentes");
        Set<String> tipos = new HashSet<>();
        int cantidad = 0;
        for (var f : p.flota()) {
            if (f == null || f.tipo() == null || !Set.of("AUTO", "MOTO", "BICICLETA").contains(f.tipo()) || !tipos.add(f.tipo()))
                throw new IllegalArgumentException("Los tipos de flota deben ser AUTO, MOTO y BICICLETA");
            if (f.cantidad() < 0 || f.cantidad() > 200 || f.capacidadPaquetes() <= 0
                    || !Double.isFinite(f.velocidadKmh()) || f.velocidadKmh() <= 0 || f.velocidadKmh() > 999.99
                    || !Double.isFinite(f.costoPorKm()) || f.costoPorKm() < 0 || f.costoPorKm() > 9999.99)
                throw new IllegalArgumentException("Cantidad, capacidad, velocidad o costo inválidos en " + f.tipo());
            cantidad += f.cantidad();
        }
        if (cantidad == 0) throw new IllegalArgumentException("Se requiere al menos un vehículo");
        try (var ps = cn.prepareStatement("""
            SELECT 1 FROM pedido_historico_escenario
            WHERE escenario=? AND (ubicacion_x<0 OR ubicacion_y<0 OR ubicacion_x>? OR ubicacion_y>?) LIMIT 1
            """)) {
            ps.setString(1, p.escenario().name());
            ps.setInt(2, p.anchoKm()); ps.setInt(3, p.altoKm());
            try (var rs = ps.executeQuery()) {
                if (rs.next()) throw new IllegalArgumentException(
                    "Hay destinos de los históricos fuera de estas dimensiones. Amplía el mapa o reemplaza los históricos incompatibles");
            }
        }
    }
}
