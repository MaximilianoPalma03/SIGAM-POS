package ar.com.sigampos.modelo;
import java.math.BigDecimal;
import java.util.Objects;
public record LineaVenta(Producto producto, int cantidad) {
    public LineaVenta { Objects.requireNonNull(producto); if (cantidad <= 0) throw new IllegalArgumentException("Cantidad inválida"); }
    public BigDecimal subtotal() { return producto.precioVenta().multiply(BigDecimal.valueOf(cantidad)); }
}
