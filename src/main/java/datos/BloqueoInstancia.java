package ar.com.sigampos.datos;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;

/** Una sola interfaz SIGAM-POS por usuario Windows evita cerrar MySQL mientras otra está cobrando. */
public final class BloqueoInstancia {
    private static FileChannel canal;
    private static FileLock bloqueo;
    private BloqueoInstancia() { }

    public static synchronized boolean adquirir() throws IOException {
        if (bloqueo != null && bloqueo.isValid()) return true;
        Files.createDirectories(ConfiguracionBD.datosAplicacion());
        canal=FileChannel.open(ConfiguracionBD.datosAplicacion().resolve("sigam.lock"),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            bloqueo=canal.tryLock();
            if (bloqueo==null) {canal.close();canal=null;return false;}
            return true;
        } catch(OverlappingFileLockException e) {
            canal.close(); canal=null; return false;
        }
    }
}
