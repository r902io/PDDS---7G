package pe.edu.pucp.sisrap.configuracionoperativa.infraestructura;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import pe.edu.pucp.sisrap.configuracionoperativa.dominio.ConfiguracionOperativa;
import pe.edu.pucp.sisrap.configuracionoperativa.dominio.ConfiguracionOperativa.AlmacenConfigurado;
import pe.edu.pucp.sisrap.configuracionoperativa.dominio.ConfiguracionOperativa.TipoFlotaConfigurado;

/** Persiste PLANTILLAS. La tabla vehiculo se reconstruye al empezar una corrida. */
@Repository
public class JdbcConfiguracionOperativa {
    private final DataSource fuente;

    public JdbcConfiguracionOperativa(DataSource fuente) {
        this.fuente = fuente;
    }

    public ConfiguracionOperativa consultar() {
        try (Connection cn = fuente.getConnection()) {
            return leer(cn);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo leer la configuración operativa", e);
        }
    }

    public ConfiguracionOperativa actualizar(ConfiguracionOperativa configuracion) {
        validarEstructura(configuracion);
        try (Connection cn = fuente.getConnection()) {
            cn.setAutoCommit(false);
            try {
                int[] dimensiones = dimensiones(cn);
                Map<String, String> tiposAlmacen = new HashMap<>();
                try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT id_almacen,tipo FROM almacen FOR UPDATE");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) tiposAlmacen.put(rs.getString(1), rs.getString(2));
                }
                if (tiposAlmacen.size() != 3) {
                    throw new IllegalArgumentException("Debe haber un almacén central y dos intermedios");
                }
                Set<String> ids = new HashSet<>();
                try (PreparedStatement ps = cn.prepareStatement("""
                        UPDATE almacen SET nombre=?, ubicacion_x=?, ubicacion_y=?,
                         capacidad_maxima=?, stock_actual=?,
                         hora_recarga=CASE WHEN tipo='INTERMEDIO' THEN '23:59:59' ELSE NULL END
                        WHERE id_almacen=?
                        """)) {
                    for (AlmacenConfigurado a : configuracion.almacenes()) {
                        if (a == null || a.idAlmacen() == null || !ids.add(a.idAlmacen())) {
                            throw new IllegalArgumentException("Identificadores de almacén duplicados o nulos");
                        }
                        String tipo = tiposAlmacen.get(a.idAlmacen());
                        if (tipo == null || !tipo.equals(a.tipo())) {
                            throw new IllegalArgumentException("No se puede cambiar el identificador ni el tipo del almacén " + a.idAlmacen());
                        }
                        if (a.nombre() == null || a.nombre().isBlank() || a.nombre().length() > 50) {
                            throw new IllegalArgumentException("Nombre de almacén inválido");
                        }
                        if (a.ubicacionX() < 0 || a.ubicacionX() > dimensiones[0]
                                || a.ubicacionY() < 0 || a.ubicacionY() > dimensiones[1]) {
                            throw new IllegalArgumentException("Ubicación fuera de la ciudad: " + a.idAlmacen());
                        }
                        if (tipo.equals("CENTRAL") && a.capacidadMaxima() != null) {
                            throw new IllegalArgumentException("El almacén central tiene capacidad ilimitada");
                        }
                        if (tipo.equals("INTERMEDIO") && (a.capacidadMaxima() == null || a.capacidadMaxima() <= 0)) {
                            throw new IllegalArgumentException("La capacidad intermedia debe ser positiva");
                        }
                        ps.setString(1, a.nombre().trim());
                        ps.setInt(2, a.ubicacionX());
                        ps.setInt(3, a.ubicacionY());
                        if (a.capacidadMaxima() == null) {
                            ps.setNull(4, Types.INTEGER);
                            ps.setNull(5, Types.INTEGER);
                        } else {
                            ps.setInt(4, a.capacidadMaxima());
                            ps.setInt(5, a.capacidadMaxima());
                        }
                        ps.setString(6, a.idAlmacen());
                        ps.addBatch();
                    }
                    if (!ids.equals(tiposAlmacen.keySet())) {
                        throw new IllegalArgumentException("Se deben enviar los tres almacenes existentes");
                    }
                    ps.executeBatch();
                }

                Set<String> tipos = new HashSet<>();
                try (PreparedStatement ps = cn.prepareStatement("""
                        UPDATE tipo_vehiculo_inicial
                        SET cantidad=?,capacidad=?,velocidad=?,costo=? WHERE tipo=?
                        """)) {
                    for (TipoFlotaConfigurado f : configuracion.flota()) {
                        if (f == null || f.tipo() == null || !tipos.add(f.tipo())) {
                            throw new IllegalArgumentException("Tipos de vehículo duplicados o nulos");
                        }
                        if (!Set.of("AUTO", "MOTO", "BICICLETA").contains(f.tipo())) {
                            throw new IllegalArgumentException("Tipo de vehículo inválido: " + f.tipo());
                        }
                        validarTipoFlota(f);
                        ps.setInt(1, f.cantidad());
                        ps.setInt(2, f.capacidadPaquetes());
                        ps.setDouble(3, f.velocidadKmh());
                        ps.setDouble(4, f.costoPorKm());
                        ps.setString(5, f.tipo());
                        if (ps.executeUpdate() != 1) {
                            throw new IllegalArgumentException("No existe plantilla para " + f.tipo());
                        }
                    }
                }
                if (tipos.size() != 3) {
                    throw new IllegalArgumentException("Se requieren los tipos AUTO, MOTO y BICICLETA");
                }
                ConfiguracionOperativa respuesta = leer(cn);
                cn.commit();
                return respuesta;
            } catch (SQLException | RuntimeException e) {
                cn.rollback();
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("No se pudo guardar la configuración operativa", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo guardar la configuración operativa", e);
        }
    }

    private static void validarEstructura(ConfiguracionOperativa c) {
        if (c == null || c.almacenes() == null || c.flota() == null
                || c.almacenes().size() != 3 || c.flota().size() != 3) {
            throw new IllegalArgumentException("Envíe tres almacenes y los tres tipos de flota");
        }
        if (c.flota().stream().filter(java.util.Objects::nonNull)
                .mapToInt(TipoFlotaConfigurado::cantidad).sum() <= 0) {
            throw new IllegalArgumentException("Se requiere al menos un vehículo");
        }
    }

    private static void validarTipoFlota(TipoFlotaConfigurado f) {
        if (f.cantidad() < 0 || f.cantidad() > 200 || f.capacidadPaquetes() <= 0
                || !Double.isFinite(f.velocidadKmh()) || f.velocidadKmh() <= 0 || f.velocidadKmh() > 999.99
                || !Double.isFinite(f.costoPorKm()) || f.costoPorKm() < 0 || f.costoPorKm() > 9999.99) {
            throw new IllegalArgumentException("Cantidad (0..200), capacidad, velocidad y costo inválidos en " + f.tipo());
        }
    }

    private static int[] dimensiones(Connection cn) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("SELECT ancho_km,alto_km FROM ciudad");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) throw new IllegalStateException("No se configuró la ciudad");
            int[] d = {rs.getInt(1), rs.getInt(2)};
            if (rs.next()) throw new IllegalStateException("Debe existir una sola ciudad");
            return d;
        }
    }

    private static ConfiguracionOperativa leer(Connection cn) throws SQLException {
        List<AlmacenConfigurado> almacenes = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement("""
                SELECT id_almacen,nombre,tipo,ubicacion_x,ubicacion_y,capacidad_maxima
                FROM almacen ORDER BY CASE WHEN tipo='CENTRAL' THEN 0 ELSE 1 END,id_almacen
                """); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                almacenes.add(new AlmacenConfigurado(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getInt(4),rs.getInt(5),(Integer)rs.getObject(6)));
            }
        }
        List<TipoFlotaConfigurado> flota = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement("""
                SELECT tipo,cantidad,capacidad,velocidad,costo
                FROM tipo_vehiculo_inicial ORDER BY FIELD(tipo,'AUTO','MOTO','BICICLETA')
                """); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                flota.add(new TipoFlotaConfigurado(rs.getString(1),rs.getInt(2),rs.getInt(3),
                        rs.getDouble(4),rs.getDouble(5)));
            }
        }
        return new ConfiguracionOperativa(almacenes,flota);
    }

    /** Se ejecuta dentro de la transacción de preparación, DESPUÉS del archivado. */
    public static void regenerarVehiculos(Connection cn) throws SQLException {
        String[] tipos = {"AUTO", "MOTO", "BICICLETA"};
        Map<String, String> prefijos = Map.of("AUTO", "TA", "MOTO", "TM", "BICICLETA", "TB");
        List<TipoFlotaConfigurado> plantillas = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement("""
                SELECT tipo,cantidad,capacidad,velocidad,costo FROM tipo_vehiculo_inicial FOR UPDATE
                """); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                TipoFlotaConfigurado f = new TipoFlotaConfigurado(rs.getString(1), rs.getInt(2),
                        rs.getInt(3), rs.getDouble(4), rs.getDouble(5));
                if (!prefijos.containsKey(f.tipo())) throw new SQLException("Tipo de flota inválido");
                validarTipoFlota(f);
                plantillas.add(f);
            }
        }
        if (plantillas.size() != tipos.length) throw new SQLException("Faltan plantillas de la flota");
        int total = plantillas.stream().mapToInt(TipoFlotaConfigurado::cantidad).sum();
        if (total == 0) throw new IllegalArgumentException("La flota no puede estar vacía");
        int x = 0, y = 0;
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT ubicacion_x,ubicacion_y FROM almacen WHERE tipo='CENTRAL'");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) throw new SQLException("Falta el almacén central");
            x = rs.getInt(1); y = rs.getInt(2);
            if (rs.next()) throw new SQLException("Debe haber un solo almacén central");
        }
        // Los registros de ruta se habrán archivado y eliminado; aquí ya no hay FK pendientes.
        try (PreparedStatement ps = cn.prepareStatement("DELETE FROM vehiculo")) {
            ps.executeUpdate();
        }
        try (PreparedStatement ps = cn.prepareStatement(
                "DELETE FROM conductor WHERE id_conductor LIKE 'SIS-%'")) {
            ps.executeUpdate();
        }
        java.util.Map<Integer, Integer> turnos = new java.util.HashMap<>();
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT id_turno,HOUR(hora_inicio) AS hora FROM turno");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) turnos.put(rs.getInt("hora"), rs.getInt("id_turno"));
        }
        if (!turnos.keySet().containsAll(java.util.Set.of(7, 15, 23))) {
            throw new SQLException("No están configurados los tres turnos de reparto");
        }
        try (PreparedStatement ps = cn.prepareStatement("""
                INSERT INTO conductor(id_conductor,nombre,id_turno_asignado)
                VALUES (?,?,?)
                """)) {
            for (TipoFlotaConfigurado f : plantillas) {
                for (int n = 1; n <= f.cantidad(); n++) {
                    String idVehiculo = prefijos.get(f.tipo()) + String.format(java.util.Locale.ROOT, "%02d", n);
                    for (int turno = 1; turno <= 3; turno++) {
                        ps.setString(1, "SIS-" + idVehiculo + "-" + turno);
                        ps.setString(2, "Repartidor " + idVehiculo + " turno " + turno);
                        ps.setInt(3, turnos.get(turno == 1 ? 7 : turno == 2 ? 15 : 23));
                        ps.addBatch();
                    }
                }
            }
            ps.executeBatch();
        }
        try (PreparedStatement ps = cn.prepareStatement("""
                INSERT INTO vehiculo(id_vehiculo,tipo,capacidad_paquetes,velocidad_kmh,costo_por_km,
                                     estado,posicion_x,posicion_y,id_conductor_actual)
                VALUES(?,?,?,?,?,'DISPONIBLE',?,?,NULL)
                """)) {
            for (TipoFlotaConfigurado f : plantillas) {
                for (int n = 1; n <= f.cantidad(); n++) {
                    ps.setString(1, prefijos.get(f.tipo()) + String.format(java.util.Locale.ROOT, "%02d", n));
                    ps.setString(2, f.tipo());
                    ps.setInt(3, f.capacidadPaquetes());
                    ps.setDouble(4, f.velocidadKmh());
                    ps.setDouble(5, f.costoPorKm());
                    ps.setInt(6, x);
                    ps.setInt(7, y);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }
}
