package ar.com.sigampos.servicio;
import ar.com.sigampos.datos.Conexion;
import java.sql.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
/** Historial de cajas/jornadas, con agregados independientes por concepto. */
public final class JornadasServicio {
    public record Jornada(long cajaId,String empleado,String turno,Timestamp inicio,Timestamp fin,
        String estado,BigDecimal fondo,BigDecimal efectivo,BigDecimal digital,
        BigDecimal entradas,BigDecimal salidas,BigDecimal diferencia) {}
    public List<Jornada> listar(LocalDate desde,LocalDate hasta,Long empleadoId,String estado) throws SQLException {
        if(desde==null || hasta==null || desde.isAfter(hasta))throw new IllegalArgumentException("Fechas inválidas");
        String sql="SELECT C.id,E.nombre,T.nombre,J.inicio_real,J.fin_real,C.estado,C.fondo_inicial,"+
            "COALESCE((SELECT SUM(M.monto) FROM MovimientoCaja M WHERE M.caja_id=C.id AND M.tipo='INGRESO_VENTA'),0),"+
            "COALESCE((SELECT SUM(MD.monto) FROM CajaDigital D JOIN MovimientoCajaDigital MD " +
            "ON MD.caja_digital_id=D.id WHERE D.caja_id=C.id " +
            "AND MD.tipo IN ('COBRO_TRANSFERENCIA','COBRO_QR')),0),"+
            "COALESCE((SELECT SUM(M.monto) FROM MovimientoCaja M WHERE M.caja_id=C.id AND M.tipo='ENTRADA'),0),"+
            "COALESCE((SELECT SUM(M.monto) FROM MovimientoCaja M WHERE M.caja_id=C.id AND M.tipo='SALIDA'),0),"+
            "A.diferencia FROM Caja C JOIN JornadaLaboral J ON J.id=C.jornada_id " +
            "JOIN Empleado E ON E.id=J.empleado_id JOIN Turno T ON T.id=J.turno_id " +
            "LEFT JOIN ArqueoCaja A ON A.caja_id=C.id " +
            "WHERE J.inicio_real>=? AND J.inicio_real<? " +
            "AND (? IS NULL OR E.id=?) AND (? IS NULL OR C.estado=?) ORDER BY C.id DESC";
        List<Jornada> filas=new ArrayList<>();
        try(Connection c=Conexion.abrir();PreparedStatement p=c.prepareStatement(sql)) {
            p.setTimestamp(1,Timestamp.valueOf(desde.atStartOfDay()));
            p.setTimestamp(2,Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
            if(empleadoId==null){p.setNull(3,Types.BIGINT);p.setNull(4,Types.BIGINT);}
            else{p.setLong(3,empleadoId);p.setLong(4,empleadoId);}
            if(estado==null||estado.isBlank()){p.setNull(5,Types.VARCHAR);p.setNull(6,Types.VARCHAR);}
            else{p.setString(5,estado);p.setString(6,estado);}
            try(ResultSet r=p.executeQuery()){
                while(r.next())filas.add(new Jornada(r.getLong(1),r.getString(2),r.getString(3),
                    r.getTimestamp(4),r.getTimestamp(5),r.getString(6),r.getBigDecimal(7),
                    r.getBigDecimal(8),r.getBigDecimal(9),r.getBigDecimal(10),r.getBigDecimal(11),r.getBigDecimal(12)));
            }
        }
        return filas;
    }
}
