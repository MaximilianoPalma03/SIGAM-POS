package ar.com.sigampos.servicio;
import ar.com.sigampos.datos.Conexion;
import ar.com.sigampos.modelo.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Confirma la venta completa de manera atómica: detalle + pagos + descuento de stock + efectivo. */
public final class VentaServicio {
    private final ProductoServicio productos = new ProductoServicio();
    public long confirmar(long cajaId, long empleadoId, List<LineaVenta> lineas, List<PagoVenta> pagos) throws SQLException {
        if (lineas == null || lineas.isEmpty()) throw new IllegalArgumentException("La venta no tiene productos");
        if (pagos == null || pagos.isEmpty()) throw new IllegalArgumentException("Falta el pago");
        // Se agrupan productos duplicados y se bloquean siempre en el mismo orden para reducir deadlocks.
        TreeMap<String,Integer> cantidades = new TreeMap<>();
        for (LineaVenta l : lineas) cantidades.merge(l.producto().codigo(), l.cantidad(), Math::addExact);
        try (Connection c = Conexion.abrir()) {
            c.setAutoCommit(false);
            try {
                comprobarCaja(c, cajaId, empleadoId);
                Map<String,Producto> actuales = new LinkedHashMap<>();
                for (var item : cantidades.entrySet()) {
                    Producto p = productos.buscar(c, item.getKey(), true);
                    if (p == null || !p.activo()) throw new IllegalArgumentException("Producto inexistente/inactivo: " + item.getKey());
                    if (p.stock() < item.getValue()) throw new IllegalArgumentException("Stock insuficiente: " + p.nombre());
                    actuales.put(item.getKey(), p);
                }
                BigDecimal total = BigDecimal.ZERO;
                for (var item : cantidades.entrySet()) total = total.add(actuales.get(item.getKey()).precioVenta().multiply(BigDecimal.valueOf(item.getValue())));
                BigDecimal abonado = pagos.stream().map(PagoVenta::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (total.compareTo(abonado) != 0) throw new IllegalArgumentException("Los pagos deben sumar exactamente " + total);
                long ventaId;
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO Venta(caja_id,empleado_id,total) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, cajaId); ps.setLong(2, empleadoId); ps.setBigDecimal(3,total); ps.executeUpdate();
                    ventaId = idGenerado(ps);
                }
                for (var item : cantidades.entrySet()) {
                    Producto p = actuales.get(item.getKey());
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO DetalleVenta(venta_id,producto_codigo,cantidad,precio_unitario,costo_unitario) VALUES(?,?,?,?,?)")) {
                        ps.setLong(1,ventaId); ps.setString(2,p.codigo()); ps.setInt(3,item.getValue()); ps.setBigDecimal(4,p.precioVenta()); ps.setBigDecimal(5,p.costo()); ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement("UPDATE Producto SET stock=stock-? WHERE codigo=? AND stock>=?")) {
                        ps.setInt(1,item.getValue()); ps.setString(2,p.codigo()); ps.setInt(3,item.getValue());
                        if (ps.executeUpdate()!=1) throw new SQLException("Stock modificado por otra operación");
                    }
                }
                for (PagoVenta pago : pagos) {
                    long pagoId;
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO Pago(venta_id,medio_pago,monto) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                        ps.setLong(1,ventaId); ps.setString(2,pago.medio().name()); ps.setBigDecimal(3,pago.monto()); ps.executeUpdate();
                        pagoId = idGenerado(ps);
                    }
                    if (pago.medio() == MedioPago.TRANSFERENCIA || pago.medio() == MedioPago.QR) {
                        CajaDigitalServicio.cobroEnTransaccion(c,cajaId,empleadoId,pagoId,pago.medio().name(),pago.monto());
                    }
                    if (pago.medio() == MedioPago.EFECTIVO) {
                        try (PreparedStatement ps = c.prepareStatement("INSERT INTO MovimientoCaja(caja_id,pago_id,empleado_id,tipo,monto,motivo) VALUES(?,?,?,'INGRESO_VENTA',?,'Cobro en efectivo')")) {
                            ps.setLong(1,cajaId); ps.setLong(2,pagoId); ps.setLong(3,empleadoId); ps.setBigDecimal(4,pago.monto()); ps.executeUpdate();
                        }
                    }
                }
                c.commit(); return ventaId;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
    static long idGenerado(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.getGeneratedKeys()) { if (!rs.next()) throw new SQLException("No se generó ID"); return rs.getLong(1); }
    }
    static void comprobarCaja(Connection c, long cajaId, long empleadoId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT C.id FROM Caja C JOIN JornadaLaboral J ON J.id=C.jornada_id JOIN Empleado E ON E.id=J.empleado_id WHERE C.id=? AND C.estado='ABIERTA' AND J.estado='ABIERTA' AND J.empleado_id=? AND E.estado='ACTIVO' FOR UPDATE")) {
            ps.setLong(1,cajaId); ps.setLong(2,empleadoId);
            try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new IllegalStateException("No hay caja abierta para el empleado activo de ese turno"); }
        }
    }
}
