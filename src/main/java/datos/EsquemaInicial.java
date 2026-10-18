package ar.com.sigampos.datos;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Instala el esquema actual SOLO en una base NUEVA. No es un migrador. */
public final class EsquemaInicial {
    private EsquemaInicial() { }
    private static final String RECURSO = "/sql/06_esquema_instalacion_v0_9_0.sql";

    public static List<String> instrucciones() throws IOException {
        try (InputStream in = EsquemaInicial.class.getResourceAsStream(RECURSO)) {
            if (in == null) throw new IOException("Falta el recurso de creación de tablas: " + RECURSO);
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            sql = sql.replaceAll("(?m)^--[^\\r\\n]*$", "");
            // DDL sin triggers ni procedimientos: cada CREATE TABLE termina en punto y coma.
            List<String> sentencias = new ArrayList<>();
            for (String parte : sql.split(";")) if (!parte.isBlank()) sentencias.add(parte.trim());
            if (sentencias.size() < 18) throw new IOException("Esquema inicial incompleto");
            return sentencias;
        }
    }

    public static void crear(Connection c) throws SQLException, IOException {
        List<String> comandos = instrucciones();
        try (Statement s = c.createStatement()) {
            s.execute("SET FOREIGN_KEY_CHECKS=0");
            try { for (String instruccion : comandos) s.execute(instruccion); }
            finally { s.execute("SET FOREIGN_KEY_CHECKS=1"); }
        }
        ValidadorEsquema.comprobar(c);
    }
}
