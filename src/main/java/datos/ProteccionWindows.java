package ar.com.sigampos.datos;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Cifra credenciales para el usuario Windows mediante DPAPI/PowerShell. Nunca se envían en argumentos. */
final class ProteccionWindows {
    private ProteccionWindows() { }

    static String cifrar(String secreto) throws IOException { return ejecutar(true, secreto); }
    static String descifrar(String cifrado) throws IOException { return ejecutar(false, cifrado); }

    private static String ejecutar(boolean cifrar, String entrada) throws IOException {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            throw new IOException("Las credenciales locales se protegen mediante Windows DPAPI: requiere Windows.");
        }
        String utf8 = "[Console]::InputEncoding=[Text.Encoding]::UTF8;" +
            "[Console]::OutputEncoding=[Text.Encoding]::UTF8;";
        String comando = utf8 + (cifrar
            ? "$v=[Console]::In.ReadToEnd(); $s=ConvertTo-SecureString -String $v -AsPlainText -Force; " +
              "[Console]::Out.Write((ConvertFrom-SecureString -SecureString $s))"
            : "$v=[Console]::In.ReadToEnd(); $s=ConvertTo-SecureString -String $v; " +
              "$b=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($s); " +
              "try {[Console]::Out.Write([Runtime.InteropServices.Marshal]::PtrToStringBSTR($b))} " +
              "finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b)}");
        Process proceso = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", comando)
            .redirectErrorStream(true).start();
        try (OutputStream out = proceso.getOutputStream()) {
            out.write(entrada.getBytes(StandardCharsets.UTF_8));
        }
        try {
            if (!proceso.waitFor(20, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();
                throw new IOException("Se agotó el tiempo al proteger la contraseña con Windows.");
            }
            byte[] bytes = proceso.getInputStream().readAllBytes();
            if (proceso.exitValue() != 0) {
                throw new IOException("Windows no pudo proteger las credenciales. Revisá PowerShell y el perfil de usuario.");
            }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operación de protección interrumpida", e);
        }
    }
}
