package pe.edu.pucp.sisrap.experimentacion.infraestructura;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.experimentacion.dominio.BaseOperativa;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
import pe.edu.pucp.sisrap.parametros.dominio.ValidarConfiguracion;
import pe.edu.pucp.sisrap.parametros.infraestructura.JdbcParametros;

/** Lee de la BD el perfil de parámetros, la ciudad, los almacenes y la flota. */
public final class JdbcBaseOperativa {
    private final DataSource fuente;

    public JdbcBaseOperativa(DataSource fuente) {
        this.fuente = fuente;
    }

    public BaseOperativa cargar(String perfil) {
        try (var cn = fuente.getConnection()) {
            cn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            cn.setAutoCommit(false);
            try {
                BaseOperativa base = leer(cn, perfil);
                cn.commit();
                return base;
            } catch (Exception e) {
                cn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo leer la base operativa", e);
        }
    }

    /** Lectura dentro de una transacción ya abierta (la reutiliza JdbcExperimento). */
    public static BaseOperativa leer(Connection cn, String perfil) throws SQLException {
        Configuracion cfg = JdbcParametros.leer(cn, perfil, false);
        ValidarConfiguracion.ejecutar(cfg);
        int ancho, alto;
        try (var st = cn.createStatement(); var rs = st.executeQuery("SELECT ancho_km,alto_km FROM ciudad ORDER BY id_ciudad")) {
            if (!rs.next()) throw new IllegalArgumentException("Falta ciudad");
            ancho = rs.getInt(1);
            alto = rs.getInt(2);
            if (rs.next()) throw new IllegalArgumentException("El esquema operativo requiere una sola ciudad");
        }
        List<Almacen> almacenes = new ArrayList<>();
        try (var st = cn.createStatement(); var rs = st.executeQuery("SELECT * FROM almacen ORDER BY id_almacen")) {
            while (rs.next()) {
                Integer capacidad = (Integer) rs.getObject("capacidad_maxima"), stock = (Integer) rs.getObject("stock_actual");
                if (capacidad != null && (capacidad <= 0 || stock == null || stock < 0 || stock > capacidad))
                    throw new IllegalArgumentException("Stock/capacidad inválidos");
                almacenes.add(new Almacen(rs.getString("id_almacen"),
                        nodo(rs.getInt("ubicacion_x"), rs.getInt("ubicacion_y"), ancho, alto), capacidad, stock));
            }
        }
        var centrales = almacenes.stream().filter(a -> a.getCapacidadMaxima() == null).toList();
        if (centrales.size() != 1) throw new IllegalArgumentException("Se requiere exactamente un almacén central");
        Nodo central = centrales.get(0).getUbicacion();
        List<Vehiculo> vehiculos = new ArrayList<>();
        try (var st = cn.createStatement(); var rs = st.executeQuery("SELECT * FROM vehiculo ORDER BY id_vehiculo")) {
            while (rs.next()) {
                var v = new Vehiculo(rs.getString("id_vehiculo"), rs.getInt("capacidad_paquetes"),
                        rs.getDouble("velocidad_kmh"), rs.getDouble("costo_por_km"), central);
                v.setDisponible("DISPONIBLE".equals(rs.getString("estado")));
                vehiculos.add(v);
            }
        }
        if (vehiculos.isEmpty()) throw new IllegalArgumentException("Flota vacía");
        return new BaseOperativa(cfg, ancho, alto, central, almacenes, vehiculos);
    }

    public static Nodo nodo(int x, int y, int ancho, int alto) {
        if (x < 0 || x > ancho || y < 0 || y > alto) throw new IllegalArgumentException("Coordenada fuera de ciudad");
        return new Nodo(x, y);
    }
}