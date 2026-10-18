package ar.com.sigampos;

import ar.com.sigampos.servicio.ConfiguracionProductos;
import ar.com.sigampos.ui.TemaVisual;
import ar.com.sigampos.util.CampoMoneda;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.util.prefs.Preferences;

/** Comprobaciones de edición monetaria, recargo, persistencia y bordes de tablas. */
public final class PruebaVisual03 {
    private static void igual(String esperado,String real){
        if(!esperado.equals(real))throw new AssertionError("Esperado: "+esperado+"; encontrado: "+real);
    }
    public static void main(String[] args)throws Exception{
        Preferences temporal=Preferences.userRoot().node("sigampos-prueba-visual03");
        try{
            temporal.remove("productos.precioPorRecargo");
            ConfiguracionProductos cfg=new ConfiguracionProductos(temporal);
            if(cfg.precioPorRecargoActivo())throw new AssertionError("El cálculo debe iniciar desactivado");
            cfg.establecerPrecioPorRecargo(true);
            if(!new ConfiguracionProductos(temporal).precioPorRecargoActivo())
                throw new AssertionError("La preferencia no persistió");
            cfg.establecerPrecioPorRecargo(false);
            if(cfg.precioPorRecargoActivo())throw new AssertionError("No se desactivó");
        }finally{temporal.removeNode();}
        igual("13000.00",ConfiguracionProductos.precioSugerido(new BigDecimal("10000"),
            new BigDecimal("30")).toPlainString());
        igual("1327.15",ConfiguracionProductos.precioSugerido(new BigDecimal("1234.56"),
            new BigDecimal("7.5")).toPlainString());
        igual("30.00",ConfiguracionProductos.recargoExistente(new BigDecimal("10000"),
            new BigDecimal("13000")).toPlainString());
        SwingUtilities.invokeAndWait(()->{
            CampoMoneda importe=new CampoMoneda(new BigDecimal("30213.50"));
            igual("30.213,50",importe.getText());
            if(importe.getMargin().left<25)throw new AssertionError("No reservó espacio para el $ fijo");
            JPanel panel=new JPanel();panel.add(importe);
            TemaVisual.aplicar(panel);
            if(importe.getMargin().left<25)throw new AssertionError("El tema visual ocultó el $ fijo");
            importe.selectAll();importe.replaceSelection("30213");
            igual("30.213",importe.getText());
            igual("30213.00",importe.valor().toPlainString());
            // Simula ingreso de números y decimales tecla por tecla, no solo pegado.
            CampoMoneda teclado=new CampoMoneda();
            teclado.selectAll();
            for(char c:"30213,50".toCharArray())
                teclado.replaceSelection(String.valueOf(c));
            igual("30.213,50",teclado.getText());
            igual("30213.50",teclado.valor().toPlainString());
            importe.setText("$ 3.000,75");
            igual("3.000,75",importe.getText());
            igual("3000.75",importe.valor().toPlainString());
            importe.setText("-22"); // Entrada inválida; se descarta, sin destruir el valor previo.
            igual("3.000,75",importe.getText());
            importe.setSize(180,32);
            java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(180,32,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=img.createGraphics();importe.paint(g);g.dispose();
            int tinta=0;
            for(int y=3;y<29;y++)for(int x=8;x<26;x++){
                if((img.getRGB(x,y)&0xFFFFFF)!=0xFFFFFF)tinta++;
            }
            if(tinta==0)throw new AssertionError("El signo de moneda no se dibuja");

            JTable tabla=new JTable(3,2);
            tabla.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer());
            panel.add(new JScrollPane(tabla));
            TemaVisual.aplicar(panel);
            if(tabla.getColumnModel().getColumn(0).getCellRenderer()==null ||
                tabla.getColumnModel().getColumn(1).getCellRenderer()==null)
                throw new AssertionError("No se aplicaron los bordes a todas las columnas");
            Component celda=tabla.prepareRenderer(tabla.getCellRenderer(0,1),0,1);
            if(!(celda instanceof JComponent c) || c.getBorder()==null)
                throw new AssertionError("No hay borde sutil entre celdas");
            TemaVisual.aplicar(panel); // Debe ser seguro aplicar el tema nuevamente.
        });
        System.out.println("Campo monetario con $ fijo, recargo configurable y separadores de tablas: OK");
    }
}
