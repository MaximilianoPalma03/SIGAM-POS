package ar.com.sigampos.ui;

import javax.swing.Icon;
import java.awt.*;
import java.awt.geom.*;

/** Iconos originales de líneas, dibujados en Java2D y sin recursos externos. */
public final class IconoSeccion implements Icon {
    public enum Tipo { VENTAS, PRODUCTOS, CAJA, PERSONAL, ANALISIS }

    private final Tipo tipo;
    private final Color color;
    private final int lado;

    public IconoSeccion(Tipo tipo, Color color, int lado) {
        this.tipo = tipo;
        this.color = color;
        this.lado = lado;
    }

    @Override public int getIconWidth() { return lado; }
    @Override public int getIconHeight() { return lado; }

    @Override public void paintIcon(Component c, Graphics grafico, int x, int y) {
        Graphics2D g = (Graphics2D) grafico.create();
        try {
            g.translate(x, y);
            g.scale(lado / 24.0, lado / 24.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (tipo) {
                case VENTAS -> {
                    g.draw(new RoundRectangle2D.Double(4, 3, 16, 18, 2.5, 2.5));
                    g.draw(new Line2D.Double(8, 8, 16, 8));
                    g.draw(new Line2D.Double(8, 12, 16, 12));
                    g.draw(new Line2D.Double(8, 16, 11, 16));
                    g.draw(new Line2D.Double(14, 16, 17, 16));
                }
                case PRODUCTOS -> {
                    Path2D caja=new Path2D.Double();
                    caja.moveTo(12,2);caja.lineTo(21,7);caja.lineTo(21,17);
                    caja.lineTo(12,22);caja.lineTo(3,17);caja.lineTo(3,7);
                    caja.closePath();
                    g.draw(caja);
                    g.draw(new Line2D.Double(3, 7, 12, 12));
                    g.draw(new Line2D.Double(21, 7, 12, 12));
                    g.draw(new Line2D.Double(12, 12, 12, 22));
                }
                case CAJA -> {
                    g.draw(new RoundRectangle2D.Double(2, 7, 20, 13, 2.5, 2.5));
                    g.draw(new Line2D.Double(4, 4, 19, 4));
                    g.draw(new Line2D.Double(5, 12, 19, 12));
                    g.draw(new Ellipse2D.Double(15, 15, 2, 2));
                }
                case PERSONAL -> {
                    g.draw(new Ellipse2D.Double(9, 3, 6, 6));
                    g.draw(new Arc2D.Double(5, 11, 14, 14, 0, 180, Arc2D.OPEN));
                    g.draw(new Arc2D.Double(0, 9, 9, 12, 0, 95, Arc2D.OPEN));
                    g.draw(new Arc2D.Double(15, 9, 9, 12, 85, 95, Arc2D.OPEN));
                }
                case ANALISIS -> {
                    g.draw(new Line2D.Double(3, 3, 3, 21));
                    g.draw(new Line2D.Double(3, 21, 22, 21));
                    g.draw(new RoundRectangle2D.Double(6, 13, 3, 6, 1, 1));
                    g.draw(new RoundRectangle2D.Double(12, 8, 3, 11, 1, 1));
                    g.draw(new RoundRectangle2D.Double(18, 4, 3, 15, 1, 1));
                }
            }
        } finally { g.dispose(); }
    }
}
