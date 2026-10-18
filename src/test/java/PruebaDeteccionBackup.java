package ar.com.sigampos;

import ar.com.sigampos.servicio.BackupServicio;
import java.nio.file.*;
import java.util.List;
import java.util.prefs.Preferences;

/** La búsqueda de mysqldump se prueba sin MySQL ni privilegios de administrador. */
public final class PruebaDeteccionBackup {
    public static void main(String[] args) throws Exception {
        Path carpeta=Files.createTempDirectory("sigam-deteccion-");
        Preferences preferencias=Preferences.userRoot().node("sigam-autodetect-test-"+System.nanoTime());
        try {
            BackupServicio s=new BackupServicio(preferencias);
            if(BackupServicio.localizarEnCarpetas(List.of(carpeta))!=null)
                throw new AssertionError("No debe inventarse un ejecutable inexistente");
            Path bin=Files.createDirectory(carpeta.resolve("bin"));
            Path fake=Files.createFile(bin.resolve("mysqldump.exe"));
            if(!fake.equals(BackupServicio.localizarEnCarpetas(List.of(carpeta,bin))))
                throw new AssertionError("No detectó mysqldump en la ruta candidata");
            s.configurarEjecutable(fake.toString());
            if(!s.ejecutableConfigurado().equals(fake.toString()))
                throw new AssertionError("La preferencia no se guardó");
            s.configurarEjecutable("");
            if(!s.ejecutableConfigurado().isEmpty())
                throw new AssertionError("No restauró la detección automática");
            try {
                s.configurarEjecutable(bin.resolve("inexistente.exe").toString());
                throw new AssertionError("Permitió guardar un archivo inexistente");
            } catch(IllegalArgumentException correcto) { /* correcto */ }
            System.out.println("PruebaDeteccionBackup OK");
        } finally {
            preferencias.removeNode();
            try(var archivos=Files.walk(carpeta)){
                for(Path p:archivos.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(p);
            }
        }
    }
}
