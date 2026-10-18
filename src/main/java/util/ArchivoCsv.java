package ar.com.sigampos.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Objects;

/** Exporta CSV UTF-8 con separador ; y BOM para Excel en Windows. */
public final class ArchivoCsv {
    private ArchivoCsv() {}

    public static void guardar(Path archivo, List<String> encabezados, List<? extends List<?>> filas) throws IOException {
        Objects.requireNonNull(archivo);
        Objects.requireNonNull(encabezados);
        Objects.requireNonNull(filas);
        if (encabezados.isEmpty() || filas.isEmpty())
            throw new IllegalArgumentException("No hay registros para exportar");
        Path destino = archivo.toAbsolutePath().normalize();
        Path directorio = destino.getParent();
        if (directorio == null || !Files.isDirectory(directorio))
            throw new IOException("La carpeta elegida no existe");
        Path temporal = Files.createTempFile(directorio, ".sigam-reporte-", ".tmp");
        try {
            try (BufferedWriter salida = Files.newBufferedWriter(temporal, StandardCharsets.UTF_8)) {
                salida.write('\ufeff');
                escribirFila(salida, encabezados);
                for (List<?> fila : filas) {
                    if (fila.size() != encabezados.size())
                        throw new IllegalArgumentException("Una fila del reporte tiene columnas incorrectas");
                    escribirFila(salida, fila);
                }
            }
            try { Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporal); }
    }

    private static void escribirFila(BufferedWriter escritor, List<?> datos) throws IOException {
        for (int i = 0; i < datos.size(); i++) {
            if (i != 0) escritor.write(';');
            Object celda = datos.get(i);
            String valor = celda == null ? "" : String.valueOf(celda);
            // Excel en configuración argentina espera coma decimal y separador de columnas ;.
            if (celda instanceof java.math.BigDecimal numero)
                valor = numero.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
            if (celda instanceof java.sql.Timestamp fecha)
                valor = fecha.toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            // Neutraliza fórmulas SOLO en campos de texto: no altera importes negativos.
            if (celda instanceof CharSequence && !valor.isBlank()) {
                char primero = valor.stripLeading().charAt(0);
                if (primero == '=' || primero == '+' || primero == '-' || primero == '@') valor = "'" + valor;
            }
            escritor.write('"');
            escritor.write(valor.replace("\"", "\"\""));
            escritor.write('"');
        }
        escritor.newLine();
    }
}
