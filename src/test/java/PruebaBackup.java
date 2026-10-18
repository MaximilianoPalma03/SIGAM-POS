package ar.com.sigampos;

import ar.com.sigampos.servicio.BackupServicio;
import java.nio.file.*;
import java.util.prefs.Preferences;

/** Prueba de preferencias y proceso ficticio mysqldump; NO equivale a restauración real MySQL. */
public final class PruebaBackup {
    public static void main(String[] args) throws Exception {
        Path carpeta=Files.createTempDirectory("sigam-backup-");
        Preferences prefs=Preferences.userRoot().node("sigampos-backup-test-"+System.nanoTime());
        try {
            BackupServicio servicio=new BackupServicio(prefs);
            if(servicio.automatico())throw new AssertionError("Backup automático debe ser optativo");
            servicio.configurar(carpeta,true);
            if(!new BackupServicio(prefs).automatico())throw new AssertionError("No persistió la configuración");
            if(!servicio.tocaAutomatico())throw new AssertionError("Falta primer respaldo");
            Path sql=servicio.crear();
            if(!sql.getFileName().toString().endsWith(".sql") || !Files.exists(sql))
                throw new AssertionError("Archivo SQL no generado");
            if(Files.size(sql)<500)throw new AssertionError("Archivo SQL vacío");
            if(servicio.tocaAutomatico())throw new AssertionError("Respaldo repetido antes de 24 horas");
            System.out.println("PruebaBackup OK (mysqldump simulado)");
        } finally {
            prefs.removeNode();
            try(var hijos=Files.list(carpeta)){for(Path p:hijos.toList())Files.deleteIfExists(p);}
            Files.deleteIfExists(carpeta);
        }
    }
}
