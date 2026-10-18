package ar.com.sigampos.datos;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;

/** Selecciona una única BD de forma explícita; jamás prueba contraseñas ni cambia otras bases. */
public final class ConfiguracionBD {
    public record Credenciales(String url, String usuario, String clave, boolean local) { }
    private static volatile Credenciales activa;
    private static final String ARCHIVO = "conexion.properties";
    private ConfiguracionBD() { }

    public static Path datosAplicacion() {
        String appData = System.getenv("LOCALAPPDATA");
        return (appData == null || appData.isBlank()
            ? Path.of(System.getProperty("user.home"), ".sigam-pos")
            : Path.of(appData, "SIGAM-POS")).toAbsolutePath().normalize();
    }

    public static Path archivo() { return datosAplicacion().resolve(ARCHIVO); }

    public static boolean configuracionExistente() { return Files.isRegularFile(archivo()); }

    /** Las variables del desarrollador tienen prioridad y NO se guardan sin autorización. */
    public static boolean entornoExplicito() {
        return System.getenv("SIGAM_DB_PASSWORD") != null;
    }

    public static Credenciales desdeEntorno() {
        if (!entornoExplicito()) return null;
        return new Credenciales(System.getenv().getOrDefault("SIGAM_DB_URL",
            "jdbc:mysql://localhost:3306/sigam_pos?connectionTimeZone=LOCAL"),
            System.getenv().getOrDefault("SIGAM_DB_USER", "sigam_app"),
            System.getenv("SIGAM_DB_PASSWORD"), false);
    }

    public static Credenciales cargar() throws IOException {
        if (!configuracionExistente()) return null;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(archivo())) { p.load(in); }
        if (!"1".equals(p.getProperty("formato"))) {
            throw new IOException("Configuración de conexión desconocida. No se modificó ningún dato.");
        }
        String url = p.getProperty("url", ""), usuario = p.getProperty("usuario", "");
        String blob = p.getProperty("clave.dpapi", "");
        if (!url.startsWith("jdbc:mysql:") || usuario.isBlank() || blob.isBlank()) {
            throw new IOException("La configuración guardada está incompleta. No se modificó ningún dato.");
        }
        return new Credenciales(url, usuario, ProteccionWindows.descifrar(blob),
            Boolean.parseBoolean(p.getProperty("local", "false")));
    }

    public static void guardar(Credenciales c) throws IOException {
        if (c == null || c.clave() == null || c.clave().isBlank())
            throw new IOException("No se pueden guardar credenciales vacías.");
        Path carpeta = datosAplicacion(); Files.createDirectories(carpeta);
        Properties p = new Properties();
        p.setProperty("formato", "1");
        p.setProperty("url", c.url());
        p.setProperty("usuario", c.usuario());
        p.setProperty("local", Boolean.toString(c.local()));
        p.setProperty("clave.dpapi", ProteccionWindows.cifrar(c.clave()));
        Path temporal = Files.createTempFile(carpeta, "conexion-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporal)) { p.store(out, "SIGAM-POS - credenciales protegidas con DPAPI"); }
            try { Files.move(temporal, archivo(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporal, archivo(), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporal); }
    }

    public static void activar(Credenciales c) { activa = c; }

    public static Credenciales actual() {
        Credenciales c = activa;
        if (c != null) return c;
        c = desdeEntorno();
        if (c != null) return c;
        return null;
    }

    public static Connection conectar(Credenciales c) throws SQLException {
        return DriverManager.getConnection(c.url(), c.usuario(), c.clave());
    }
}
