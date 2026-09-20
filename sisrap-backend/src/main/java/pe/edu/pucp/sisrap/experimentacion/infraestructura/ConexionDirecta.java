package pe.edu.pucp.sisrap.experimentacion.infraestructura;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

/** DataSource mínimo sobre DriverManager, para ejecutar el experimento sin levantar Spring. */
public final class ConexionDirecta implements DataSource {
    private final String url, usuario, clave;

    public ConexionDirecta(String url, String usuario, String clave) {
        this.url = url;
        this.usuario = usuario;
        this.clave = clave;
    }

    @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, usuario, clave); }
    @Override public Connection getConnection(String u, String c) throws SQLException { return DriverManager.getConnection(url, u, c); }
    @Override public PrintWriter getLogWriter() { return null; }
    @Override public void setLogWriter(PrintWriter out) { }
    @Override public void setLoginTimeout(int segundos) { DriverManager.setLoginTimeout(segundos); }
    @Override public int getLoginTimeout() { return DriverManager.getLoginTimeout(); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
    @Override public <T> T unwrap(Class<T> tipo) throws SQLException { throw new SQLException("No es un wrapper"); }
    @Override public boolean isWrapperFor(Class<?> tipo) { return false; }
}