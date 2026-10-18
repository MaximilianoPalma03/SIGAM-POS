package ar.com.sigampos.util;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Importe editable: el signo $ siempre está a la vista y no pertenece al texto editable.
 * Los millares se agrupan también durante la escritura (30.213,50).
 * El valor se obtiene con {@link #valor()}, sin depender de la representación visual.
 */
public final class CampoMoneda extends JTextField {
    private static final Locale ARGENTINA=Locale.forLanguageTag("es-AR");
    private static final int ESPACIO_SIMBOLO=29;

    public CampoMoneda(){this(BigDecimal.ZERO);}

    public CampoMoneda(BigDecimal inicial){
        super(12);
        setHorizontalAlignment(SwingConstants.LEFT);
        ajustarMargen();
        ((AbstractDocument)getDocument()).setDocumentFilter(new FiltroImporte());
        setText(numero(inicial));
        addFocusListener(new FocusAdapter(){
            @Override public void focusGained(FocusEvent e){selectAll();}
            @Override public void focusLost(FocusEvent e){
                try{setText(numero(valor()));}catch(IllegalArgumentException ex){/* La validación final muestra el error. */}
            }
        });
    }

    /** Respetar esta separación incluso cuando el tema visual restablece márgenes. */
    public void ajustarMargen(){setMargin(new Insets(5,ESPACIO_SIMBOLO,5,9));}

    public BigDecimal valor(){return Moneda.parsear(getText());}

    private static String numero(BigDecimal valor){
        NumberFormat nf=NumberFormat.getNumberInstance(ARGENTINA);
        nf.setMinimumFractionDigits(2);nf.setMaximumFractionDigits(2);
        return nf.format(valor==null?BigDecimal.ZERO:valor);
    }

    @Override protected void paintComponent(Graphics grafico){
        super.paintComponent(grafico);
        Graphics2D g=(Graphics2D)grafico.create();
        try{
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(getFont().deriveFont(Font.BOLD));
            g.setColor(isEnabled()?new Color(80,101,124):new Color(143,151,161));
            FontMetrics fm=g.getFontMetrics();
            int x=Math.max(9,getInsets().left-ESPACIO_SIMBOLO+9);
            int y=(getHeight()-fm.getHeight())/2+fm.getAscent();
            g.drawString("$",x,y);
        }finally{g.dispose();}
    }

    /**
     * Conserva únicamente el contenido numérico permitido y coloca los puntos de millares
     * al editar; el signo de moneda queda fijo fuera del Document de Swing.
     */
    private static final class FiltroImporte extends DocumentFilter {
        @Override public void insertString(FilterBypass fb,int offset,String texto,AttributeSet atributos)
            throws BadLocationException{replace(fb,offset,0,texto,atributos);}

        @Override public void remove(FilterBypass fb,int offset,int longitud)
            throws BadLocationException{replace(fb,offset,longitud,"",null);}

        @Override public void replace(FilterBypass fb,int offset,int longitud,String texto,AttributeSet atributos)
            throws BadLocationException{
            Document doc=fb.getDocument();
            String antes=doc.getText(0,doc.getLength());
            String candidato=antes.substring(0,offset)+(texto==null?"":texto)+antes.substring(offset+longitud);
            String nuevo=normalizar(candidato);
            if(nuevo==null)return; // No insertar letras, signos negativos ni decimales excesivos.
            fb.replace(0,doc.getLength(),nuevo,atributos);
        }

        static String normalizar(String candidato){
            String valor=candidato.replace("$","").replace(" ","")
                .replace("\u00a0","").replace("\u202f","").trim();
            if(valor.isEmpty())return "";
            if(!valor.matches("[0-9.,]*"))return null;
            if(valor.equals(",")||valor.equals("."))return "0,";
            int coma=valor.indexOf(',');
            if(coma>=0 && valor.indexOf(',',coma+1)>=0)return null;
            // Se admite el punto decimal de un importe pegado (1234.50).
            if(coma<0 && valor.matches("[0-9]+\\.[0-9]{1,2}")){
                valor=valor.replace('.',',');coma=valor.indexOf(',');
            }
            String entero=(coma<0?valor:valor.substring(0,coma)).replace(".","");
            String decimales=coma<0?null:valor.substring(coma+1);
            if(decimales!=null && (!decimales.matches("[0-9]{0,2}")))return null;
            if(entero.isEmpty())entero="0";
            if(!entero.matches("[0-9]+"))return null;
            entero=entero.replaceFirst("^0+(?!$)","");
            StringBuilder agrupado=new StringBuilder(entero);
            for(int i=agrupado.length()-3;i>0;i-=3)agrupado.insert(i,'.');
            return agrupado+(decimales==null?"":","+decimales);
        }
    }
}
