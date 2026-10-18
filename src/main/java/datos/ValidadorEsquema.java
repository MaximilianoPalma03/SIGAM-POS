package ar.com.sigampos.datos;

import java.sql.*;
import java.util.*;

/** Valida SOLO metadatos. Nunca crea, altera o borra tablas existentes. */
public final class ValidadorEsquema {
    private ValidadorEsquema() { }

    private static final Map<String, Set<String>> OBLIGATORIAS = crearRequisitos();

    private static Map<String, Set<String>> crearRequisitos() {
        Map<String, Set<String>> r = new LinkedHashMap<>();
        r.put("empleado", campos("id", "nombre", "dni", "estado"));
        r.put("usuario", campos("id", "empleado_id", "nombre_usuario", "contrasena_hash", "rol", "debe_cambiar_clave"));
        r.put("turno", campos("id", "nombre", "hora_inicio", "hora_fin", "lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo", "estado"));
        r.put("empleadoturno", campos("empleado_id", "turno_id"));
        r.put("jornadalaboral", campos("id", "empleado_id", "turno_id", "inicio_real", "fin_real", "estado"));
        r.put("caja", campos("id", "turno_id", "jornada_id", "fondo_inicial", "estado"));
        r.put("cajadigital", campos("id", "caja_id", "saldo_inicial", "estado"));
        r.put("arqueocaja", campos("id", "caja_id", "efectivo_esperado", "efectivo_contado", "diferencia"));
        r.put("arqueocajadigital", campos("id", "caja_digital_id", "saldo_esperado", "saldo_declarado", "diferencia"));
        r.put("producto", campos("codigo", "nombre", "precio_venta", "costo", "stock", "estado", "stock_minimo", "stock_maximo"));
        r.put("historialprecio", campos("id", "producto_codigo", "precio", "fecha_cambio"));
        r.put("venta", campos("id", "caja_id", "empleado_id", "fecha_hora", "total"));
        r.put("detalleventa", campos("id", "venta_id", "producto_codigo", "cantidad", "precio_unitario", "costo_unitario"));
        r.put("pago", campos("id", "venta_id", "medio_pago", "monto"));
        r.put("movimientocaja", campos("id", "caja_id", "empleado_id", "tipo", "monto"));
        r.put("movimientocajadigital", campos("id", "caja_digital_id", "empleado_id", "tipo", "medio_digital", "monto"));
        r.put("notificacionstockdescartada", campos("empleado_id", "producto_codigo", "stock_registrado", "tipo"));
        return Collections.unmodifiableMap(r);
    }

    private static Set<String> campos(String... nombres) { return Set.of(nombres); }
    public static int cantidadTablas() { return OBLIGATORIAS.size(); }

    /** Devuelve las incompatibilidades sin modificar la BD. */
    public static List<String> diferencias(Map<String, ? extends Set<String>> esquema) {
        List<String> diferencias = new ArrayList<>();
        for (var entrada : OBLIGATORIAS.entrySet()) {
            Set<String> presentes = esquema.get(entrada.getKey());
            if (presentes == null) {
                diferencias.add("Falta la tabla " + entrada.getKey());
            } else {
                for (String columna : entrada.getValue()) {
                    if (!presentes.contains(columna))
                        diferencias.add("Falta " + entrada.getKey() + "." + columna);
                }
            }
        }
        return diferencias;
    }

    public static void comprobar(Connection conexion) throws SQLException {
        String producto = conexion.getMetaData().getDatabaseProductName();
        int major = conexion.getMetaData().getDatabaseMajorVersion();
        int minor = conexion.getMetaData().getDatabaseMinorVersion();
        if (!producto.toLowerCase(Locale.ROOT).contains("mysql") || major < 8) {
            throw new SQLException("SIGAM-POS requiere MySQL 8.0.16 o superior. Se detectó: "+producto+
                " "+major+"."+minor+". No se modificó la base.");
        }
        Map<String, Set<String>> tablas = new HashMap<>();
        String catalogo = conexion.getCatalog();
        try (PreparedStatement p = conexion.prepareStatement(
                "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = ?")) {
            p.setString(1, catalogo);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    tablas.computeIfAbsent(r.getString(1).toLowerCase(Locale.ROOT),
                        k -> new HashSet<>()).add(r.getString(2).toLowerCase(Locale.ROOT));
                }
            }
        }
        List<String> errores = diferencias(tablas);
        // Comprobaciones adicionales: relaciones fundamentales, no solo nombres coincidentes.
        Set<String> clavesForaneas = new HashSet<>();
        try (PreparedStatement p = conexion.prepareStatement(
                "SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME " +
                "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                "WHERE TABLE_SCHEMA=? AND REFERENCED_TABLE_NAME IS NOT NULL")) {
            p.setString(1, catalogo);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) clavesForaneas.add((r.getString(1)+"."+r.getString(2)+"->"+
                    r.getString(3)+"."+r.getString(4)).toLowerCase(Locale.ROOT));
            }
        }
        for (String requerida : Set.of(
            "venta.caja_id->caja.id", "venta.empleado_id->empleado.id",
            "detalleventa.venta_id->venta.id", "pago.venta_id->venta.id",
            "movimientocaja.caja_id->caja.id", "cajadigital.caja_id->caja.id",
            "movimientocajadigital.caja_digital_id->cajadigital.id")) {
            if (!clavesForaneas.contains(requerida)) errores.add("Falta la relación "+requerida);
        }
        try (PreparedStatement p = conexion.prepareStatement(
                "SELECT TABLE_NAME, ENGINE FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=?")) {
            p.setString(1, catalogo);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    if (OBLIGATORIAS.containsKey(r.getString(1).toLowerCase(Locale.ROOT)) &&
                        !"InnoDB".equalsIgnoreCase(r.getString(2)))
                        errores.add("Se esperaba InnoDB en "+r.getString(1));
                }
            }
        }
        if (tablas.containsKey("sigam_version_esquema")) {
            if (!tablas.get("sigam_version_esquema").contains("version")) {
                errores.add("Tabla sigam_version_esquema sin campo version");
            } else {
                try (Statement s=conexion.createStatement();
                     ResultSet r=s.executeQuery("SELECT version FROM sigam_version_esquema")) {
                    if (!r.next() || !"0.9.0".equals(r.getString(1)) || r.next())
                        errores.add("Versión de esquema no compatible (se requiere 0.9.0)");
                }
            }
        }
        if (!errores.isEmpty()) {
            throw new SQLException("Se encontró la base '" + catalogo + "', pero su esquema no es compatible: " +
                String.join("; ", errores.subList(0, Math.min(errores.size(), 8))) +
                ". No se modificaron tablas ni registros.");
        }
    }
}
