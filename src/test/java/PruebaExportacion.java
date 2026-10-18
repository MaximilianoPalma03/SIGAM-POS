package ar.com.sigampos;

import ar.com.sigampos.util.ArchivoCsv;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Verifica CSV para Excel, escape de comillas, nombres peligrosos y ausencia de registros. */
public final class PruebaExportacion {
    public static void main(String[] args) throws Exception {
        Path carpeta=Files.createTempDirectory("sigam-csv-");
        Path archivo=carpeta.resolve("productos.csv");
        try {
            ArchivoCsv.guardar(archivo,List.of("Código","Producto","Stock"),List.of(
                List.of("123","Mouse; \"Gamer\"",10),
                List.of("456","=HYPERLINK(\"malo\")",0),
                List.of("789","Descuento",new java.math.BigDecimal("-500.25"))));
            String resultado=Files.readString(archivo,StandardCharsets.UTF_8);
            if(!resultado.startsWith("\ufeff\"Código\";\"Producto\";\"Stock\""))
                throw new AssertionError("BOM/columnas incorrectas");
            if(!resultado.contains("\"Mouse; \"\"Gamer\"\"\""))
                throw new AssertionError("Escape de comillas/separador incorrecto");
            if(!resultado.contains("\"-500,25\""))
                throw new AssertionError("Cálculo negativo convertido a texto o decimal incorrecto");
            if(!resultado.contains("\"'=HYPERLINK"))
                throw new AssertionError("No neutralizó fórmula de Excel");
            try {
                ArchivoCsv.guardar(archivo,List.of("Código"),List.of());
                throw new AssertionError("No rechazó el listado vacío");
            } catch(IllegalArgumentException esperado) { /* correcto */ }
            if(!Files.readString(archivo).equals(resultado))
                throw new AssertionError("Se dañó el archivo existente en una exportación fallida");
            System.out.println("PruebaExportacion OK");
        } finally {Files.deleteIfExists(archivo);Files.deleteIfExists(carpeta);}
    }
}
