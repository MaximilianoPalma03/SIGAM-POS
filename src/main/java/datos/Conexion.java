package ar.com.sigampos.datos;
import java.sql.*;

public final class Conexion {
    private Conexion() { }
    public static Connection abrir() throws SQLException {
        ConfiguracionBD.Credenciales c = ConfiguracionBD.actual();
        if (c == null) throw new SQLException("No se configuró una base de datos. Abrí el asistente de instalación.");
        return ConfiguracionBD.conectar(c);
    }
    public static ConfiguracionBD.Credenciales parametros() {
        return ConfiguracionBD.actual();
    }
}
