package ar.com.sigampos.ui;

import ar.com.sigampos.servicio.AnalisisAvanzadoServicio;
import ar.com.sigampos.servicio.AnalisisAvanzadoServicio.*;
import ar.com.sigampos.servicio.PeriodoAnalisis;
import ar.com.sigampos.util.Moneda;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Análisis comercial: cada sección cambia de 1/3/6 meses con UN solo clic. */
public final class PanelAnalisisComercial extends JPanel {
    private final AnalisisAvanzadoServicio servicio=new AnalisisAvanzadoServicio();
    private final Consumer<String> verProducto;
    private final JTabbedPane secciones=new JTabbedPane();
    private final JLabel estado=new JLabel("Abrí un gráfico para calcular sus indicadores.");
    private final JLabel ventas=new JLabel("—"),facturacion=new JLabel("—"),ticket=new JLabel("—"),
        unidades=new JLabel("—"),ganancia=new JLabel("—"),pico=new JLabel("—");
    private final JLabel periodoVisible=new JLabel("Período: 1 mes");
    private final JButton exportar=new JButton("Exportar tabla CSV");
    private final PeriodoAnalisis[] periodos=new PeriodoAnalisis[6];
    private final GraficoAnalisis graficoDias=new GraficoAnalisis(true),graficoHoras=new GraficoAnalisis(false),
        graficoProductos=new GraficoAnalisis(false),graficoVendedores=new GraficoAnalisis(false),
        graficoMedios=new GraficoAnalisis(false);
    private final JComboBox<String> metricaDias=new JComboBox<>(new String[]{"Facturación ($)","Cantidad de ventas"});
    private final JComboBox<String> metricaHoras=new JComboBox<>(new String[]{"Cantidad de ventas","Facturación ($)"});
    private final JComboBox<String> ordenProductos=new JComboBox<>(new String[]{"Unidades vendidas","Facturación","Ganancia conocida"});
    private final DefaultTableModel tablaDias=modelo(new String[]{"Fecha","Ventas","Facturación"},
        new Class<?>[]{String.class,Long.class,BigDecimal.class});
    private final DefaultTableModel tablaHoras=modelo(new String[]{"Hora","Ventas","Facturación"},
        new Class<?>[]{String.class,Long.class,BigDecimal.class});
    private final DefaultTableModel tablaProductos=modelo(new String[]{"Código","Producto","Estado","Unidades","Facturación","Margen*","Ganancia conocida*","Stock","Cobertura (días)","Diagnóstico"},
        new Class<?>[]{String.class,String.class,String.class,Long.class,BigDecimal.class,String.class,String.class,Integer.class,String.class,String.class});
    private final DefaultTableModel tablaOportunidades=modelo(new String[]{"Código","Producto","Stock","Unidades","Cobertura","Motivo"},
        new Class<?>[]{String.class,String.class,Integer.class,Long.class,String.class,String.class});
    private final DefaultTableModel tablaVendedores=modelo(new String[]{"Empleado","Ventas","Facturación"},
        new Class<?>[]{String.class,Long.class,BigDecimal.class});
    private final DefaultTableModel tablaPagos=modelo(new String[]{"Medio de pago","Pagos","Importe"},
        new Class<?>[]{String.class,Long.class,BigDecimal.class});
    private final JTable tDias=tabla(tablaDias),tHoras=tabla(tablaHoras),tProductos=tabla(tablaProductos),
        tOportunidades=tabla(tablaOportunidades),tVendedores=tabla(tablaVendedores),tPagos=tabla(tablaPagos);
    private Informe actual;
    private long peticion=0;
    private boolean cargando=false;

    public PanelAnalisisComercial(Consumer<String> verProducto) {
        super(new BorderLayout(8,8));this.verProducto=Objects.requireNonNull(verProducto);
        Arrays.fill(periodos,PeriodoAnalisis.UN_MES);
        setBorder(BorderFactory.createEmptyBorder(10,12,10,12));
        JPanel superior=new JPanel(new BorderLayout(4,6));
        JPanel encabezado=new JPanel(new BorderLayout());
        JLabel titulo=new JLabel("ANÁLISIS COMERCIAL · Estadísticas de los últimos meses");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD,15f));
        encabezado.add(titulo,BorderLayout.WEST);
        encabezado.add(periodoVisible,BorderLayout.EAST);
        superior.add(encabezado,BorderLayout.NORTH);
        JPanel metricas=new JPanel(new GridLayout(2,3,8,7));
        metricas.add(indicador("VENTAS",ventas));metricas.add(indicador("FACTURACIÓN NOMINAL",facturacion));
        metricas.add(indicador("TICKET PROMEDIO",ticket));metricas.add(indicador("UNIDADES VENDIDAS",unidades));
        metricas.add(indicador("GANANCIA BRUTA CONOCIDA*",ganancia));metricas.add(indicador("HORA PICO",pico));
        superior.add(metricas,BorderLayout.CENTER);
        add(superior,BorderLayout.NORTH);
        secciones.addTab("Evolución de ventas",panelGrafico(0,graficoDias,metricaDias,tDias));
        secciones.addTab("Horarios pico",panelGrafico(1,graficoHoras,metricaHoras,tHoras));
        secciones.addTab("Productos",panelProductos());
        secciones.addTab("Oportunidades",panelOportunidades());
        secciones.addTab("Vendedores",panelListado(4,tVendedores,graficoVendedores));
        secciones.addTab("Medios de pago",panelListado(5,tPagos,graficoMedios));
        add(secciones,BorderLayout.CENTER);
        JPanel inferior=new JPanel(new BorderLayout(4,4));
        JLabel nota=new JLabel("* Ganancia bruta = venta - costo histórico conocido; no incluye gastos. Ventas anteriores a v0.6.0: costo desconocido.");
        nota.setFont(nota.getFont().deriveFont(11f));
        inferior.add(nota,BorderLayout.NORTH);
        JPanel pie=new JPanel(new BorderLayout(8,0));
        pie.add(estado,BorderLayout.CENTER);pie.add(exportar,BorderLayout.EAST);
        inferior.add(pie,BorderLayout.SOUTH);add(inferior,BorderLayout.SOUTH);
        exportar.addActionListener(e->exportarVisible());
        ordenProductos.addActionListener(e->{if(actual!=null)cargarProductos(actual);});
        metricaDias.addActionListener(e->{if(actual!=null)actualizarGraficoDias(actual);});
        metricaHoras.addActionListener(e->{if(actual!=null)actualizarGraficoHoras(actual);});
        secciones.addChangeListener(e->{if(isShowing())cargar();});
        addHierarchyListener(e->{if((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED)!=0 && isShowing())cargar();});
    }
    private static DefaultTableModel modelo(String[] nombres,Class<?>[] clases){
        return new DefaultTableModel(nombres,0){
            @Override public boolean isCellEditable(int r,int c){return false;}
            @Override public Class<?> getColumnClass(int c){return clases[c];}
        };
    }
    private static JTable tabla(DefaultTableModel m){
        JTable t=new JTable(m);t.setRowHeight(26);t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setAutoCreateRowSorter(true);t.setFillsViewportHeight(true);
        t.setDefaultRenderer(BigDecimal.class,new DefaultTableCellRenderer(){
            @Override protected void setValue(Object o){setText(o==null?"—":Moneda.formato((BigDecimal)o));}
        });
        return t;
    }
    private static JPanel indicador(String titulo,JLabel dato){
        JPanel p=new JPanel(new BorderLayout(4,3));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(216,225,237)),
            BorderFactory.createEmptyBorder(7,12,7,12)));
        JLabel l=new JLabel(titulo);l.setFont(l.getFont().deriveFont(Font.BOLD,10f));
        dato.setFont(dato.getFont().deriveFont(Font.BOLD,17f));
        p.add(l,BorderLayout.NORTH);p.add(dato,BorderLayout.CENTER);return p;
    }
    private JPanel selector(int indice,String descripcion,JComponent izquierda){
        JPanel barra=new JPanel(new BorderLayout(10,2));
        JPanel izq=new JPanel(new FlowLayout(FlowLayout.LEFT,6,3));
        if(descripcion!=null && !descripcion.isBlank())izq.add(new JLabel(descripcion));
        if(izquierda!=null)izq.add(izquierda);
        barra.add(izq,BorderLayout.WEST);
        JPanel derecha=new JPanel(new FlowLayout(FlowLayout.RIGHT,3,2));
        ButtonGroup grupo=new ButtonGroup();
        for(PeriodoAnalisis periodo:PeriodoAnalisis.values()){
            JToggleButton boton=new JToggleButton(periodo.etiqueta());
            boton.setSelected(periodo==periodos[indice]);
            grupo.add(boton);derecha.add(boton);
            boton.addActionListener(e->{periodos[indice]=periodo;if(isShowing() && secciones.getSelectedIndex()==indice)cargar();});
        }
        barra.add(derecha,BorderLayout.EAST);
        return barra;
    }
    private JPanel panelGrafico(int indice,GraficoAnalisis grafico,JComboBox<String> metrica,JTable datos){
        JPanel p=new JPanel(new BorderLayout(7,7));
        p.add(selector(indice,"Métrica:",metrica),BorderLayout.NORTH);
        JSplitPane particion=new JSplitPane(JSplitPane.VERTICAL_SPLIT,grafico,new JScrollPane(datos));
        particion.setResizeWeight(.61);particion.setDividerLocation(265);
        p.add(particion,BorderLayout.CENTER);return p;
    }
    private JPanel panelProductos(){
        JPanel p=new JPanel(new BorderLayout(6,6));
        JPanel filtros=new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));
        filtros.add(new JLabel("Ordenar:"));filtros.add(ordenProductos);
        JButton ver=new JButton("Ver producto");ver.addActionListener(e->abrirProducto(tProductos));filtros.add(ver);
        p.add(selector(2,"",filtros),BorderLayout.NORTH);
        tProductos.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] anchos={130,200,75,90,125,105,140,65,115,255};
        for(int i=0;i<anchos.length;i++)tProductos.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        JSplitPane particion=new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            graficoProductos,new JScrollPane(tProductos));
        particion.setResizeWeight(.46);particion.setDividerLocation(220);
        p.add(particion,BorderLayout.CENTER);return p;
    }
    private JPanel panelOportunidades(){
        JPanel p=new JPanel(new BorderLayout(6,6));
        JButton ver=new JButton("Ver producto");ver.addActionListener(e->abrirProducto(tOportunidades));
        p.add(selector(3,"Alertas y oportunidades comerciales ·",ver),BorderLayout.NORTH);
        p.add(new JScrollPane(tOportunidades),BorderLayout.CENTER);return p;
    }
    private JPanel panelListado(int indice,JTable datos,GraficoAnalisis grafico){
        JPanel p=new JPanel(new BorderLayout(6,6));
        p.add(selector(indice,indice==4?"Resultados por vendedor":"Recaudación por medio de pago",null),BorderLayout.NORTH);
        JSplitPane particion=new JSplitPane(JSplitPane.VERTICAL_SPLIT,grafico,new JScrollPane(datos));
        particion.setResizeWeight(.58);particion.setDividerLocation(250);
        p.add(particion,BorderLayout.CENTER);return p;
    }
    private void abrirProducto(JTable t){
        int fila=t.getSelectedRow();
        if(fila<0){JOptionPane.showMessageDialog(this,"Seleccioná un producto.");return;}
        verProducto.accept(String.valueOf(t.getValueAt(fila,0)));
    }
    private void cargar(){
        int tab=secciones.getSelectedIndex();
        if(tab<0)return;
        PeriodoAnalisis periodo=periodos[tab];
        LocalDate hasta=LocalDate.now(),desde=periodo.desde(hasta);
        long id=++peticion;
        actual=null;cargando=true;exportar.setEnabled(false);
        periodoVisible.setText("Período: "+periodo.etiqueta());
        estado.setText("Calculando "+periodo.etiqueta()+" ("+desde+" a "+hasta+")...");
        new SwingWorker<Informe,Void>(){
            @Override protected Informe doInBackground()throws Exception{return servicio.consultar(desde,hasta);}
            @Override protected void done(){
                if(id!=peticion)return; // no sobreescribir una selección nueva con una respuesta vieja
                cargando=false;
                try{mostrar(get(),tab,periodo);exportar.setEnabled(true);}
                catch(Exception e){
                    Throwable raiz=e.getCause()==null?e:e.getCause();
                    estado.setText("Error de consulta: "+raiz.getMessage());
                    JOptionPane.showMessageDialog(PanelAnalisisComercial.this,raiz.getMessage(),
                        "No se pudo consultar Análisis",JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    private void mostrar(Informe datos,int tab,PeriodoAnalisis periodo){
        actual=datos;Indicadores k=datos.indicadores();
        ventas.setText(Long.toString(k.cantidadVentas()));
        facturacion.setText(Moneda.formato(k.facturacion()));
        ticket.setText(Moneda.formato(k.ticketPromedio()));
        unidades.setText(Long.toString(k.unidadesVendidas()));
        ganancia.setText(k.unidadesConCosto()==0?"Sin costos":Moneda.formato(k.gananciaBrutaConocida()));
        Hora h=datos.horaPico();
        pico.setText(h==null||k.cantidadVentas()==0?"Sin ventas":String.format("%02d:00–%02d:00",h.hora(),(h.hora()+1)%24));
        estado.setText("Datos de "+datos.desde()+" a "+datos.hasta()+" · Costos históricos disponibles para "+
            (k.coberturaCostosPorcentaje()==null?"0%":k.coberturaCostosPorcentaje()+"%")+
            " de las unidades. Sin ajuste por inflación.");
        switch(tab){
            case 0 -> {
                tablaDias.setRowCount(0);
                for(Dia d:datos.dias())tablaDias.addRow(new Object[]{d.fecha().toString(),d.ventas(),d.facturacion()});
                actualizarGraficoDias(datos);
            }
            case 1 -> {
                tablaHoras.setRowCount(0);
                for(Hora hora:datos.horas())tablaHoras.addRow(new Object[]{String.format("%02d:00",hora.hora()),hora.ventas(),hora.facturacion()});
                actualizarGraficoHoras(datos);
            }
            case 2 -> cargarProductos(datos);
            case 3 -> {
                tablaOportunidades.setRowCount(0);
                for(ProductoFila p:datos.oportunidades())tablaOportunidades.addRow(new Object[]{
                    p.codigo(),p.nombre(),p.stock(),p.unidades(),
                    p.diasCobertura()==null?"—":p.diasCobertura().toPlainString(),p.diagnostico()});
            }
            case 4 -> {
                tablaVendedores.setRowCount(0);
                List<GraficoAnalisis.Punto> barras=new ArrayList<>();
                for(Vendedor v:datos.vendedores()){
                    tablaVendedores.addRow(new Object[]{v.nombre(),v.ventas(),v.facturacion()});
                    if(barras.size()<10)barras.add(new GraficoAnalisis.Punto(v.nombre().length()>12?v.nombre().substring(0,12):v.nombre(),v.ventas()));
                }
                graficoVendedores.mostrar("Operaciones por vendedor (top 10)",barras);
            }
            case 5 -> {
                tablaPagos.setRowCount(0);
                List<GraficoAnalisis.Punto> barras=new ArrayList<>();
                for(Medio m:datos.medios()){
                    tablaPagos.addRow(new Object[]{m.medio(),m.operaciones(),m.facturacion()});
                    barras.add(new GraficoAnalisis.Punto(m.medio(),m.facturacion().doubleValue()));
                }
                graficoMedios.mostrar("Recaudación nominal por medio de pago ($)",barras);
            }
            default -> throw new IllegalStateException("Sección desconocida");
        }
    }
    private void cargarProductos(Informe datos){
        List<ProductoFila> ranking=new ArrayList<>(datos.productos());
        switch(ordenProductos.getSelectedIndex()){
            case 1 -> ranking.sort(Comparator.comparing(ProductoFila::facturacion).reversed().thenComparing(ProductoFila::nombre));
            case 2 -> ranking.sort(Comparator.comparing(ProductoFila::costoCompleto).reversed()
                .thenComparing(ProductoFila::gananciaBrutaConocida,Comparator.reverseOrder())
                .thenComparing(ProductoFila::nombre));
            default -> ranking.sort(Comparator.comparingLong(ProductoFila::unidades).reversed().thenComparing(ProductoFila::nombre));
        }
        tablaProductos.setRowCount(0);
        List<GraficoAnalisis.Punto> barras=new ArrayList<>();
        for(ProductoFila p:ranking){
            String margen=p.costoCompleto() && p.margenConocido()!=null?p.margenConocido()+"%":"—";
            String ganancia=p.costoCompleto()?Moneda.formato(p.gananciaBrutaConocida()):"—";
            tablaProductos.addRow(new Object[]{p.codigo(),p.nombre(),p.activo()?"ACTIVO":"INACTIVO",
                p.unidades(),p.facturacion(),margen,ganancia,p.stock(),
                p.diasCobertura()==null?"—":p.diasCobertura().toPlainString(),p.diagnostico()});
            if(barras.size()<10 && p.unidades()>0 && (ordenProductos.getSelectedIndex()!=2 || p.costoCompleto())){
                double valor=switch(ordenProductos.getSelectedIndex()){
                    case 1->p.facturacion().doubleValue();
                    case 2->p.gananciaBrutaConocida().doubleValue();
                    default->p.unidades();
                };
                barras.add(new GraficoAnalisis.Punto(p.codigo().length()>12?p.codigo().substring(0,12):p.codigo(),
                    Math.max(0,valor)));
            }
        }
        String titulo=switch(ordenProductos.getSelectedIndex()){
            case 1->"Top productos por facturación nominal ($)";
            case 2->"Top productos por ganancia conocida* ($)";
            default->"Top productos por unidades vendidas";
        };
        graficoProductos.mostrar(titulo,barras);
    }
    /** A 3/6 meses se agrupa semanalmente para evitar cientos de etiquetas superpuestas. */
    public static List<GraficoAnalisis.Punto> serieVentas(Informe datos,boolean porImporte,PeriodoAnalisis periodo){
        if(periodo==PeriodoAnalisis.UN_MES){
            List<GraficoAnalisis.Punto> puntos=new ArrayList<>();
            for(Dia d:datos.dias())puntos.add(new GraficoAnalisis.Punto(
                String.format("%02d/%02d",d.fecha().getDayOfMonth(),d.fecha().getMonthValue()),
                porImporte?d.facturacion().doubleValue():d.ventas()));
            return puntos;
        }
        Map<LocalDate,Double> semanas=new TreeMap<>();
        for(Dia d:datos.dias()){
            LocalDate lunes=d.fecha().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            semanas.merge(lunes,porImporte?d.facturacion().doubleValue():(double)d.ventas(),Double::sum);
        }
        List<GraficoAnalisis.Punto> puntos=new ArrayList<>();
        for(var entrada:semanas.entrySet())puntos.add(new GraficoAnalisis.Punto(
            String.format("%02d/%02d",entrada.getKey().getDayOfMonth(),entrada.getKey().getMonthValue()),
            entrada.getValue()));
        return puntos;
    }
    private void actualizarGraficoDias(Informe datos){
        boolean dinero=metricaDias.getSelectedIndex()==0;
        PeriodoAnalisis periodo=periodos[0];
        graficoDias.mostrar(dinero?"Facturación ($) · "+periodo.etiqueta():"Cantidad de ventas · "+periodo.etiqueta(),
            serieVentas(datos,dinero,periodo));
    }
    private void actualizarGraficoHoras(Informe datos){
        boolean dinero=metricaHoras.getSelectedIndex()==1;
        List<GraficoAnalisis.Punto> puntos=new ArrayList<>();
        for(Hora h:datos.horas())puntos.add(new GraficoAnalisis.Punto(String.format("%02d",h.hora()),
            dinero?h.facturacion().doubleValue():h.ventas()));
        graficoHoras.mostrar(dinero?"Facturación por hora ($)":"Operaciones por hora",puntos);
    }
    private void exportarVisible(){
        if(actual==null || cargando){JOptionPane.showMessageDialog(this,"Esperá a que finalice la consulta.");return;}
        JTable t=switch(secciones.getSelectedIndex()){
            case 0->tDias;case 1->tHoras;case 2->tProductos;
            case 3->tOportunidades;case 4->tVendedores;default->tPagos;
        };
        JFileChooser archivo=new JFileChooser();
        archivo.setSelectedFile(new java.io.File("sigam_analisis_"+periodos[secciones.getSelectedIndex()].meses()+"m.csv"));
        if(archivo.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path destino=archivo.getSelectedFile().toPath();
        if(Files.exists(destino) && JOptionPane.showConfirmDialog(this,"¿Sobrescribir el archivo existente?",
            "Confirmar",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try(java.io.BufferedWriter salida=Files.newBufferedWriter(destino,StandardCharsets.UTF_8)){
            salida.write('\ufeff'); // Excel: BOM UTF-8 y delimitador punto y coma
            List<String> fila=new ArrayList<>();
            for(int c=0;c<t.getColumnCount();c++)fila.add(t.getColumnName(c));
            escribirCsv(salida,fila);
            for(int r=0;r<t.getRowCount();r++){
                fila.clear();
                for(int c=0;c<t.getColumnCount();c++){
                    Object v=t.getValueAt(r,c);
                    fila.add(v==null?"":v instanceof BigDecimal b?b.toPlainString():v.toString());
                }
                escribirCsv(salida,fila);
            }
            JOptionPane.showMessageDialog(this,"CSV exportado correctamente.");
        }catch(Exception e){JOptionPane.showMessageDialog(this,"Error de exportación: "+e.getMessage(),
            "No se pudo exportar",JOptionPane.ERROR_MESSAGE);}
    }
    private static void escribirCsv(java.io.Writer salida,List<String> fila)throws java.io.IOException {
        for(int i=0;i<fila.size();i++){
            if(i>0)salida.write(';');
            salida.write('"');salida.write(fila.get(i).replace("\"","\"\""));salida.write('"');
        }
        salida.write("\r\n");
    }
}
