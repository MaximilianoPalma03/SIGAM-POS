package ar.com.sigampos.datos;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** MySQL portátil, aislado de las instalaciones existentes y del directorio del programa. */
public final class MysqlLocal {
    public static final int PUERTO = 33307;
    private static final String BD = "sigam_pos";
    private MysqlLocal() { }

    public static Path mysqlHome() {
        String forzado = System.getProperty("sigam.mysql.home", System.getenv("SIGAM_MYSQL_HOME"));
        if (forzado != null && !forzado.isBlank()) return Path.of(forzado).toAbsolutePath().normalize();
        String ejecutable = System.getProperty("jpackage.app-path");
        if (ejecutable != null && !ejecutable.isBlank()) {
            Path p = Path.of(ejecutable).toAbsolutePath().getParent().resolve("mysql");
            if (Files.isRegularFile(p.resolve("bin/mysqld.exe"))) return p;
        }
        // En jpackage/app-image, java.home apunta a <app>/runtime.
        return Path.of(System.getProperty("java.home")).toAbsolutePath().getParent().resolve("mysql");
    }

    public static Path clienteDump() { return mysqlHome().resolve("bin/mysqldump.exe"); }
    public static boolean incluido() { return Files.isRegularFile(mysqlHome().resolve("bin/mysqld.exe")); }

    public static Path datos() { return ConfiguracionBD.datosAplicacion().resolve("mysql-data"); }
    public static Path archivoConfiguracion() { return ConfiguracionBD.datosAplicacion().resolve("mysql.ini"); }
    public static String jdbc() {
        return "jdbc:mysql://127.0.0.1:"+PUERTO+"/"+BD+"?connectionTimeZone=LOCAL&allowPublicKeyRetrieval=true";
    }

    private static String rutaIni(Path p) { return p.toAbsolutePath().normalize().toString().replace('\\','/'); }

    private static void prepararIni() throws IOException {
        Path root = ConfiguracionBD.datosAplicacion();
        Files.createDirectories(root);
        String ini = "[mysqld]\n"+
            "basedir=\""+rutaIni(mysqlHome())+"\"\n"+
            "datadir=\""+rutaIni(datos())+"\"\n"+
            "port="+PUERTO+"\n"+
            "bind-address=127.0.0.1\n"+
            "mysqlx=0\n"+
            "log-error=\""+rutaIni(root.resolve("mysql-error.log"))+"\"\n"+
            "pid-file=\""+rutaIni(root.resolve("mysql.pid"))+"\"\n"+
            "character-set-server=utf8mb4\n"+
            "collation-server=utf8mb4_unicode_ci\n";
        Files.writeString(archivoConfiguracion(), ini, StandardCharsets.UTF_8);
    }

    private static boolean puertoAbierto() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", PUERTO), 450);
            return true;
        } catch(IOException e) { return false; }
    }

    private static String aleatorio() {
        byte[] b = new byte[24]; new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static void inicializarArchivos() throws IOException, InterruptedException {
        Path datadir = datos();
        if (Files.exists(datadir)) {
            // Los datos existentes nunca se inicializan de nuevo, ni siquiera si la estructura está dañada.
            throw new IOException("Ya existe la carpeta de datos MySQL. " +
                "No se inicializará nuevamente: " + datadir + 
                ". Si se perdió la configuración, recuperala desde el respaldo o solicitá asistencia.");
        }
        Files.createDirectories(ConfiguracionBD.datosAplicacion());
        Path log = ConfiguracionBD.datosAplicacion().resolve("mysql-inicializacion.log");
        Process p = new ProcessBuilder(mysqlHome().resolve("bin/mysqld.exe").toString(),
            "--no-defaults", "--initialize-insecure", "--basedir="+mysqlHome(),
            "--datadir="+datadir, "--console")
            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean terminado = p.waitFor(90, TimeUnit.SECONDS);
        if (!terminado) { p.destroyForcibly(); throw new IOException("MySQL tardó demasiado en inicializar. Consultá " + log); }
        if (p.exitValue()!=0) throw new IOException("No se pudo inicializar MySQL. Consultá " + log);
    }

    private static void esperarSQL() throws SQLException, InterruptedException {
        SQLException ultimo = null;
        String url = "jdbc:mysql://127.0.0.1:"+PUERTO+"/?connectionTimeZone=LOCAL&allowPublicKeyRetrieval=true";
        for (int intento=0; intento<35; intento++) {
            try (Connection c=DriverManager.getConnection(url,"root","")) { return; }
            catch (SQLException e) { ultimo=e; Thread.sleep(350); }
        }
        throw new SQLException("MySQL inició, pero no respondió a una conexión SQL inicial",ultimo);
    }

    private static void iniciarServidor() throws IOException, InterruptedException {
        if (!incluido()) throw new IOException("Falta MySQL portátil (mysql/bin/mysqld.exe). " +
            "Utilizá el paquete Windows completo, no solamente el archivo .exe.");
        if (puertoAbierto()) return;
        prepararIni();
        Path log = ConfiguracionBD.datosAplicacion().resolve("mysql-arranque.log");
        Process p = new ProcessBuilder(mysqlHome().resolve("bin/mysqld.exe").toString(),
            "--defaults-file="+archivoConfiguracion())
            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        for (int i = 0; i < 120; i++) {
            if (puertoAbierto()) return;
            if (!p.isAlive()) throw new IOException("MySQL portátil no pudo iniciarse. Revisá " + log);
            Thread.sleep(250);
        }
        throw new IOException("MySQL portátil no respondió en 30 segundos. Revisá " + log);
    }


    public static void detenerServidorLocal() {
        Path cliente = mysqlHome().resolve("bin/mysqladmin.exe");
        Path secreto = ConfiguracionBD.datosAplicacion().resolve("mysql-root.dpapi");
        if (!Files.isRegularFile(cliente) || !Files.isRegularFile(secreto) || !puertoAbierto()) return;
        try {
            String root = ProteccionWindows.descifrar(Files.readString(secreto, StandardCharsets.UTF_8).trim());
            ProcessBuilder pb = new ProcessBuilder(cliente.toString(),
                "--host=127.0.0.1", "--port="+PUERTO, "--user=root", "shutdown");
            pb.environment().put("MYSQL_PWD", root);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ConfiguracionBD.datosAplicacion().resolve("mysql-cierre.log").toFile());
            Process p = pb.start();
            if (!p.waitFor(12, TimeUnit.SECONDS)) p.destroyForcibly(); // termina solo mysqladmin, NO mysqld
            for (int i=0; i<35 && puertoAbierto(); i++) Thread.sleep(200);
        } catch (Exception e) {
            // Si no puede cerrarse limpiamente, el servidor sigue activo y los datos siguen intactos.
            try {Files.writeString(ConfiguracionBD.datosAplicacion().resolve("mysql-cierre-error.log"),
                "No se pudo completar el cierre seguro: "+e.getClass().getSimpleName(),StandardCharsets.UTF_8);}
            catch(IOException ignored) { }
        }
    }

    /** Arranque posterior: abre la instancia que YA fue configurada. Jamás reinicializa datos. */
    public static void asegurarIniciado(ConfiguracionBD.Credenciales credenciales)
            throws IOException, InterruptedException, SQLException {
        if (!credenciales.local()) return;
        if (!Files.isDirectory(datos()))
            throw new IOException("Falta el directorio persistente de MySQL. No se creará otro automáticamente.");
        iniciarServidor();
        try (Connection c = ConfiguracionBD.conectar(credenciales)) { ValidadorEsquema.comprobar(c); }
    }

    /** Ejecutar SOLO tras confirmación de nueva instalación. Nunca sobrescribe un datadir existente. */
    public static ConfiguracionBD.Credenciales instalarNueva()
            throws IOException, InterruptedException, SQLException {
        if (!incluido()) throw new IOException("El paquete no incluye mysql/bin/mysqld.exe.");
        if (Files.exists(datos()) || ConfiguracionBD.configuracionExistente())
            throw new IOException("Se encontraron datos o configuración previa. No se hará una instalación nueva.");
        if (puertoAbierto())
            throw new IOException("El puerto local "+PUERTO+" está en uso. No se reemplazará ese servidor.");
        // Antes de crear nada comprobamos la integridad del esquema incluido.
        EsquemaInicial.instrucciones();
        inicializarArchivos();
        iniciarServidor();
        String appClave = aleatorio(), rootClave = aleatorio();
        String urlRoot = "jdbc:mysql://127.0.0.1:"+PUERTO+"/?connectionTimeZone=LOCAL";
        esperarSQL();
        try (Connection c = DriverManager.getConnection(urlRoot+"&allowPublicKeyRetrieval=true", "root", "");
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE `"+BD+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            // El administrador incluido en PersonalServicio se genera al abrir la interfaz.
            try (Connection base = DriverManager.getConnection(jdbc(), "root", "")) {
                EsquemaInicial.crear(base);
            }
            for (String host : List.of("localhost", "127.0.0.1")) {
                s.execute("CREATE USER 'sigam_app'@'"+host+"' IDENTIFIED BY '"+appClave+"'");
                s.execute("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, TRIGGER, " +
                    "SHOW VIEW ON `"+BD+"`.* TO 'sigam_app'@'"+host+"'");
            }
            ConfiguracionBD.Credenciales credenciales =
                new ConfiguracionBD.Credenciales(jdbc(), "sigam_app", appClave, true);
            try (Connection prueba = ConfiguracionBD.conectar(credenciales)) {
                ValidadorEsquema.comprobar(prueba);
            }
            // Salvaguardamos la clave de recuperación antes de cerrar el acceso inicial de root.
            Path rootSecreto = ConfiguracionBD.datosAplicacion().resolve("mysql-root.dpapi");
            Files.writeString(rootSecreto, ProteccionWindows.cifrar(rootClave), StandardCharsets.UTF_8);
            s.execute("ALTER USER 'root'@'localhost' IDENTIFIED BY '"+rootClave+"'");
            ConfiguracionBD.guardar(credenciales);
            return credenciales;
        } catch(SQLException | IOException error) {
            // No se elimina nada: una inicialización interrumpida necesita recuperación manual.
            throw new IOException("La instalación local quedó incompleta. No se borraron datos. " +
                "Consultá el registro de MySQL; NO repitas inicialización. Detalle: " +error.getMessage(), error);
        }
    }
}
