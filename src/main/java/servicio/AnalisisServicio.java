package ar.com.sigampos.servicio;
import ar.com.sigampos.datos.Conexion;
import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class AnalisisServicio {
    public record Resumen(long cantidadVentas,BigDecimal montoTotal,BigDecimal promedio) { }
    public Resumen resumen(LocalDate desde,LocalDate hasta)throws SQLException {
        if(hasta.isBefore(desde))throw new IllegalArgumentException("Rango de fechas inválido");
        String sql="SELECT COUNT(*) cantidad,COALESCE(SUM(total),0) total,COALESCE(AVG(total),0) promedio FROM Venta WHERE fecha_hora >= ? AND fecha_hora < ?";
        try(Connection c=Conexion.abrir();PreparedStatement ps=c.prepareStatement(sql)){
            ps.setTimestamp(1,Timestamp.valueOf(desde.atStartOfDay()));ps.setTimestamp(2,Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
            try(ResultSet rs=ps.executeQuery()){rs.next();return new Resumen(rs.getLong(1),rs.getBigDecimal(2),rs.getBigDecimal(3));}
        }
    }
}
