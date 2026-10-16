package ar.com.sigampos;

import ar.com.sigampos.ui.PanelAnalisisComercial;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Prueba de la interfaz de filtros sin conexión a MySQL ni pantalla gráfica. */
public final class PruebaPanelAnalisis {
    private static void recorrer(Component c,List<JToggleButton> botones,List<JButton> acciones){
        if(c instanceof JToggleButton b)botones.add(b);
        else if(c instanceof JButton b)acciones.add(b);
        if(c instanceof Container cont)for(Component child:cont.getComponents())recorrer(child,botones,acciones);
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("java.awt.headless","true");
        SwingUtilities.invokeAndWait(()->{
            PanelAnalisisComercial p=new PanelAnalisisComercial(codigo->{});
            List<JToggleButton> botones=new ArrayList<>();List<JButton> acciones=new ArrayList<>();
            recorrer(p,botones,acciones);
            if(botones.size()!=18)throw new AssertionError("Esperaba tres selectores por seis secciones, hay "+botones.size());
            int unMes=0,tres=0,seis=0;
            for(JToggleButton b:botones){
                if("1 mes".equals(b.getText()))unMes++;
                if("3 meses".equals(b.getText()))tres++;
                if("6 meses".equals(b.getText()))seis++;
                if("Recalcular".equalsIgnoreCase(b.getText()))throw new AssertionError("No debe existir Recalcular");
            }
            if(unMes!=6||tres!=6||seis!=6)throw new AssertionError("Selectores incompletos");
            if(acciones.stream().anyMatch(b->b.getText().toLowerCase().contains("recalcular")||
                    b.getText().toLowerCase().contains("actualizar análisis")))
                throw new AssertionError("Quedó un botón extra para confirmar el período");
            JToggleButton selector3=botones.stream().filter(b->"3 meses".equals(b.getText())).findFirst().orElseThrow();
            selector3.doClick();
            if(!selector3.isSelected())throw new AssertionError("El selector no responde en un clic");
            System.out.println("Panel: 6 grupos independientes de 1/3/6 meses y sin Recalcular: OK");
        });
    }
}
