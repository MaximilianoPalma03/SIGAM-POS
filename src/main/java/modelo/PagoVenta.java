package ar.com.sigampos.modelo;
import java.math.BigDecimal;
import java.util.Objects;
public record PagoVenta(MedioPago medio, BigDecimal monto) {
    public PagoVenta {
        Objects.requireNonNull(medio); Objects.requireNonNull(monto);
        if (monto.signum() <= 0 || monto.scale() > 2) throw new IllegalArgumentException("Importe de pago inválido");
    }
}
