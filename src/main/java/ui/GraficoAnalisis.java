package ar.com.sigampos.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/** Gráfico Java2D liviano: Swing puro, sin dependencias ni servicios externos. */
public final class GraficoAnalisis extends JPanel {
    public record Punto(String etiqueta,double valor) {}
    private final boolean lineas;
    private String titulo="Sin datos";
    private List<Punto> puntos=List.of();
    private final Color azul=new Color(36,112,189),rejilla=new Color(226,232,239);
    private int resaltado=-1;

    public GraficoAnalisis(boolean lineas){
        this.lineas=lineas;setOpaque(true);setBackground(Color.WHITE);
        setPreferredSize(new Dimension(860,265));
        setBorder(BorderFactory.createEmptyBorder(9,9,9,9));
        setToolTipText(" "); // Activa los tooltips nativos de Swing.
        addMouseMotionListener(new MouseMotionAdapter(){
            @Override public void mouseMoved(MouseEvent e){
                int nuevo=indiceEn(e.getX(),e.getY());
                if(resaltado!=nuevo){resaltado=nuevo;repaint();}
            }
        });
        addMouseListener(new java.awt.event.MouseAdapter(){
            @Override public void mouseExited(MouseEvent e){resaltado=-1;repaint();}
        });
    }
    public void mostrar(String titulo,List<Punto> puntos){
        this.titulo=titulo;this.puntos=List.copyOf(puntos);resaltado=-1;repaint();
    }
    private int indiceEn(int x,int y){
        if(puntos.isEmpty())return -1;
        int izq=60,der=22,arr=41,abajo=40;
        int ancho=getWidth()-izq-der;
        if(ancho<=0 || x<izq || x>getWidth()-der || y<arr || y>getHeight()-abajo)return -1;
        int n=puntos.size();
        double posicion=(double)(x-izq)/ancho;
        return Math.max(0,Math.min(n-1,lineas?(int)Math.round(posicion*(n-1)):(int)(posicion*n)));
    }
    @Override public String getToolTipText(MouseEvent e){
        int i=indiceEn(e.getX(),e.getY());
        if(i<0)return null;
        Punto p=puntos.get(i);
        String valor=NumberFormat.getNumberInstance(new Locale("es","AR")).format(p.valor());
        return p.etiqueta()+": "+(titulo.contains("$")?"$ ":"")+valor;
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
            if(resaltado>=0 && resaltado<n){
                int px=izq+(int)Math.round(lineas?(n==1?ancho/2d:(double)resaltado/(n-1)*ancho)
                    :((resaltado+.5)*ancho/n));
                int py=arr+alto-(int)Math.round(puntos.get(resaltado).valor()/max*alto);
                g.setColor(new Color(22,109,191,65));
                g.setStroke(new BasicStroke(1f));
                g.drawLine(px,arr,px,arr+alto);
                g.setColor(azul);
                g.fillOval(px-5,py-5,10,10);
                g.setColor(Color.WHITE);
                g.fillOval(px-2,py-2,4,4);
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
