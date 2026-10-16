package ar.com.sigampos.servicio;
import ar.com.sigampos.datos.Conexion;
import java.sql.*;
import java.util.*;
/** Alertas persistentes por empleado. Descartar vale hasta que cambie stock o nivel. */
public final class AlertasServicio {
    public record Alerta(String codigo,String nombre,int stock,Integer minimo,String nivel) {}
    public List<Alerta> pendientes(long empleadoId) throws SQLException {
        List<Alerta> lista=new ArrayList<>();
        String sql="SELECT P.codigo,P.nombre,P.stock,P.stock_minimo,N.stock_registrado,N.tipo " +
            "FROM Producto P LEFT JOIN NotificacionStockDescartada N " +
            "ON N.producto_codigo=P.codigo AND N.empleado_id=? " +
            "WHERE P.estado='ACTIVO' ORDER BY P.nombre";
        try(Connection c=Conexion.abrir();PreparedStatement p=c.prepareStatement(sql)) {
            p.setLong(1,empleadoId);
            try(ResultSet r=p.executeQuery()) {
                while(r.next()) {
                    int stock=r.getInt(3);Object m=r.getObject(4);
                    Integer minimo=m==null?null:((Number)m).intValue();
                    String nivel=clasificar(stock,minimo);
                    if(nivel==null)continue;
                    Object descartado=r.getObject(5);
                    if(descartado!=null && ((Number)descartado).intValue()==stock && nivel.equals(r.getString(6)))continue;
                    lista.add(new Alerta(r.getString(1),r.getString(2),stock,minimo,nivel));
                }
            }
        }
        lista.sort(Comparator.comparingInt((Alerta a)->switch(a.nivel()){case "SIN_STOCK"->0;case "BAJO"->1;default->2;})
            .thenComparing(Alerta::nombre));
        return lista;
    }
    public static String clasificar(int stock,Integer minimo){
        if(stock<=0)return "SIN_STOCK";
        if(minimo==null)return null;
        if(stock<=minimo)return "BAJO";
        int margen=Math.max(2,(minimo+4)/5);
        if(stock<=minimo+margen)return "PROXIMO";
        return null;
    }
    public void descartar(long empleadoId,Alerta alerta) throws SQLException {
        try(Connection c=Conexion.abrir();PreparedStatement p=c.prepareStatement(
            "INSERT INTO NotificacionStockDescartada(empleado_id,producto_codigo,stock_registrado,tipo)"+
            " VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE stock_registrado=VALUES(stock_registrado),tipo=VALUES(tipo)")) {
            p.setLong(1,empleadoId);p.setString(2,alerta.codigo());p.setInt(3,alerta.stock());p.setString(4,alerta.nivel());
            p.executeUpdate();
        }
    }
}
