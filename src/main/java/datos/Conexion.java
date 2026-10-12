package ar.com.sigampos.datos;
import java.sql.*;
public final class Conexion {
    private Conexion() { }
    public static Connection abrir() throws SQLException {
        String url = System.getenv().getOrDefault("SIGAM_DB_URL", "jdbc:mysql://localhost:3306/sigam_pos?connectionTimeZone=LOCAL");
        String user = System.getenv().getOrDefault("SIGAM_DB_USER", "sigam_app");
        String pass = System.getenv("SIGAM_DB_PASSWORD");
        if (pass == null) throw new SQLException("Definí SIGAM_DB_PASSWORD como variable de entorno");
        return DriverManager.getConnection(url, user, pass);
    }
}
