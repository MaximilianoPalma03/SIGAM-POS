package ar.com.sigampos;

import ar.com.sigampos.util.ArchivoCsv;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.Timestamp;
import java.util.*;

public final class PruebaFormatoCsv {
    public static void main(String[] args) throws Exception {
        Path archivo=Files.createTempFile("sigam-formato-", ".csv");
        try {
            ArchivoCsv.guardar(archivo,List.of("Fecha","Total","Saldo","Concepto"),List.of(
                List.of(Timestamp.valueOf("2026-10-16 18:12:50.0"),new BigDecimal("5000.00"),
                    new BigDecimal("-200.50"),"Venta mixta")));
            String csv=Files.readString(archivo);
            if(!csv.contains("\"2026-10-16 18:12:50\";\"5000,00\";\"-200,50\""))
                throw new AssertionError("Fecha e importes argentinos no uniformes: "+csv);
            System.out.println("PruebaFormatoCsv OK");
        } finally {Files.deleteIfExists(archivo);}
    }
}
