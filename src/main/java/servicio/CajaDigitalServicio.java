package ar.com.sigampos.servicio;

import ar.com.sigampos.datos.Conexion;
import java.math.BigDecimal;
import java.sql.*;

/** Saldo declarado de Mercado Pago, sin APIs externas ni suposiciones bancarias. */
public final class CajaDigitalServicio {
    /** Canal declarado de un movimiento manual: no supone conexión ni acreditación externa. */
    public enum MedioRegistro {
        TRANSFERENCIA("Transferencia normal"), QR("Código QR"), TARJETA("Tarjeta acreditada");
        private final String etiqueta;
        MedioRegistro(String etiqueta){this.etiqueta=etiqueta;}
        @Override public String toString(){return etiqueta;}
    }
    public record Resumen(long id,BigDecimal inicial,BigDecimal cobros,
          BigDecimal entradas,BigDecimal salidas,BigDecimal esperado,String estado) {}

    static void abrirEnTransaccion(Connection c,long cajaId,BigDecimal inicial) throws SQLException {
        validar(inicial,false);
        try(PreparedStatement p=c.prepareStatement(
            "INSERT INTO CajaDigital(caja_id,saldo_inicial) VALUES(?,?)")) {
            p.setLong(1,cajaId);p.setBigDecimal(2,inicial);p.executeUpdate();
        }
    }
    /** Devuelve false cuando el negocio optó por operar sin billetera digital en esa jornada. */
    public boolean habilitada(long cajaId) throws SQLException {
        try(Connection c=Conexion.abrir();PreparedStatement p=c.prepareStatement(
            "SELECT id FROM CajaDigital WHERE caja_id=?")) {
            p.setLong(1,cajaId);try(ResultSet r=p.executeQuery()){return r.next();}
        }
    }
    /** Cobro en misma transacción que venta, detalle, stock y pagos. */
    static void cobroEnTransaccion(Connection c,long cajaId,long empleadoId,long pagoId,
                   String medio,BigDecimal monto) throws SQLException {
        if(!"TRANSFERENCIA".equals(medio) && !"QR".equals(medio))
            throw new IllegalArgumentException("Medio no acreditable automáticamente en Mercado Pago");
        validar(monto,true);
        Long digital=buscarId(c,cajaId);
        if(digital==null) {
            throw new IllegalArgumentException("No hay caja digital abierta. Los cobros por transferencia o QR requieren abrir Mercado Pago para este turno.");
        }
        try(PreparedStatement p=c.prepareStatement(
                "INSERT INTO MovimientoCajaDigital(caja_digital_id,pago_id,empleado_id,tipo,medio_digital,monto,motivo) " +
                "VALUES(?,?,?,?,?,?,?)")) {
            p.setLong(1,digital);p.setLong(2,pagoId);p.setLong(3,empleadoId);
            p.setString(4,"QR".equals(medio)?"COBRO_QR":"COBRO_TRANSFERENCIA");
            p.setString(5,medio);p.setBigDecimal(6,monto);
            p.setString(7,"Cobro digital de venta");p.executeUpdate();
        }
    }
    public static void validarMovimientoManual(BigDecimal monto,String motivo,MedioRegistro medio) {
        validar(monto,true);
        if(motivo==null||motivo.isBlank()||motivo.trim().length()>255)
            throw new IllegalArgumentException("Ingresá un motivo válido (máximo 255 caracteres)");
        if(medio==null)
            throw new IllegalArgumentException("Seleccioná transferencia, QR o tarjeta para el movimiento digital");
    }
    public void registrar(long cajaId,long empleadoId,boolean entrada,BigDecimal monto,
                          String motivo,MedioRegistro medio) throws SQLException {
        validarMovimientoManual(monto,motivo,medio);
        try(Connection c=Conexion.abrir()) {
            c.setAutoCommit(false);
            try {
                VentaServicio.comprobarCaja(c,cajaId,empleadoId);
                Long id=buscarId(c,cajaId);
                if(id==null)throw new IllegalStateException("No hay caja digital abierta para este turno");
                if(!entrada && resumen(c,cajaId).esperado().compareTo(monto)<0)
                    throw new IllegalArgumentException("La salida supera el saldo digital esperado disponible");
                try(PreparedStatement p=c.prepareStatement(
                        "INSERT INTO MovimientoCajaDigital(caja_digital_id,empleado_id,tipo,medio_digital,monto,motivo) VALUES(?,?,?,?,?,?)")) {
                    p.setLong(1,id);p.setLong(2,empleadoId);p.setString(3,entrada?"ENTRADA":"SALIDA");
                    p.setString(4,medio.name());p.setBigDecimal(5,monto);
                    p.setString(6,motivo.trim());p.executeUpdate();
                }
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    private static Long buscarId(Connection c,long cajaId) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(
                "SELECT id FROM CajaDigital WHERE caja_id=? AND estado='ABIERTA' FOR UPDATE")) {
            p.setLong(1,cajaId);try(ResultSet r=p.executeQuery()){return r.next()?r.getLong(1):null;}
        }
    }
    public Resumen resumen(long cajaId) throws SQLException {
        try(Connection c=Conexion.abrir()) {return resumen(c,cajaId);}
    }
    static Resumen resumen(Connection c,long cajaId) throws SQLException {
        String sql="SELECT D.id,D.saldo_inicial,D.estado," +
            "COALESCE(SUM(CASE WHEN M.tipo IN ('COBRO_TRANSFERENCIA','COBRO_QR') THEN M.monto ELSE 0 END),0)," +
            "COALESCE(SUM(CASE WHEN M.tipo='ENTRADA' THEN M.monto ELSE 0 END),0)," +
            "COALESCE(SUM(CASE WHEN M.tipo='SALIDA' THEN M.monto ELSE 0 END),0)" +
            " FROM CajaDigital D LEFT JOIN MovimientoCajaDigital M ON M.caja_digital_id=D.id" +
            " WHERE D.caja_id=? GROUP BY D.id,D.saldo_inicial,D.estado";
        try(PreparedStatement p=c.prepareStatement(sql)) {
            p.setLong(1,cajaId);try(ResultSet r=p.executeQuery()){
                if(!r.next())return null;
                BigDecimal ini=r.getBigDecimal(2),cob=r.getBigDecimal(4),ent=r.getBigDecimal(5),sal=r.getBigDecimal(6);
                return new Resumen(r.getLong(1),ini,cob,ent,sal,ini.add(cob).add(ent).subtract(sal),r.getString(3));
            }
        }
    }
    static void cerrarEnTransaccion(Connection c,long cajaId,long empleadoId,BigDecimal declarado,
                         BigDecimal proximo,String observaciones) throws SQLException {
        validar(declarado,false);validar(proximo,false);
        if(proximo.compareTo(declarado)>0)throw new IllegalArgumentException("El fondo digital del próximo turno supera el declarado");
        Resumen r=resumen(c,cajaId);
        if(r==null)throw new IllegalStateException("No hay caja digital vinculada a la jornada");
        if(!"ABIERTA".equals(r.estado()))throw new IllegalStateException("La caja digital ya fue cerrada");
        try(PreparedStatement p=c.prepareStatement(
            "INSERT INTO ArqueoCajaDigital(caja_digital_id,empleado_id,saldo_esperado,saldo_declarado,diferencia,fondo_proximo_turno,observaciones) " +
            "VALUES(?,?,?,?,?,?,?)")) {
            p.setLong(1,r.id());p.setLong(2,empleadoId);p.setBigDecimal(3,r.esperado());
            p.setBigDecimal(4,declarado);p.setBigDecimal(5,declarado.subtract(r.esperado()));
            p.setBigDecimal(6,proximo);p.setString(7,observaciones);p.executeUpdate();
        }
        try(PreparedStatement p=c.prepareStatement("UPDATE CajaDigital SET estado='CERRADA' WHERE id=? AND estado='ABIERTA'")) {
            p.setLong(1,r.id());if(p.executeUpdate()!=1)throw new SQLException("La caja digital ya estaba cerrada");
        }
    }
    public String historial(long cajaId) throws SQLException {
        StringBuilder sb=new StringBuilder("Movimientos de Mercado Pago (caja #"+cajaId+")\n\n");
        try(Connection c=Conexion.abrir();PreparedStatement p=c.prepareStatement(
            "SELECT M.fecha_hora,M.tipo,M.medio_digital,M.monto,M.motivo,E.nombre FROM MovimientoCajaDigital M"+
            " JOIN CajaDigital D ON D.id=M.caja_digital_id JOIN Empleado E ON E.id=M.empleado_id"+
            " WHERE D.caja_id=? ORDER BY M.id")) {
            p.setLong(1,cajaId);try(ResultSet r=p.executeQuery()) {
                while(r.next())sb.append(r.getTimestamp(1)).append(" | ").append(r.getString(2))
                    .append(" | ").append(r.getString(3)==null?"SIN CLASIFICAR (v0.5)":r.getString(3))
                    .append(" | ").append(ar.com.sigampos.util.Moneda.formato(r.getBigDecimal(4)))
                    .append(" | ").append(r.getString(5)).append(" | ").append(r.getString(6)).append('\n');
            }
        }
        return sb.toString();
    }
    static void validar(BigDecimal n,boolean mayorCero) {
        if(n==null||n.signum()<0||n.scale()>2||(mayorCero&&n.signum()==0))
            throw new IllegalArgumentException("Importe inválido (máximo dos decimales)");
    }
}
