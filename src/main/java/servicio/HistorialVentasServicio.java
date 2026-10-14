package ar.com.sigampos.servicio;

import ar.com.sigampos.datos.Conexion;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Consulta histórica de ventas sin duplicar ventas mixtas.
 * Cada Venta se cuenta una sola vez; el detalle de medios viene de Pago.
 */
public final class HistorialVentasServicio {
    public record VentaResumen(long id, long cajaId, String empleado,
                               Timestamp fechaHora, BigDecimal total) {}
    public record PagoDetalle(long id, String medio, BigDecimal monto) {}
    public record ProductoDetalle(String codigo, int cantidad,
                                  BigDecimal unitario, BigDecimal subtotal) {}
    public record Detalle(long ventaId, List<PagoDetalle> pagos,
                          List<ProductoDetalle> productos) {}
    public record Recaudacion(String medio, int pagos, BigDecimal importe) {}

    public List<VentaResumen> listar(LocalDate desde, LocalDate hasta,
                                    Long empleadoId, String medio) throws SQLException {
        if (desde==null || hasta==null || hasta.isBefore(desde))
            throw new IllegalArgumentException("El rango de fechas es inválido");
        if (medio!=null && !medio.isBlank() &&
            !List.of("EFECTIVO","TRANSFERENCIA","QR","TARJETA").contains(medio))
            throw new IllegalArgumentException("Medio de pago inválido");
        String sql="SELECT V.id,V.caja_id,E.nombre,V.fecha_hora,V.total " +
            "FROM Venta V JOIN Empleado E ON E.id=V.empleado_id " +
            "WHERE V.fecha_hora>=? AND V.fecha_hora<? " +
            "AND (? IS NULL OR V.empleado_id=?) " +
            "AND (? IS NULL OR EXISTS (SELECT 1 FROM Pago P WHERE P.venta_id=V.id AND P.medio_pago=?)) " +
            "ORDER BY V.fecha_hora DESC,V.id DESC";
        List<VentaResumen> datos=new ArrayList<>();
        try(Connection c=Conexion.abrir();PreparedStatement p=c.prepareStatement(sql)) {
            p.setTimestamp(1,Timestamp.valueOf(desde.atStartOfDay()));
            p.setTimestamp(2,Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
            if(empleadoId==null){p.setNull(3,Types.BIGINT);p.setNull(4,Types.BIGINT);}
            else {p.setLong(3,empleadoId);p.setLong(4,empleadoId);}
            if(medio==null || medio.isBlank()) {p.setNull(5,Types.VARCHAR);p.setNull(6,Types.VARCHAR);}
            else {p.setString(5,medio);p.setString(6,medio);}
            try(ResultSet r=p.executeQuery()){
                while(r.next())datos.add(new VentaResumen(r.getLong(1),r.getLong(2),r.getString(3),
                    r.getTimestamp(4),r.getBigDecimal(5)));
            }
        }
        return datos;
    }

    public Detalle detalle(long ventaId) throws SQLException {
        List<PagoDetalle> pagos=new ArrayList<>();
        List<ProductoDetalle> productos=new ArrayList<>();
        try(Connection c=Conexion.abrir()) {
            try(PreparedStatement p=c.prepareStatement(
                "SELECT P.id,P.medio_pago,P.monto FROM Pago P WHERE P.venta_id=? ORDER BY P.id")) {
                p.setLong(1,ventaId);
                try(ResultSet r=p.executeQuery()) {
                    while(r.next())pagos.add(new PagoDetalle(r.getLong(1),r.getString(2),r.getBigDecimal(3)));
                }
            }
            try(PreparedStatement p=c.prepareStatement(
                "SELECT D.producto_codigo,D.cantidad,D.precio_unitario, " +
                "(D.cantidad*D.precio_unitario) FROM DetalleVenta D WHERE D.venta_id=? ORDER BY D.id")) {
                p.setLong(1,ventaId);
                try(ResultSet r=p.executeQuery()) {
                    while(r.next())productos.add(new ProductoDetalle(r.getString(1),r.getInt(2),
                        r.getBigDecimal(3),r.getBigDecimal(4)));
                }
            }
        }
        if(pagos.isEmpty() && productos.isEmpty())
            throw new IllegalArgumentException("Venta inexistente: "+ventaId);
        return new Detalle(ventaId,List.copyOf(pagos),List.copyOf(productos));
    }

    /** Agrupa pagos, no ventas. Una operación con dos medios suma en ambos conceptos. */
    public List<Recaudacion> recaudacion(LocalDate desde,LocalDate hasta) throws SQLException {
        if(desde==null || hasta==null || hasta.isBefore(desde))
            throw new IllegalArgumentException("El rango de fechas es inválido");
        List<Recaudacion> datos=new ArrayList<>();
        try(Connection c=Conexion.abrir();PreparedStatement p=c.prepareStatement(
            "SELECT P.medio_pago,COUNT(P.id),COALESCE(SUM(P.monto),0) " +
            "FROM Pago P JOIN Venta V ON V.id=P.venta_id " +
            "WHERE V.fecha_hora>=? AND V.fecha_hora<? GROUP BY P.medio_pago ORDER BY P.medio_pago")) {
            p.setTimestamp(1,Timestamp.valueOf(desde.atStartOfDay()));
            p.setTimestamp(2,Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
            try(ResultSet r=p.executeQuery()) {
                while(r.next())datos.add(new Recaudacion(r.getString(1),r.getInt(2),r.getBigDecimal(3)));
            }
        }
        return datos;
    }
}
