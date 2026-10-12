package ar.com.sigampos.modelo;
import java.math.BigDecimal;
public final class EgresoCaja extends MovimientoCaja {
    public EgresoCaja(BigDecimal monto, String motivo) { super(monto, motivo); }
    @Override public BigDecimal impactoEfectivo() { return getMonto().negate(); }
    @Override public String tipo() { return "SALIDA"; }
}
