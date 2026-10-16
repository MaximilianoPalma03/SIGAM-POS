package ar.com.sigampos.servicio;
import ar.com.sigampos.datos.Conexion;
import java.sql.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
/** Recuento independiente de Venta y Pago: una venta mixta no se duplica. */
public final class HistorialVentasServicio {
    public record VentaResumen(long id,long cajaId,String empleado,Timestamp fecha,BigDecimal total) {}
    public record PagoDetalle(String medio,BigDecimal monto) {}
    public record Recaudacion(String medio,long operaciones,BigDecimal monto) {}
    public List<VentaResumen> listar(LocalDate desde,LocalDate hasta,String medio) throws SQLException {
        if(desde==null||hasta==null||desde.isAfter(hasta))throw new IllegalArgumentException("Rango de fechas inválido");
        String sql="SELECT V.id,V.caja_id,E.nombre,V.fecha_hora,V.total"+
            " FROM Venta V JOIN Empleado E ON E.id=V.empleado_id"+
            " WHERE V.fecha_hora>=? AND V.fecha_hora<?"+
            " AND (? IS NULL OR EXISTS(SELECT 1 FROM Pago P WHERE P.venta_id=V.id AND P.medio_pago=?))"+
            " ORDER BY V.fecha_hora DESC,V.id DESC";
        List<VentaResumen> lista=new ArrayList<>();
        try(Connection c=Conexion.abrir();PreparedStatement p=c.prepareStatement(sql)) {
            p.setTimestamp(1,Timestamp.valueOf(desde.atStartOfDay()));
            p.setTimestamp(2,Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
            if(medio==null||medio.equals("TODOS")){p.setNull(3,Types.VARCHAR);p.setNull(4,Types.VARCHAR);}
            else{p.setString(3,medio);p.setString(4,medio);}
            try(ResultSet r=p.executeQuery()){
                while(r.next())lista.add(new VentaResumen(r.getLong(1),r.getLong(2),r.getString(3),r.getTimestamp(4),r.getBigDecimal(5)));
            }
        }
        return lista;
    }
    public List<PagoDetalle> pagos(long ventaId) throws SQLException {
        List<PagoDetalle> lista=new ArrayList<>();
        try(Connection c=Conexion.abrir();PreparedStatement p=c.prepareStatement(
                "SELECT medio_pago,monto FROM Pago WHERE venta_id=? ORDER BY id")) {
            p.setLong(1,ventaId);try(ResultSet r=p.executeQuery()){
                while(r.next())lista.add(new PagoDetalle(r.getString(1),r.getBigDecimal(2)));
            }
        }
        return lista;
    }
    public String detalle(long ventaId) throws SQLException {
        StringBuilder texto=new StringBuilder("VENTA #"+ventaId+"\n\nARTÍCULOS\n");
        try(Connection c=Conexion.abrir();PreparedStatement p=c.prepareStatement(
            "SELECT D.producto_codigo,P.nombre,D.cantidad,D.precio_unitario"+
            " FROM DetalleVenta D JOIN Producto P ON P.codigo=D.producto_codigo WHERE D.venta_id=?")) {
            p.setLong(1,ventaId);try(ResultSet r=p.executeQuery()) {
                while(r.next())texto.append(r.getString(1)).append(" · ").append(r.getString(2))
                    .append(" × ").append(r.getInt(3)).append(" · ")
                    .append(ar.com.sigampos.util.Moneda.formato(r.getBigDecimal(4))).append('\n');
            }
        }
        texto.append("\nMEDIOS DE PAGO\n");
        for(PagoDetalle pago:pagos(ventaId))texto.append(pago.medio()).append(" · ")
            .append(ar.com.sigampos.util.Moneda.formato(pago.monto())).append('\n');
        return texto.toString();
    }
    public List<Recaudacion> recaudacion(LocalDate desde,LocalDate hasta) throws SQLException {
        if(desde==null||hasta==null||desde.isAfter(hasta))throw new IllegalArgumentException("Rango de fechas inválido");
        List<Recaudacion> lista=new ArrayList<>();
        try(Connection c=Conexion.abrir();PreparedStatement p=c.prepareStatement(
            "SELECT P.medio_pago,COUNT(*),COALESCE(SUM(P.monto),0)"+
            " FROM Pago P JOIN Venta V ON V.id=P.venta_id WHERE V.fecha_hora>=? AND V.fecha_hora<?"+
            " GROUP BY P.medio_pago ORDER BY P.medio_pago")) {
            p.setTimestamp(1,Timestamp.valueOf(desde.atStartOfDay()));
            p.setTimestamp(2,Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
            try(ResultSet r=p.executeQuery()) {
                while(r.next())lista.add(new Recaudacion(r.getString(1),r.getLong(2),r.getBigDecimal(3)));
            }
        }
        return lista;
    }
}
