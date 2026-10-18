package ar.com.sigampos.ui;

import ar.com.sigampos.util.Moneda;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

/** Vista de un saldo declarado. Los importes llegan calculados desde los servicios. */
public final class TarjetaSaldoCaja extends JPanel {
    private final JLabel saldo = new JLabel("—");
    private final JLabel estado = new JLabel("Sin jornada abierta");
    private final JLabel[] importes = new JLabel[4];

    public TarjetaSaldoCaja(String titulo, String[] conceptos) {
        super(new BorderLayout(0, 10));
        if (conceptos.length != 4) throw new IllegalArgumentException("Se necesitan cuatro conceptos");
        TemaVisual.tarjeta(this);
        JPanel superior = new JPanel(new BorderLayout(3, 5));
        superior.setOpaque(false);
        JLabel encabezado = new JLabel(titulo);
        encabezado.setFont(new Font("Segoe UI", Font.BOLD, 16));
        encabezado.setForeground(TemaVisual.AZUL_OSCURO);
        superior.add(encabezado, BorderLayout.NORTH);
        saldo.setFont(new Font("Segoe UI", Font.BOLD, 27));
        saldo.setForeground(TemaVisual.AZUL);
        superior.add(saldo, BorderLayout.CENTER);
        estado.setForeground(TemaVisual.SECUNDARIO);
        estado.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        superior.add(estado, BorderLayout.SOUTH);
        add(superior, BorderLayout.NORTH);

        JPanel detalle = new JPanel(new GridLayout(4, 2, 8, 9));
        detalle.setOpaque(false);
        for (int i = 0; i < 4; i++) {
            JLabel concepto = new JLabel(conceptos[i]);
            concepto.setForeground(TemaVisual.SECUNDARIO);
            concepto.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            importes[i] = new JLabel("—", SwingConstants.RIGHT);
            importes[i].setFont(new Font("Segoe UI", Font.BOLD, 13));
            importes[i].setForeground(TemaVisual.TEXTO);
            detalle.add(concepto);
            detalle.add(importes[i]);
        }
        add(detalle, BorderLayout.CENTER);
    }

    /** El saldo se actualiza sin realizar consultas ni registrar movimientos. */
    public void mostrar(BigDecimal esperado, BigDecimal[] desglose, String subtitulo) {
        if (desglose.length != 4) throw new IllegalArgumentException("Desglose incompleto");
        saldo.setText(Moneda.formato(esperado));
        estado.setText(subtitulo);
        for (int i = 0; i < 4; i++) importes[i].setText(Moneda.formato(desglose[i]));
    }

    public void sinDatos(String mensaje) {
        saldo.setText("—");
        estado.setText(mensaje);
        for (JLabel importe : importes) importe.setText("—");
    }
}
