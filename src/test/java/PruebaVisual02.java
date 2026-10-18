package ar.com.sigampos;

import ar.com.sigampos.ui.GraficoAnalisis;
import ar.com.sigampos.ui.TarjetaSaldoCaja;
import ar.com.sigampos.util.Moneda;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.List;

/** Verifica que los nuevos componentes muestren saldos y datos del gráfico sin MySQL. */
public final class PruebaVisual02 {
    private static boolean contieneTexto(Container origen,String buscado){
        for(Component componente:origen.getComponents()){
            if(componente instanceof JLabel texto && buscado.equals(texto.getText()))return true;
            if(componente instanceof Container hijo && contieneTexto(hijo,buscado))return true;
        }
        return false;
    }
    public static void main(String[] args)throws Exception {
        SwingUtilities.invokeAndWait(()->{
            TarjetaSaldoCaja caja=new TarjetaSaldoCaja("Efectivo",new String[]{"Fondo","Ventas","Entradas","Salidas"});
            caja.mostrar(new BigDecimal("14800"),new BigDecimal[]{new BigDecimal("10000"),
                new BigDecimal("5000"),BigDecimal.ZERO,new BigDecimal("200")},"Saldo esperado");
            if(!contieneTexto(caja,Moneda.formato(new BigDecimal("14800"))))
                throw new AssertionError("La tarjeta no muestra el saldo físico esperado");
            caja.sinDatos("Sin jornada");
            if(!contieneTexto(caja,"Sin jornada") || !contieneTexto(caja,"—"))
                throw new AssertionError("La tarjeta no se restablece sin jornada");

            GraficoAnalisis grafico=new GraficoAnalisis(true);
            grafico.setSize(860,265);
            grafico.mostrar("Facturación ($)",List.of(
                new GraficoAnalisis.Punto("12/10",5000),new GraficoAnalisis.Punto("13/10",10000)));
            MouseEvent hover=new MouseEvent(grafico,MouseEvent.MOUSE_MOVED,System.currentTimeMillis(),0,
                60,120,0,false);
            String tooltip=grafico.getToolTipText(hover);
            if(tooltip==null || !tooltip.contains("12/10") || !tooltip.contains("5.000"))
                throw new AssertionError("El gráfico no muestra datos al pasar el cursor: "+tooltip);
        });
        System.out.println("Tarjetas de caja y datos al pasar el cursor: OK");
    }
}
