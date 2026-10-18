package ar.com.sigampos;

import ar.com.sigampos.datos.*;
import java.nio.file.*;
import java.util.*;

/** Contratos verificables del instalador (no sustituye una prueba real en Windows). */
public final class PruebaDistribucionV1 {
    public static void main(String[] args) throws Exception {
        Path proyecto = Path.of(System.getProperty("user.dir"));
        String innosetup = Files.readString(proyecto.resolve("distribucion/SIGAM-POS-Setup.iss"));
        String constructor = Files.readString(proyecto.resolve("distribucion/Crear-SIGAM-POS-Windows.ps1"));
        if (!innosetup.contains("OutputBaseFilename=SIGAM-POS-Setup"))
            throw new AssertionError("Falta Setup.exe");
        if (!innosetup.contains("UninstallDisplayIcon") || !innosetup.contains("AppId="))
            throw new AssertionError("No se registró la desinstalación");
        if (java.util.regex.Pattern.compile("(?m)^\\s*\\[UninstallDelete\\]", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(innosetup).find())
            throw new AssertionError("No debe haber acciones de borrado durante la desinstalación");
        if (!constructor.contains("--type','app-image") || !constructor.contains("jpackage"))
            throw new AssertionError("Falta ejecutable de escritorio con runtime");
        for (String requerido : List.of("mysqld.exe","mysqldump.exe","mysqladmin.exe","flatlaf","mysql-connector-j")) {
            if (!constructor.contains(requerido)) throw new AssertionError("Paquete incompleto: " + requerido);
        }
        if (!constructor.contains("mysql-9\\.7\\.1-winx64") || !constructor.contains("MySQL 9.7.1"))
            throw new AssertionError("El constructor debe exigir MySQL 9.7.1 LTS");
        if (!constructor.contains("java.prefs") || !constructor.contains("java.sql"))
            throw new AssertionError("Runtime Java incompleto");
        if (!ConfiguracionBD.archivo().startsWith(ConfiguracionBD.datosAplicacion()))
            throw new AssertionError("Credenciales dentro de carpeta de instalación");
        if (!MysqlLocal.datos().startsWith(ConfiguracionBD.datosAplicacion()))
            throw new AssertionError("Datos de MySQL dentro de carpeta de instalación");
        if (EsquemaInicial.instrucciones().stream().anyMatch(sql -> sql.toUpperCase(Locale.ROOT).contains("DROP TABLE")))
            throw new AssertionError("El esquema de instalación contiene borrados");
        System.out.println("PruebaDistribucionV1 OK (verificación de fuentes, no prueba nativa Windows)");
    }
}
