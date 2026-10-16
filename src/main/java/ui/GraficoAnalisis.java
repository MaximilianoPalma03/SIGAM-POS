package ar.com.sigampos.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/** Gráfico Java2D liviano: Swing puro, sin dependencias ni servicios externos. */
public final class GraficoAnalisis extends JPanel {
    public record Punto(String etiqueta,double valor) {}
    private final boolean lineas;
    private String titulo="Sin datos";
    private List<Punto> puntos=List.of();
    private final Color azul=new Color(36,112,189),rejilla=new Color(226,232,239);

    public GraficoAnalisis(boolean lineas){
        this.lineas=lineas;setOpaque(true);setBackground(Color.WHITE);
        setPreferredSize(new Dimension(860,265));
        setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
    }
    public void mostrar(String titulo,List<Punto> puntos){
        this.titulo=titulo;this.puntos=List.copyOf(puntos);repaint();
    }
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);
        Graphics2D g=(Graphics2D)graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight(),izq=60,der=22,arr=41,abajo=40;
            g.setColor(new Color(35,50,69));g.setFont(getFont().deriveFont(Font.BOLD,13f));
            g.drawString(titulo,izq,22);
            if(w<130 || h<110)return;
            int alto=h-arr-abajo,ancho=w-izq-der;
            double max=puntos.stream().mapToDouble(Punto::valor).max().orElse(0);
            max=Math.max(1,max);
            g.setFont(getFont().deriveFont(11f));
            for(int i=0;i<=4;i++){
                int y=arr+alto-i*alto/4;
                g.setColor(rejilla);g.drawLine(izq,y,w-der,y);
                g.setColor(new Color(85,94,107));
                String tick=formatoCorto(max*i/4d);
                g.drawString(tick,Math.max(3,izq-7-g.getFontMetrics().stringWidth(tick)),y+4);
            }
            if(puntos.isEmpty()){
                g.setColor(Color.GRAY);g.drawString("Consultá un período para ver el gráfico",izq+25,arr+alto/2);return;
            }
            int n=puntos.size();
            if(lineas){
                Path2D.Double linea=new Path2D.Double();
                for(int i=0;i<n;i++){
                    double x=izq+(n==1?ancho/2d:(double)i/(n-1)*ancho);
                    double y=arr+alto-(puntos.get(i).valor()/max)*alto;
                    if(i==0)linea.moveTo(x,y);else linea.lineTo(x,y);
                }
                g.setColor(azul);g.setStroke(new BasicStroke(2.4f));g.draw(linea);
                if(n<=40)for(int i=0;i<n;i++){
                    int x=izq+(int)Math.round(n==1?ancho/2d:(double)i/(n-1)*ancho);
                    int y=arr+alto-(int)Math.round(puntos.get(i).valor()/max*alto);
                    g.fillOval(x-3,y-3,6,6);
                }
            } else {
                double sector=(double)ancho/n;
                for(int i=0;i<n;i++){
                    int x=izq+(int)Math.round(sector*i+sector*.15);
                    int anchoBarra=Math.max(2,(int)Math.floor(sector*.7));
                    int altoBarra=(int)Math.round(puntos.get(i).valor()/max*alto);
                    g.setColor(azul);g.fillRoundRect(x,arr+alto-altoBarra,anchoBarra,altoBarra,3,3);
                }
            }
            g.setStroke(new BasicStroke(1f));
            int salto=n<=14?1:Math.max(1,(int)Math.ceil(n/9d));
            for(int i=0;i<n;i++){
                if(i%salto!=0 && i!=n-1)continue;
                int x=izq+(int)Math.round(lineas?(n==1?ancho/2d:(double)i/(n-1)*ancho):((i+.5)*ancho/n));
                String etiqueta=puntos.get(i).etiqueta();
                int lw=g.getFontMetrics().stringWidth(etiqueta);
                g.setColor(new Color(82,91,104));g.drawString(etiqueta,
                    Math.max(izq,Math.min(w-der-lw,x-lw/2)),h-17);
            }
        } finally {g.dispose();}
    }
    private static String formatoCorto(double valor){
        if(valor>=1000000)return String.format(java.util.Locale.ROOT,"%.1fM",valor/1000000);
        if(valor>=1000)return String.format(java.util.Locale.ROOT,"%.0fk",valor/1000);
        return String.format(java.util.Locale.ROOT,"%.0f",valor);
    }
}
