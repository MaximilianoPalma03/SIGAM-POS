package ar.com.sigampos.modelo;
import java.math.BigDecimal;
import java.util.Objects;
public final class Producto {
    private final String codigo;
    private final String nombre;
    private final BigDecimal precioVenta;
    private final BigDecimal costo;
    private final int stock;
    private final Integer stockMinimo;
    private final Integer stockMaximo;
    private final boolean activo;
    public Producto(String codigo, String nombre, BigDecimal precioVenta, BigDecimal costo,
                    int stock, Integer stockMinimo, Integer stockMaximo, boolean activo) {
        this.codigo = texto(codigo, "Código"); this.nombre = texto(nombre, "Nombre");
        this.precioVenta = positivo(precioVenta, "Precio"); this.costo = positivo(costo, "Costo");
        if (stock < 0) throw new IllegalArgumentException("Stock negativo");
        if (stockMinimo != null && stockMinimo <= 0) throw new IllegalArgumentException("Mínimo inválido");
        if (stockMaximo != null && stockMaximo <= 0) throw new IllegalArgumentException("Máximo inválido");
        if (stockMinimo != null && stockMaximo != null && stockMaximo < stockMinimo)
            throw new IllegalArgumentException("El máximo no puede ser menor al mínimo");
        this.stock = stock; this.stockMinimo = stockMinimo; this.stockMaximo = stockMaximo; this.activo = activo;
    }
    private static String texto(String s, String nombre) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(nombre + " obligatorio");
        return s.trim();
    }
    private static BigDecimal positivo(BigDecimal b, String nombre) {
        Objects.requireNonNull(b, nombre + " obligatorio");
        if (b.signum() <= 0) throw new IllegalArgumentException(nombre + " debe ser positivo");
        return b.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }
    public String codigo() { return codigo; } public String nombre() { return nombre; }
    public BigDecimal precioVenta() { return precioVenta; } public BigDecimal costo() { return costo; }
    public int stock() { return stock; } public Integer stockMinimo() { return stockMinimo; }
    public Integer stockMaximo() { return stockMaximo; } public boolean activo() { return activo; }
    public BigDecimal rentabilidadPorcentaje() {
        return precioVenta.subtract(costo).multiply(BigDecimal.valueOf(100))
                .divide(costo, 2, java.math.RoundingMode.HALF_UP);
    }
    @Override public String toString() { return codigo + " — " + nombre + " (" + stock + " u.)"; }
}
