package ar.com.sigampos.servicio;

import ar.com.sigampos.datos.Conexion;
import java.math.BigDecimal;
import java.sql.*;

/**
 * Caja digital declarativa de Mercado Pago. No utiliza API externa.
 * Los cobros digitales se registran una sola vez por pago para evitar duplicados.
 * Requiere ejecutar sql/03_evolucion_v0_5_0.sql.
 */
public final class CajaDigitalServicio {
    public record Resumen(long id,String nombre,BigDecimal inicial,BigDecimal cobros,
                          BigDecimal entradas,BigDecimal salidas,BigDecimal esperado,
                          String estado) {}

    /** Inserción dentro de la misma transacción que abre Caja/JornadaLaboral. */
    static void abrirEnTransaccion(Connection c,long cajaId,BigDecimal inicial) throws SQLException {
        validar(inicial,false);
        try(PreparedStatement p=c.prepareStatement(
                "INSERT INTO CajaDigital(caja_id,nombre,saldo_inicial) VALUES(?,'Mercado Pago',?)")) {
            p.setLong(1,cajaId);p.setBigDecimal(2,inicial);p.executeUpdate();
        }
    }
    static void validar(BigDecimal n,boolean mayorCero) {
        validarNoNegativo(n,"Importe");
        if(mayorCero && n.signum()==0)throw new IllegalArgumentException("El importe debe ser mayor que cero");
    }
    public boolean habilitada(long cajaId) throws SQLException {
        try(Connection c=Conexion.abrir();PreparedStatement p=c.prepareStatement(
                "SELECT id FROM CajaDigital WHERE caja_id=?")) {
            p.setLong(1,cajaId);try(ResultSet r=p.executeQuery()){return r.next();}
        }
    }
    static void cobroEnTransaccion(Connection c,long cajaId,long empleadoId,long pagoId,
                                   String medio,BigDecimal monto) throws SQLException {
        new CajaDigitalServicio().registrarCobroEnVenta(c,cajaId,empleadoId,pagoId,medio,monto);
    }
    public String historial(long cajaId) throws SQLException {
        StringBuilder sb=new StringBuilder("Movimientos de Mercado Pago (Caja #"+cajaId+")\n\n");
        try(Connection c=Conexion.abrir();PreparedStatement p=c.prepareStatement(
            "SELECT M.fecha_hora,M.tipo,M.monto,M.motivo,E.nombre FROM MovimientoCajaDigital M " +
            "JOIN CajaDigital D ON D.id=M.caja_digital_id JOIN Empleado E ON E.id=M.empleado_id " +
            "WHERE D.caja_id=? ORDER BY M.id")) {
            p.setLong(1,cajaId);
            try(ResultSet r=p.executeQuery()) {
                while(r.next())sb.append(r.getTimestamp(1)).append(" | ").append(r.getString(2))
                    .append(" | ").append(ar.com.sigampos.util.Moneda.formato(r.getBigDecimal(3)))
                    .append(" | ").append(r.getString(4)).append(" | ").append(r.getString(5)).append('\n');
            }
        }
        return sb.toString();
    }
    public long abrir(long cajaId,long empleadoId,BigDecimal saldoInicial) throws SQLException {
        validarNoNegativo(saldoInicial,"Saldo inicial");
        try(Connection c=Conexion.abrir()) {
            c.setAutoCommit(false);
            try {
                VentaServicio.comprobarCaja(c,cajaId,empleadoId);
                try(PreparedStatement p=c.prepareStatement(
                    "INSERT INTO CajaDigital(caja_id,nombre,saldo_inicial) VALUES(?,'Mercado Pago',?)",
                    Statement.RETURN_GENERATED_KEYS)){
                    p.setLong(1,cajaId);p.setBigDecimal(2,saldoInicial);p.executeUpdate();
                    long id=VentaServicio.idGenerado(p);
                    c.commit();return id;
                }
            } catch(SQLException | RuntimeException e) {c.rollback();throw e;}
        }
    }

    /**
     * Se invoca desde la MISMA transacción que inserta Pago en VentaServicio.
     * El llamador es responsable del commit y del rollback.
     */
    public void registrarCobroEnVenta(Connection c,long cajaId,long empleadoId,
                                      long pagoId,String medio,BigDecimal monto) throws SQLException {
        if(!"TRANSFERENCIA".equals(medio) && !"QR".equals(medio))
            throw new IllegalArgumentException("Solo transferencias y QR pueden acreditarse a Mercado Pago");
        validarPositivo(monto,"Cobro");
        VentaServicio.comprobarCaja(c,cajaId,empleadoId);
        long digitalId=idAbierta(c,cajaId);
        try(PreparedStatement p=c.prepareStatement(
            "INSERT INTO MovimientoCajaDigital(caja_digital_id,pago_id,empleado_id,tipo,monto,motivo) " +
            "VALUES(?,?,?, ?,?,?)")){
            p.setLong(1,digitalId);p.setLong(2,pagoId);p.setLong(3,empleadoId);
            p.setString(4,"QR".equals(medio)?"COBRO_QR":"COBRO_TRANSFERENCIA");
            p.setBigDecimal(5,monto);p.setString(6,"Pago digital de venta #"+pagoId);
            p.executeUpdate();
        }
    }

    public void registrarMovimientoManual(long cajaId,long empleadoId,boolean entrada,
                                           BigDecimal monto,String motivo) throws SQLException {
        validarPositivo(monto,"Movimiento");
        if(motivo==null || motivo.isBlank() || motivo.length()>255)
            throw new IllegalArgumentException("Indicá un motivo válido (máximo 255 caracteres)");
        try(Connection c=Conexion.abrir()) {
            c.setAutoCommit(false);
            try {
                VentaServicio.comprobarCaja(c,cajaId,empleadoId);
                long id=idAbierta(c,cajaId);
                if(!entrada && resumen(c,cajaId).esperado().compareTo(monto)<0)
                    throw new IllegalArgumentException("Saldo declarado de Mercado Pago insuficiente para la salida");
                try(PreparedStatement p=c.prepareStatement(
                    "INSERT INTO MovimientoCajaDigital(caja_digital_id,empleado_id,tipo,monto,motivo) " +
                    "VALUES(?,?,?,?,?)")){
                    p.setLong(1,id);p.setLong(2,empleadoId);p.setString(3,entrada?"ENTRADA":"SALIDA");
                    p.setBigDecimal(4,monto);p.setString(5,motivo.trim());p.executeUpdate();
                }
                c.commit();
            } catch(SQLException | RuntimeException e) {c.rollback();throw e;}
        }
    }

    public Resumen resumen(long cajaId) throws SQLException {
        try(Connection c=Conexion.abrir()) {return resumen(c,cajaId);}
    }
    static Resumen resumen(Connection c,long cajaId) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(
            "SELECT D.id,D.nombre,D.saldo_inicial,D.estado," +
            "COALESCE(SUM(CASE WHEN M.tipo IN ('COBRO_TRANSFERENCIA','COBRO_QR') THEN M.monto ELSE 0 END),0)," +
            "COALESCE(SUM(CASE WHEN M.tipo='ENTRADA' THEN M.monto ELSE 0 END),0)," +
            "COALESCE(SUM(CASE WHEN M.tipo='SALIDA' THEN M.monto ELSE 0 END),0) " +
            "FROM CajaDigital D LEFT JOIN MovimientoCajaDigital M ON M.caja_digital_id=D.id " +
            "WHERE D.caja_id=? GROUP BY D.id,D.nombre,D.saldo_inicial,D.estado")) {
            p.setLong(1,cajaId);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next())return null;
                BigDecimal inicial=r.getBigDecimal(3),cobros=r.getBigDecimal(5),
                    entradas=r.getBigDecimal(6),salidas=r.getBigDecimal(7);
                return new Resumen(r.getLong(1),r.getString(2),inicial,cobros,entradas,
                    salidas,inicial.add(cobros).add(entradas).subtract(salidas),r.getString(4));
            }
        }
    }

    /**
     * Cierre pensado para invocarse desde la transacción de cierre de CajaServicio,
     * garantizando que caja física y digital se cierren o fallen conjuntamente.
     */
    static void cerrarEnTransaccion(Connection c,long cajaId,long empleadoId,
                             BigDecimal declarado,BigDecimal proximo,String observaciones) throws SQLException {
        validarNoNegativo(declarado,"Saldo declarado");
        validarNoNegativo(proximo,"Fondo digital próximo turno");
        if(proximo.compareTo(declarado)>0)
            throw new IllegalArgumentException("El fondo digital próximo no puede superar el saldo declarado");
        if(observaciones!=null && observaciones.length()>500)
            throw new IllegalArgumentException("Observaciones demasiado extensas");
        VentaServicio.comprobarCaja(c,cajaId,empleadoId);
        long digitalId=idAbierta(c,cajaId);
        BigDecimal inicial,cobros,entradas,salidas;
        try(PreparedStatement p=c.prepareStatement(
            "SELECT D.saldo_inicial," +
            "COALESCE(SUM(CASE WHEN M.tipo IN ('COBRO_TRANSFERENCIA','COBRO_QR') THEN M.monto ELSE 0 END),0)," +
            "COALESCE(SUM(CASE WHEN M.tipo='ENTRADA' THEN M.monto ELSE 0 END),0)," +
            "COALESCE(SUM(CASE WHEN M.tipo='SALIDA' THEN M.monto ELSE 0 END),0) " +
            "FROM CajaDigital D LEFT JOIN MovimientoCajaDigital M ON M.caja_digital_id=D.id " +
            "WHERE D.id=? GROUP BY D.id,D.saldo_inicial")){
            p.setLong(1,digitalId);
            try(ResultSet r=p.executeQuery()){
                if(!r.next())throw new SQLException("Caja digital no encontrada");
                inicial=r.getBigDecimal(1);cobros=r.getBigDecimal(2);
                entradas=r.getBigDecimal(3);salidas=r.getBigDecimal(4);
            }
        }
        BigDecimal esperado=inicial.add(cobros).add(entradas).subtract(salidas);
        try(PreparedStatement p=c.prepareStatement(
            "INSERT INTO ArqueoCajaDigital(caja_digital_id,empleado_id,saldo_esperado,saldo_declarado," +
            "diferencia,fondo_proximo_turno,observaciones) VALUES(?,?,?,?,?,?,?)")){
            p.setLong(1,digitalId);p.setLong(2,empleadoId);p.setBigDecimal(3,esperado);
            p.setBigDecimal(4,declarado);p.setBigDecimal(5,declarado.subtract(esperado));
            p.setBigDecimal(6,proximo);p.setString(7,observaciones);p.executeUpdate();
        }
        try(PreparedStatement p=c.prepareStatement(
            "UPDATE CajaDigital SET estado='CERRADA' WHERE id=? AND estado='ABIERTA'")){
            p.setLong(1,digitalId);
            if(p.executeUpdate()!=1)throw new SQLException("La caja digital ya estaba cerrada");
        }
    }

    private static long idAbierta(Connection c,long cajaId) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(
            "SELECT id FROM CajaDigital WHERE caja_id=? AND estado='ABIERTA' FOR UPDATE")){
            p.setLong(1,cajaId);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next())throw new IllegalStateException("No hay caja digital abierta para Mercado Pago");
                return r.getLong(1);
            }
        }
    }
    private static void validarPositivo(BigDecimal n,String campo){
        validarNoNegativo(n,campo);
        if(n.signum()==0)throw new IllegalArgumentException(campo+" debe ser mayor que cero");
    }
    private static void validarNoNegativo(BigDecimal n,String campo){
        if(n==null || n.signum()<0 || n.scale()>2)
            throw new IllegalArgumentException(campo+" inválido");
    }
}
