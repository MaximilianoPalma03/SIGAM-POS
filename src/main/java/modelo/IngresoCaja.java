package ar.com.sigampos.modelo;
import java.math.BigDecimal;
public final class IngresoCaja extends MovimientoCaja {
    private final boolean originadoPorVenta;
    public IngresoCaja(BigDecimal monto, String motivo, boolean originadoPorVenta) {
        super(monto, motivo); this.originadoPorVenta = originadoPorVenta;
    }
    @Override public BigDecimal impactoEfectivo() { return getMonto(); }
    @Override public String tipo() { return originadoPorVenta ? "INGRESO_VENTA" : "ENTRADA"; }
}
