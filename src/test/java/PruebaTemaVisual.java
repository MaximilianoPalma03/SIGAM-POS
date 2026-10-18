package ar.com.sigampos;

import ar.com.sigampos.ui.IconoSeccion;
import ar.com.sigampos.ui.TemaVisual;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Verifica componentes del tema sin conectar a MySQL ni abrir ventanas. */
public final class PruebaTemaVisual {
    public static void main(String[] args) throws Exception {
        TemaVisual.instalar();
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel=new JPanel();
            JTable tabla=new JTable(2,3);
            JButton boton=new JButton("Probar");
            panel.add(new JScrollPane(tabla));
            panel.add(boton);
            TemaVisual.aplicar(panel);
            if (tabla.getRowHeight()!=36) throw new AssertionError("Altura de fila");
            if (!TemaVisual.BLANCO.equals(tabla.getBackground())) throw new AssertionError("Color tabla");
            TemaVisual.botonPrincipal(boton);
            if (!TemaVisual.AZUL.equals(boton.getBackground()))throw new AssertionError("Botón principal");
            for(IconoSeccion.Tipo tipo:IconoSeccion.Tipo.values()) {
                BufferedImage imagen=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
                Graphics2D g=imagen.createGraphics();
                new IconoSeccion(tipo,Color.BLUE,24).paintIcon(panel,g,4,4);
                g.dispose();
                int coloreados=0;
                for(int y=0;y<32;y++)for(int x=0;x<32;x++)
                    if((imagen.getRGB(x,y)>>>24)!=0)coloreados++;
                if(coloreados==0)throw new AssertionError("Icono vacío: "+tipo);
            }
        });
        System.out.println("Tema, tablas, botones e iconos: OK");
    }
}
