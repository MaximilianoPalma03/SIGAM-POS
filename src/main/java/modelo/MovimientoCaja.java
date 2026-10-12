package ar.com.sigampos.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** POO: abstracción, encapsulamiento y herencia del movimiento físico de efectivo. */
public abstract class MovimientoCaja {
    private final BigDecimal monto;
    private final String motivo;
    private final LocalDateTime fechaHora;

    protected MovimientoCaja(BigDecimal monto, String motivo) {
        Objects.requireNonNull(monto, "El monto es obligatorio");
        if (monto.signum() <= 0) throw new IllegalArgumentException("El monto debe ser positivo");
        this.monto = monto.setScale(2, java.math.RoundingMode.UNNECESSARY);
        this.motivo = motivo == null ? "" : motivo.trim();
        this.fechaHora = LocalDateTime.now();
    }
    public final BigDecimal getMonto() { return monto; }
    public final String getMotivo() { return motivo; }
    public final LocalDateTime getFechaHora() { return fechaHora; }
    public abstract BigDecimal impactoEfectivo();
    public abstract String tipo();
}
