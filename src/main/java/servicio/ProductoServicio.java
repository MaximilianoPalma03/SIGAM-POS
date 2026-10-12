package ar.com.sigampos.servicio;
import ar.com.sigampos.datos.Conexion;
import ar.com.sigampos.modelo.Producto;
import java.sql.*;
import java.util.*;

public final class ProductoServicio {
    public List<Producto> listar(String filtro) throws SQLException {
        String sql = "SELECT * FROM Producto WHERE codigo LIKE ? OR nombre LIKE ? ORDER BY nombre";
        List<Producto> encontrados = new ArrayList<>();
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            String q = "%" + (filtro == null ? "" : filtro.trim()) + "%";
            ps.setString(1, q); ps.setString(2, q);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) encontrados.add(desde(rs)); }
        }
        return encontrados;
    }
    public Producto buscar(Connection c, String codigo, boolean bloquear) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM Producto WHERE codigo = ?" + (bloquear ? " FOR UPDATE" : ""))) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? desde(rs) : null; }
        }
    }
    public void guardar(Producto p) throws SQLException {
        try (Connection c = Conexion.abrir()) {
            c.setAutoCommit(false);
            try {
                Producto anterior = buscar(c, p.codigo(), true);
                String sql = anterior == null ?
                        "INSERT INTO Producto(codigo,nombre,precio_venta,costo,stock,stock_minimo,stock_maximo,estado) VALUES(?,?,?,?,?,?,?,?)" :
                        "UPDATE Producto SET nombre=?,precio_venta=?,costo=?,stock=?,stock_minimo=?,stock_maximo=?,estado=? WHERE codigo=?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    if (anterior == null) ps.setString(1, p.codigo());
                    int pos = anterior == null ? 2 : 1;
                    ps.setString(pos++, p.nombre()); ps.setBigDecimal(pos++, p.precioVenta());
                    ps.setBigDecimal(pos++, p.costo()); ps.setInt(pos++, p.stock());
                    if (p.stockMinimo() == null) ps.setNull(pos++, Types.INTEGER); else ps.setInt(pos++, p.stockMinimo());
                    if (p.stockMaximo() == null) ps.setNull(pos++, Types.INTEGER); else ps.setInt(pos++, p.stockMaximo());
                    ps.setString(pos++, p.activo() ? "ACTIVO" : "INACTIVO");
                    if (anterior != null) ps.setString(pos, p.codigo());
                    ps.executeUpdate();
                }
                if (anterior == null || anterior.precioVenta().compareTo(p.precioVenta()) != 0) {
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO HistorialPrecio(producto_codigo,precio) VALUES(?,?)")) {
                        ps.setString(1, p.codigo()); ps.setBigDecimal(2, p.precioVenta()); ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
    public void desactivar(String codigo) throws SQLException {
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement("UPDATE Producto SET estado='INACTIVO' WHERE codigo=?")) {
            ps.setString(1, codigo); if (ps.executeUpdate() != 1) throw new SQLException("Producto inexistente");
        }
    }
    private static Producto desde(ResultSet rs) throws SQLException {
        Integer min = (Integer) rs.getObject("stock_minimo"); Integer max = (Integer) rs.getObject("stock_maximo");
        return new Producto(rs.getString("codigo"),rs.getString("nombre"),rs.getBigDecimal("precio_venta"),
                rs.getBigDecimal("costo"),rs.getInt("stock"),min,max,"ACTIVO".equals(rs.getString("estado")));
    }
}
