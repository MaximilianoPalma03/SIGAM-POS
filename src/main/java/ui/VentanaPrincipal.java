package ar.com.sigampos.ui;

import ar.com.sigampos.modelo.*;
import ar.com.sigampos.servicio.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import ar.com.sigampos.util.Moneda;
import ar.com.sigampos.util.CampoMoneda;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.List;

/** Interfaz Swing inspirada en la distribución de trabajo de un POS, no en recursos de terceros. */
public final class VentanaPrincipal extends JFrame {
    private final ProductoServicio productos=new ProductoServicio();
    private final VentaServicio ventas=new VentaServicio();
    private final CajaServicio cajas=new CajaServicio();
    private final PersonalServicio personal=new PersonalServicio();
    private final TurnoServicio turnos=new TurnoServicio();
    private final AnalisisServicio analisis=new AnalisisServicio();
    private final HistorialVentasServicio historiales=new HistorialVentasServicio();
    private final JornadasServicio jornadas=new JornadasServicio();
    private final AlertasServicio alertas=new AlertasServicio();
    private final CajaDigitalServicio digital=new CajaDigitalServicio();
    private final JButton botonAlertas=new JButton("Avisos de stock");
    private JTable vistaProductos;
    private JTextField filtroProductos;
    private final List<JButton> accionesProductos=new ArrayList<>();
    private PersonalServicio.Sesion sesion;
    private final List<LineaVenta> carrito=new ArrayList<>();
    private final DefaultTableModel tablaCarrito=new DefaultTableModel(new Object[]{"Código","Producto","Cantidad","Unitario","Subtotal"},0) {
        @Override public boolean isCellEditable(int r,int c){return false;}
    };
    private final DefaultTableModel tablaProductos=new DefaultTableModel(new Object[]{"Código","Producto","Precio","Stock","Estado"},0) {
        @Override public boolean isCellEditable(int r,int c){return false;}
    };
    private final JTextField caja=new JTextField(6);
    private final DefaultTableModel tablaTurnos=new DefaultTableModel(new Object[]{"ID","Nombre","Inicio","Fin","Días","Duración","Estado","Empleados asignados"},0) {
        @Override public boolean isCellEditable(int r,int c){return false;}
    };
    private final JLabel total=new JLabel("TOTAL $ 0,00");
    private final DefaultTableModel tablaCatalogo=new DefaultTableModel(new Object[]{"Código","Producto","Precio","Stock"},0) {
        @Override public boolean isCellEditable(int r,int c){return false;}
    };
    private final DefaultTableModel tablaEmpleados=new DefaultTableModel(new Object[]{"ID","Empleado","Login","Estado","Horarios"},0) {
        @Override public boolean isCellEditable(int r,int c){return false;}
    };
    private final JLabel sesionLabel=new JLabel();
    private final JTabbedPane pestañas=new JTabbedPane();

    public VentanaPrincipal() {
        super("SIGAM-POS — Terminal de ventas");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosing(java.awt.event.WindowEvent e){salirDelPrograma();}});setSize(1200,760);setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        caja.setEditable(false);
        var cabecera=new JPanel(new BorderLayout());cabecera.setBackground(new Color(30,97,155));
        JLabel titulo=new JLabel(" SIGAM-POS  |  Terminal de mostrador");titulo.setFont(titulo.getFont().deriveFont(Font.BOLD,20f));titulo.setForeground(Color.WHITE);
        cabecera.add(titulo,BorderLayout.WEST);sesionLabel.setForeground(Color.WHITE);
        JPanel acceso=new JPanel(new FlowLayout(FlowLayout.RIGHT));acceso.setOpaque(false);
        JButton cambiarClave=new JButton("Mis credenciales"),cerrarSesion=new JButton("Cerrar sesión");
        acceso.add(sesionLabel);acceso.add(cambiarClave);acceso.add(cerrarSesion);
        cabecera.add(acceso,BorderLayout.EAST);
        cambiarClave.addActionListener(e->ejecutar(()->editarMisCredenciales()));
        cerrarSesion.addActionListener(e->cerrarSesion());
        cabecera.setBorder(BorderFactory.createEmptyBorder(12,8,12,16));add(cabecera,BorderLayout.NORTH);
        pestañas.addTab("Ventas",panelVentas());pestañas.addTab("Productos",panelProductos());
        pestañas.addTab("Caja",panelCaja());pestañas.addTab("Personal / Turnos",panelPersonal());pestañas.addTab("Análisis",panelAnalisis());
        add(pestañas,BorderLayout.CENTER);
        pestañas.addChangeListener(e->{
            if(sesion!=null && pestañas.getSelectedIndex()==0)
                ejecutar(()->cargarCatalogo(""));
        });
        SwingUtilities.invokeLater(()->{
            try {
                if(personal.crearAdminInicialSiFalta()) mensaje("Primera instalación: usuario Admin y contraseña temporal 1234. Cambiala desde Mis credenciales.");
                if(!iniciarSesion()) {dispose();return;}
                actualizarPermisos();
                cargarProductos("");cargarCatalogo("");actualizarAlertas();
            } catch(Exception e) {error(e);dispose();}
        });
    }
    private boolean iniciarSesion(){
        while(true){
            JTextField usuario=new JTextField();JPasswordField clave=new JPasswordField();
            JPanel panel=form("Usuario",usuario,"Contraseña",clave);
            int opcion=JOptionPane.showConfirmDialog(this,panel,"Inicio de sesión",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
            if(opcion!=JOptionPane.OK_OPTION)return false;
            char[] temporal=clave.getPassword();
            try {
                PersonalServicio.Sesion candidata=personal.autenticar(usuario.getText(),new String(temporal));
                if(candidata.debeCambiarClave()) {
                    if(!cambiarClaveObligatoria(candidata.empleadoId(),new String(temporal)))continue;
                    candidata=new PersonalServicio.Sesion(candidata.empleadoId(),candidata.nombre(),candidata.rol(),false);
                }
                sesion=candidata;return true;
            } catch(Exception e){error(e);}
            finally{java.util.Arrays.fill(temporal,'\0');}
        }
    }
    private void actualizarPermisos(){
        sesionLabel.setText(sesion.nombre()+"  |  "+sesion.rol()+"  ");
        pestañas.setEnabledAt(1,true); // Los vendedores consultan el stock en solo lectura.
        for(JButton b:accionesProductos)b.setEnabled(sesion.administrador());
        pestañas.setEnabledAt(3,sesion.administrador());
        pestañas.setEnabledAt(4,sesion.administrador());
        pestañas.setSelectedIndex(0);
        try {
            Long activa=cajas.cajaAbiertaDe(sesion.empleadoId());
            caja.setText(activa==null?"":activa.toString());
        } catch(SQLException e) {caja.setText("");error(e);}
    }
    private boolean confirmarSalir(){
        if(carrito.isEmpty())return true;
        return JOptionPane.showConfirmDialog(this,
            "Hay una venta sin finalizar. Si continuás, se descartará el carrito (no se registró ningún cobro). ¿Continuar?",
            "Venta pendiente",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION;
    }
    private void cerrarSesion(){
        if(!confirmarSalir())return;
        carrito.clear();refrescarCarrito();sesion=null;
        if(iniciarSesion()){actualizarPermisos();try{cargarProductos("");cargarCatalogo("");}catch(SQLException e){error(e);}}
        else dispose();
    }
    private void salirDelPrograma(){if(confirmarSalir()){carrito.clear();sesion=null;dispose();}}
    /** El formulario solo se cierra al guardar correctamente o cancelar. */
    private boolean cambiarClaveObligatoria(long empleadoId,String temporal) {
        JPasswordField nueva=new JPasswordField(),repetir=new JPasswordField();
        JPanel panel=form("Nueva contraseña (8+ caracteres)",nueva,"Confirmar nueva contraseña",repetir);
        while(true) {
            int opcion=JOptionPane.showConfirmDialog(this,panel,
                "Cambio obligatorio de contraseña",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE);
            if(opcion!=JOptionPane.OK_OPTION)return false;
            char[] a=nueva.getPassword(),b=repetir.getPassword();
            try {
                if(!java.util.Arrays.equals(a,b))
                    throw new IllegalArgumentException("Las contraseñas no coinciden");
                personal.cambiarClaveObligatoria(empleadoId,temporal,new String(a));
                mensaje("Contraseña actualizada. Podés utilizar el sistema.");
                return true;
            } catch(Exception e) {
                error(e);nueva.setText("");repetir.setText("");
            } finally {java.util.Arrays.fill(a,'\0');java.util.Arrays.fill(b,'\0');}
        }
    }
    private void editarMisCredenciales() throws SQLException {
        JTextField usuario=new JTextField(personal.nombreUsuario(sesion.empleadoId()));
        JPasswordField actual=new JPasswordField(),clave=new JPasswordField(),repetir=new JPasswordField();
        JPanel panel=form("Nombre de usuario",usuario,"Contraseña actual",actual,
            "Nueva contraseña (8+)",clave,"Repetir nueva contraseña",repetir);
        while(true) {
            if(JOptionPane.showConfirmDialog(this,panel,"Mis credenciales",
                JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            char[] anterior=actual.getPassword(),a=clave.getPassword(),b=repetir.getPassword();
            try {
                if(!java.util.Arrays.equals(a,b))
                    throw new IllegalArgumentException("Las contraseñas no coinciden");
                personal.cambiarCredencialesSeguro(sesion.empleadoId(),new String(anterior),
                    usuario.getText(),new String(a));
                mensaje("Credenciales actualizadas. Se aplicarán en el próximo inicio de sesión.");
                return;
            } catch(Exception e) {
                error(e);actual.setText("");clave.setText("");repetir.setText("");
            } finally {
                java.util.Arrays.fill(anterior,'\0');java.util.Arrays.fill(a,'\0');
                java.util.Arrays.fill(b,'\0');
            }
        }
    }
    private JPanel panelVentas(){
        JPanel raiz=new JPanel(new BorderLayout(10,10));raiz.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JTextField buscar=new JTextField(24); JSpinner cantidad=new JSpinner(new SpinnerNumberModel(1,1,999999,1));
        JPanel superior=new JPanel(new FlowLayout(FlowLayout.LEFT,8,4));
        superior.add(new JLabel("Buscar por nombre / código:"));superior.add(buscar);
        superior.add(new JLabel("Cantidad:"));superior.add(cantidad);
        JButton agregar=new JButton("Agregar seleccionado"),quitar=new JButton("Quitar del carrito");
        superior.add(agregar);superior.add(quitar);superior.add(botonAlertas);
        botonAlertas.addActionListener(e->ejecutar(()->abrirAlertas()));
        raiz.add(superior,BorderLayout.NORTH);

        JTable catalogo=new JTable(tablaCatalogo);catalogo.setRowHeight(25);
        catalogo.setAutoCreateRowSorter(true);catalogo.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JTable tabla=new JTable(tablaCarrito);tabla.setRowHeight(25);
        JSplitPane medio=new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JPanel arriba=new JPanel(new BorderLayout());arriba.add(new JLabel(" Productos disponibles (doble clic para agregar)"),BorderLayout.NORTH);
        arriba.add(new JScrollPane(catalogo),BorderLayout.CENTER);
        JPanel abajo=new JPanel(new BorderLayout());abajo.add(new JLabel(" Carrito actual"),BorderLayout.NORTH);
        abajo.add(new JScrollPane(tabla),BorderLayout.CENTER);
        medio.setTopComponent(arriba);medio.setBottomComponent(abajo);medio.setResizeWeight(0.48);
        SwingUtilities.invokeLater(()->medio.setDividerLocation(0.48));
        raiz.add(medio,BorderLayout.CENTER);

        JPanel inferior=new JPanel(new BorderLayout());total.setFont(total.getFont().deriveFont(Font.BOLD,26f));inferior.add(total,BorderLayout.WEST);
        JPanel acciones=new JPanel(new FlowLayout(FlowLayout.RIGHT));acciones.add(new JLabel("Caja activa:"));acciones.add(caja);
        JButton cobrar=new JButton("COBRAR");cobrar.setFont(cobrar.getFont().deriveFont(Font.BOLD,16f));acciones.add(cobrar);
        inferior.add(acciones,BorderLayout.EAST);raiz.add(inferior,BorderLayout.SOUTH);
        Runnable cargar=()->ejecutar(()->cargarCatalogo(buscar.getText()));
        alCambiar(buscar,cargar); // Actualiza resultados sin exigir código exacto.
        agregar.addActionListener(e->ejecutar(()->agregarDeCatalogo(catalogo,cantidad)));
        buscar.addActionListener(e->ejecutar(()->{
            String valor=buscar.getText().trim();
            for(int i=0;i<catalogo.getRowCount();i++){
                if(catalogo.getValueAt(i,0).toString().equalsIgnoreCase(valor)){
                    catalogo.setRowSelectionInterval(i,i);agregarDeCatalogo(catalogo,cantidad);buscar.setText("");return;
                }
            }
            if(catalogo.getRowCount()==1){catalogo.setRowSelectionInterval(0,0);agregarDeCatalogo(catalogo,cantidad);buscar.setText("");}
        }));
        catalogo.addMouseListener(new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent e){
            if(e.getClickCount()==2)ejecutar(()->agregarDeCatalogo(catalogo,cantidad));
        }});
        quitar.addActionListener(e->{int fila=tabla.getSelectedRow();if(fila>=0){carrito.remove(fila);refrescarCarrito();}});
        cobrar.addActionListener(e->ejecutar(()->cobrar()));
        return raiz;
    }
    private void cargarCatalogo(String filtro) throws SQLException {
        tablaCatalogo.setRowCount(0);
        for(Producto p:productos.listar(filtro))if(p.activo() && p.stock()>0)
            tablaCatalogo.addRow(new Object[]{p.codigo(),p.nombre(),Moneda.formato(p.precioVenta()),p.stock()});
        if(sesion!=null)actualizarAlertas();
    }
    private void agregarDeCatalogo(JTable catalogo,JSpinner cantidad) throws SQLException {
        int fila=catalogo.getSelectedRow();
        if(fila<0)throw new IllegalArgumentException("Seleccioná un producto del catálogo");
        String codigo=String.valueOf(catalogo.getValueAt(fila,0));
        Producto p=productos.buscar(codigo);
        if(p==null || !p.activo())throw new IllegalArgumentException("Producto inexistente o inactivo");
        int q=((Number)cantidad.getValue()).intValue();
        int anteriores=carrito.stream().filter(l->l.producto().codigo().equals(p.codigo())).mapToInt(LineaVenta::cantidad).sum();
        if(anteriores+q>p.stock())throw new IllegalArgumentException("Stock insuficiente. Disponibles: "+p.stock());
        int indice=-1;
        for(int j=0;j<carrito.size();j++)if(carrito.get(j).producto().codigo().equals(p.codigo())){indice=j;break;}
        if(indice>=0)carrito.set(indice,new LineaVenta(p,anteriores+q));
        else carrito.add(new LineaVenta(p,q));
        refrescarCarrito();cantidad.setValue(1);
    }
    private BigDecimal totalCarrito(){return carrito.stream().map(LineaVenta::subtotal).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private void refrescarCarrito(){tablaCarrito.setRowCount(0);for(LineaVenta l:carrito)tablaCarrito.addRow(new Object[]{l.producto().codigo(),l.producto().nombre(),l.cantidad(),Moneda.formato(l.producto().precioVenta()),Moneda.formato(l.subtotal())});total.setText("TOTAL "+Moneda.formato(totalCarrito()));}
    private void cobrar()throws Exception {
        if(carrito.isEmpty())throw new IllegalArgumentException("Carrito vacío");
        long cajaId=cajaActual(); // Validar ANTES del diálogo de cobro (INT-03).
        JPanel panel=new JPanel(new GridLayout(5,2,8,8));
        panel.add(new JLabel("TOTAL:"));panel.add(new JLabel(Moneda.formato(totalCarrito())));
        Map<MedioPago,CampoMoneda> importes=new LinkedHashMap<>();
        for(MedioPago medio:MedioPago.values()){
            CampoMoneda tf=new CampoMoneda();importes.put(medio,tf);
            panel.add(new JLabel(medio.name()));panel.add(tf);
        }
        if(JOptionPane.showConfirmDialog(this,panel,"Finalizar venta - pagos divididos",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        List<PagoVenta> pagos=new ArrayList<>();
        for(var entry:importes.entrySet()){BigDecimal m=importe(entry.getValue());if(m.signum()>0)pagos.add(new PagoVenta(entry.getKey(),m));}
        if(pagos.stream().anyMatch(x->x.medio()==MedioPago.TRANSFERENCIA||x.medio()==MedioPago.QR)
            && !digital.habilitada(cajaId))
            throw new IllegalArgumentException("Para cobrar con transferencia o QR debés abrir Mercado Pago en Caja al iniciar la jornada.");
        long id=ventas.confirmar(cajaId,sesion.empleadoId(),List.copyOf(carrito),pagos);
        carrito.clear();refrescarCarrito();cargarProductos("");cargarCatalogo("");mensaje("Venta #"+id+" registrada y stock actualizado");
    }
    private JPanel panelProductos(){
        JPanel raiz=new JPanel(new BorderLayout(8,8));raiz.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JTextField filtro=new JTextField(22);filtroProductos=filtro;
        JPanel arriba=new JPanel(new FlowLayout(FlowLayout.LEFT));
        arriba.add(new JLabel("Buscar nombre o código:"));arriba.add(filtro);
        JButton nuevo=new JButton("Crear producto"),editar=new JButton("Editar seleccionado"),baja=new JButton("Desactivar"),activar=new JButton("Reactivar");
        for(JButton b:new JButton[]{nuevo,editar,baja,activar}){arriba.add(b);accionesProductos.add(b);}
        raiz.add(arriba,BorderLayout.NORTH);
        JTable tabla=new JTable(tablaProductos);vistaProductos=tabla;tabla.setRowHeight(27);tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setAutoCreateRowSorter(true);raiz.add(new JScrollPane(tabla),BorderLayout.CENTER);
        alCambiar(filtro,()->ejecutar(()->cargarProductos(filtro.getText())));
        nuevo.addActionListener(e->ejecutar(()->editarProducto(null,filtro.getText())));
        editar.addActionListener(e->ejecutar(()->editarProducto(productoSeleccionado(tabla),filtro.getText())));
        tabla.addMouseListener(new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent e){
            if(e.getClickCount()==2 && sesion!=null && sesion.administrador())
                ejecutar(()->editarProducto(productoSeleccionado(tabla),filtro.getText()));
        }});
        baja.addActionListener(e->ejecutar(()->{
            Producto p=productoSeleccionado(tabla);
            if(!p.activo())throw new IllegalArgumentException("El producto ya está inactivo");
            if(JOptionPane.showConfirmDialog(this,"¿Desactivar "+p.nombre()+"?","Confirmación",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
            productos.desactivar(p.codigo());cargarProductos(filtro.getText());cargarCatalogo("");
        }));
        activar.addActionListener(e->ejecutar(()->{
            Producto p=productoSeleccionado(tabla);
            if(p.activo())throw new IllegalArgumentException("El producto ya está activo");
            productos.reactivar(p.codigo());cargarProductos(filtro.getText());cargarCatalogo("");
        }));
        return raiz;
    }
    private Producto productoSeleccionado(JTable tabla) throws SQLException {
        int fila=tabla.getSelectedRow();
        if(fila<0)throw new IllegalArgumentException("Seleccioná un producto de la tabla");
        String codigo=tabla.getValueAt(fila,0).toString();
        Producto p=productos.buscar(codigo);
        if(p==null)throw new IllegalArgumentException("Producto inexistente");
        return p;
    }
    private void cargarProductos(String filtro)throws SQLException {
        tablaProductos.setRowCount(0);
        for(Producto p:productos.listar(filtro))tablaProductos.addRow(new Object[]{p.codigo(),p.nombre(),Moneda.formato(p.precioVenta()),p.stock(),p.activo()?"ACTIVO":"INACTIVO"});
    }
    private void editarProducto(Producto anterior,String filtro)throws Exception {
        JTextField codigo=new JTextField(anterior==null?"":anterior.codigo()),nombre=new JTextField(anterior==null?"":anterior.nombre()),
            precio=new CampoMoneda(anterior==null?BigDecimal.ZERO:anterior.precioVenta()),
            costo=new CampoMoneda(anterior==null?BigDecimal.ZERO:anterior.costo()),
            stock=new JTextField(anterior==null?"0":String.valueOf(anterior.stock())),
            min=new JTextField(anterior==null||anterior.stockMinimo()==null?"":anterior.stockMinimo().toString()),
            max=new JTextField(anterior==null||anterior.stockMaximo()==null?"":anterior.stockMaximo().toString());
        codigo.setEditable(anterior==null);
        JPanel panel=form("Código de producto",codigo,"Nombre",nombre,"Precio venta ($)",precio,
            "Costo ($)",costo,"Stock",stock,"Stock mínimo (opcional)",min,"Stock máximo (opcional)",max);
        if(JOptionPane.showConfirmDialog(this,panel,anterior==null?"Crear producto":"Editar producto",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        String clave=codigo.getText().trim();
        if(anterior==null && productos.buscar(clave)!=null)
            throw new IllegalArgumentException("Ya existe un producto con ese código. Seleccionalo y elegí Editar.");
        Producto p=new Producto(clave,nombre.getText(),importe(precio),importe(costo),enteroNoNegativo(stock,"Stock"),enteroOpcional(min),enteroOpcional(max),anterior==null||anterior.activo());
        productos.guardar(p);cargarProductos(filtro);cargarCatalogo("");mensaje("Producto guardado correctamente");
    }
    private JPanel panelCaja(){
        JPanel raiz=new JPanel(new BorderLayout(12,12));raiz.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        JLabel estado=new JLabel("Sin caja activa");estado.setFont(estado.getFont().deriveFont(Font.BOLD,16f));
        JPanel encabezado=new JPanel(new BorderLayout(6,6));encabezado.add(new JLabel("CAJA · Jornada laboral"),BorderLayout.NORTH);
        encabezado.add(estado,BorderLayout.CENTER);raiz.add(encabezado,BorderLayout.NORTH);
        JTextArea resumen=new JTextArea(9,45);resumen.setEditable(false);resumen.setFont(new Font(Font.MONOSPACED,Font.PLAIN,14));
        resumen.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        JPanel centro=new JPanel(new BorderLayout(8,8));centro.add(new JLabel("Resumen de efectivo y Mercado Pago"),BorderLayout.NORTH);
        centro.add(new JScrollPane(resumen),BorderLayout.CENTER);raiz.add(centro,BorderLayout.CENTER);
        JPanel acciones=new JPanel(new FlowLayout(FlowLayout.LEFT,10,8));
        JButton abrir=new JButton("Iniciar jornada / Abrir caja"),entrada=new JButton("Registrar entrada (+)"),salida=new JButton("Registrar salida (-)"),
            esperado=new JButton("Actualizar desglose"),historial=new JButton("Historial de movimientos"),
            movimientoDigital=new JButton("Movimiento Mercado Pago"),cerrar=new JButton("Finalizar jornada (arqueo)");
        for(JButton b:new JButton[]{abrir,entrada,salida,movimientoDigital,esperado,historial,cerrar})acciones.add(b);
        JPanel pie=new JPanel(new BorderLayout());pie.add(acciones,BorderLayout.CENTER);
        pie.add(new JLabel("Cada movimiento solicita un importe y un motivo en un formulario independiente."),BorderLayout.SOUTH);
        raiz.add(pie,BorderLayout.SOUTH);
        Runnable actualizar=()->{
            if(caja.getText().isBlank()) {estado.setText("Sin caja activa · Iniciá una jornada para operar");resumen.setText("No hay caja abierta para esta sesión.");return;}
            try {estado.setText("Caja #"+caja.getText()+" · Empleado: "+sesion.nombre());
                String texto=desglose(cajas.resumen(cajaActual()));
                CajaDigitalServicio.Resumen d=digital.resumen(cajaActual());
                resumen.setText(texto+"\n\n"+desgloseDigital(d));
            } catch(Exception e){resumen.setText("No se pudo actualizar el resumen: "+e.getMessage());}
        };
        caja.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e){SwingUtilities.invokeLater(actualizar);}
            public void removeUpdate(DocumentEvent e){SwingUtilities.invokeLater(actualizar);}
            public void changedUpdate(DocumentEvent e){SwingUtilities.invokeLater(actualizar);}
        });
        abrir.addActionListener(e->ejecutar(()->{
            if(!caja.getText().isBlank())throw new IllegalStateException("Ya tenés una caja abierta");
            List<Turno> asignados=turnos.asignados(sesion.empleadoId()).stream()
                .filter(t->t.activo() && t.dias().contains(DiaSemana.desde(LocalDate.now().getDayOfWeek()))).toList();
            if(asignados.isEmpty())throw new IllegalArgumentException("No tenés turnos activos asignados para hoy.");
            JComboBox<TurnoOpcion> lista=new JComboBox<>();
            for(Turno t:asignados)lista.addItem(new TurnoOpcion(t));
            CampoMoneda fondo=new CampoMoneda();
            JCheckBox usarMp=new JCheckBox("Abrir también Mercado Pago (declarativo, sin API)");
            CampoMoneda saldoMp=new CampoMoneda();saldoMp.setEnabled(false);
            usarMp.addActionListener(ev->saldoMp.setEnabled(usarMp.isSelected()));
            JPanel formulario=form("Turno de hoy",lista,"Fondo inicial en efectivo",fondo,
                "Caja digital",usarMp,"Saldo inicial Mercado Pago",saldoMp);
            if(JOptionPane.showConfirmDialog(this,formulario,"Iniciar jornada",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            TurnoOpcion elegido=(TurnoOpcion)lista.getSelectedItem();
            long id=cajas.abrir(elegido.turno().id(),sesion.empleadoId(),importe(fondo),
                usarMp.isSelected(),usarMp.isSelected()?importe(saldoMp):BigDecimal.ZERO);
            caja.setText(String.valueOf(id));actualizar.run();mensaje("Jornada iniciada. Caja #"+id+" abierta.");
        }));
        entrada.addActionListener(e->ejecutar(()->{registrarMovimiento(true);actualizar.run();}));
        salida.addActionListener(e->ejecutar(()->{registrarMovimiento(false);actualizar.run();}));
        esperado.addActionListener(e->ejecutar(()->{cajaActual();actualizar.run();}));
        movimientoDigital.addActionListener(e->ejecutar(()->{registrarDigital();actualizar.run();}));
        historial.addActionListener(e->ejecutar(()->{
            long id=cajaActual();String datos=cajas.historial(id);
            if(digital.habilitada(id))datos+="\n\n"+digital.historial(id);
            JTextArea area=new JTextArea(datos);area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
            JOptionPane.showMessageDialog(this,new JScrollPane(area),"Movimientos de caja #"+id,JOptionPane.INFORMATION_MESSAGE);
        }));
        cerrar.addActionListener(e->ejecutar(()->{finalizarJornada();actualizar.run();}));
        return raiz;
    }
    private void registrarMovimiento(boolean esEntrada) throws SQLException {
        long id=cajaActual();
        JTextField monto=new CampoMoneda(),motivo=new JTextField();
        String tipo=esEntrada?"entrada":"salida";
        JPanel formulario=form("Importe de "+tipo+" ($)",monto,"Motivo obligatorio",motivo);
        if(JOptionPane.showConfirmDialog(this,formulario,"Registrar "+tipo,JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        BigDecimal valor=importe(monto);
        if(valor.signum()<=0)throw new IllegalArgumentException("El importe debe ser mayor que cero");
        String descripcion=motivo.getText().trim();
        if(descripcion.isBlank())throw new IllegalArgumentException("Indicá un motivo para este movimiento");
        if(descripcion.length()>255)throw new IllegalArgumentException("Motivo demasiado largo (máximo 255 caracteres)");
        if(JOptionPane.showConfirmDialog(this,"Confirmar "+tipo+" de "+Moneda.formato(valor)+"\nMotivo: "+descripcion,
            "Confirmar movimiento",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        cajas.registrar(id,sesion.empleadoId(),esEntrada?new IngresoCaja(valor,descripcion,false):new EgresoCaja(valor,descripcion));
        mensaje("Movimiento registrado: "+Moneda.formato(valor));
    }
    private void registrarDigital() throws SQLException {
        long id=cajaActual();
        if(!digital.habilitada(id))throw new IllegalStateException("No está habilitada la caja digital de Mercado Pago");
        JComboBox<String> tipo=new JComboBox<>(new String[]{"ENTRADA","SALIDA"});
        CampoMoneda monto=new CampoMoneda();JTextField motivo=new JTextField();
        JPanel panel=form("Movimiento",tipo,"Importe",monto,"Motivo",motivo);
        if(JOptionPane.showConfirmDialog(this,panel,"Movimiento manual Mercado Pago",JOptionPane.OK_CANCEL_OPTION)
            !=JOptionPane.OK_OPTION)return;
        BigDecimal valor=monto.valor();
        if(valor.signum()<=0)throw new IllegalArgumentException("El importe debe ser mayor que cero");
        if(motivo.getText().isBlank())throw new IllegalArgumentException("Motivo obligatorio");
        if(JOptionPane.showConfirmDialog(this,"Confirmar "+tipo.getSelectedItem()+" en Mercado Pago por "+Moneda.formato(valor)+"?",
            "Confirmación",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        digital.registrarMovimientoManual(id,sesion.empleadoId(),"ENTRADA".equals(tipo.getSelectedItem()),valor,motivo.getText());
    }
    private String desgloseDigital(CajaDigitalServicio.Resumen d){
        if(d==null)return "MERCADO PAGO: No habilitado en esta jornada";
        return "MERCADO PAGO (declarado, sin conexion)\nSaldo inicial: "+Moneda.formato(d.inicial())+
            "\nCobros por transferencia/QR: "+Moneda.formato(d.cobros())+
            "\nEntradas manuales: "+Moneda.formato(d.entradas())+
            "\nSalidas manuales: "+Moneda.formato(d.salidas())+
            "\nSALDO DIGITAL ESPERADO: "+Moneda.formato(d.esperado());
    }
    private record TurnoOpcion(Turno turno) {
        @Override public String toString(){return turno.nombre()+" ("+turno.inicio()+"–"+turno.fin()+", "+turno.descripcionDias()+")";}
    }
    private long cajaActual(){
        if(caja.getText().isBlank())throw new IllegalStateException("No hay caja activa. Iniciá tu jornada en Caja.");
        return Long.parseLong(caja.getText());
    }
    private String desglose(CajaServicio.Resumen r){
        return "Fondo inicial: "+Moneda.formato(r.fondoInicial())+"\nVentas en efectivo: "+Moneda.formato(r.ventasEfectivo())+
            "\nEntradas manuales: "+Moneda.formato(r.entradas())+"\nSalidas manuales: "+Moneda.formato(r.salidas())+
            "\n-------------------------\nEFECTIVO ESPERADO: "+Moneda.formato(r.esperado());
    }
    private void finalizarJornada() throws SQLException {
        if(!confirmarSalir())return;
        long id=cajaActual();
        CajaServicio.Resumen r=cajas.resumen(id);
        CajaDigitalServicio.Resumen d=digital.resumen(id);
        CampoMoneda contado=new CampoMoneda(r.esperado()),proximo=new CampoMoneda(),
            digitalContado=new CampoMoneda(d==null?BigDecimal.ZERO:d.esperado()),digitalProximo=new CampoMoneda();
        JTextField observaciones=new JTextField();
        JPanel p=new JPanel(new BorderLayout(8,8));
        JTextArea detalle=new JTextArea(desglose(r)+"\n\n"+desgloseDigital(d));
        detalle.setEditable(false);detalle.setRows(12);detalle.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
        p.add(new JScrollPane(detalle),BorderLayout.NORTH);
        JPanel efectivo=form("Efectivo contado",contado,"Fondo para siguiente turno",proximo);
        JButton billetes=new JButton("Contar billetes y monedas");
        billetes.addActionListener(ev->{
            BigDecimal valor=contarBilletes();if(valor!=null)contado.setText(Moneda.formato(valor));
        });
        JPanel centro=new JPanel(new BorderLayout(8,6));centro.add(efectivo,BorderLayout.NORTH);
        centro.add(billetes,BorderLayout.CENTER);
        if(d!=null)centro.add(form("Saldo Mercado Pago declarado",digitalContado,
            "Fondo digital próximo turno",digitalProximo),BorderLayout.SOUTH);
        p.add(centro,BorderLayout.CENTER);
        p.add(form("Observaciones",observaciones),BorderLayout.SOUTH);
        if(JOptionPane.showConfirmDialog(this,p,"Cierre y arqueo",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        BigDecimal diferencia=cajas.cerrar(id,sesion.empleadoId(),contado.valor(),proximo.valor(),
            observaciones.getText(),d==null?null:digitalContado.valor(),d==null?null:digitalProximo.valor());
        caja.setText("");carrito.clear();refrescarCarrito();
        mensaje("Jornada cerrada. Diferencia de efectivo: "+Moneda.formato(diferencia)+". Se cerrará tu sesión.");
        cerrarSesion();
    }
    private BigDecimal contarBilletes(){
        int[] denominaciones={20000,10000,2000,1000,500,200,100,50,20,10};
        JPanel panel=new JPanel(new GridLayout(0,2,6,6));
        List<JSpinner> campos=new ArrayList<>();
        for(int denominacion:denominaciones){
            JSpinner cantidad=new JSpinner(new SpinnerNumberModel(0,0,100000,1));campos.add(cantidad);
            panel.add(new JLabel(Moneda.formato(BigDecimal.valueOf(denominacion))));panel.add(cantidad);
        }
        CampoMoneda otros=new CampoMoneda();panel.add(new JLabel("Otros / monedas"));panel.add(otros);
        if(JOptionPane.showConfirmDialog(this,new JScrollPane(panel),"Recuento de billetes",JOptionPane.OK_CANCEL_OPTION)
            !=JOptionPane.OK_OPTION)return null;
        BigDecimal total=otros.valor();
        for(int i=0;i<denominaciones.length;i++)total=total.add(BigDecimal.valueOf(
            (long)denominaciones[i]*((Number)campos.get(i).getValue()).intValue()));
        return total;
    }
    private JTabbedPane panelPersonal(){
        JTabbedPane secciones=new JTabbedPane();
        JPanel contenedor=new JPanel(new GridLayout(0,3,10,10));
        contenedor.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel empleados=new JPanel(new BorderLayout(8,8));
        JButton alta=new JButton("Crear empleado"),recargar=new JButton("Actualizar empleados");
        JPanel cabecera=new JPanel(new FlowLayout(FlowLayout.LEFT));cabecera.add(alta);cabecera.add(recargar);
        empleados.add(cabecera,BorderLayout.NORTH);
        empleados.add(new JScrollPane(contenedor),BorderLayout.CENTER);
        alta.addActionListener(e->ejecutar(()->{
            JTextField nombre=new JTextField(),dni=new JTextField();
            JPanel p=form("Nombre",nombre,"DNI (login temporal)",dni);
            if(JOptionPane.showConfirmDialog(this,p,"Alta de empleado vendedor",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            long id=personal.crearEmpleadoConAccesoDni(nombre.getText(),dni.getText());
            cargarFichasEmpleados(contenedor);
            mensaje("Empleado #"+id+" creado. Usuario temporal: DNI. Contraseña temporal: últimos 4 dígitos. Debe cambiarla.");
        }));
        recargar.addActionListener(e->ejecutar(()->cargarFichasEmpleados(contenedor)));
        secciones.addTab("Empleados",empleados);

        JPanel pantallaTurnos=new JPanel(new BorderLayout(8,8));pantallaTurnos.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel acciones=new JPanel(new FlowLayout(FlowLayout.LEFT,8,8));
        JButton nuevo=new JButton("Crear turno"),editar=new JButton("Modificar turno"),
            asignar=new JButton("Asignar turno"),quitar=new JButton("Quitar asignación"),
            ver=new JButton("Ver empleado asignado"),refrescar=new JButton("Actualizar horarios");
        for(JButton button:new JButton[]{nuevo,editar,asignar,quitar,ver,refrescar})acciones.add(button);
        pantallaTurnos.add(acciones,BorderLayout.NORTH);
        JTable tabla=new JTable(tablaTurnos);tabla.setRowHeight(27);tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.addMouseListener(new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent e){
            if(e.getClickCount()==2 && SwingUtilities.isLeftMouseButton(e))
                ejecutar(()->{gestionarTurno(turnoSeleccionado(tabla));cargarTurnos();cargarFichasEmpleados(contenedor);});
        }});
        tabla.setAutoCreateRowSorter(true);pantallaTurnos.add(new JScrollPane(tabla),BorderLayout.CENTER);
        nuevo.addActionListener(e->ejecutar(()->{gestionarTurno(null);cargarTurnos();cargarFichasEmpleados(contenedor);}));
        editar.addActionListener(e->ejecutar(()->{gestionarTurno(turnoSeleccionado(tabla));cargarTurnos();cargarFichasEmpleados(contenedor);}));
        asignar.addActionListener(e->ejecutar(()->{
            Turno t=turnoSeleccionado(tabla);TurnoServicio.EmpleadoResumen empleado=seleccionarEmpleado();
            turnos.asignar(empleado.id(),t.id());cargarTurnos();cargarFichasEmpleados(contenedor);
            mensaje("Turno '"+t.nombre()+"' asignado a "+empleado.nombre());
        }));
        quitar.addActionListener(e->ejecutar(()->{
            Turno t=turnoSeleccionado(tabla);TurnoServicio.EmpleadoResumen empleado=seleccionarEmpleadoAsignado(t);
            if(JOptionPane.showConfirmDialog(this,"¿Quitar '"+t.nombre()+"' a "+empleado.nombre()+"?", "Confirmar",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
            turnos.quitarAsignacion(empleado.id(),t.id());cargarTurnos();cargarFichasEmpleados(contenedor);
        }));
        ver.addActionListener(e->ejecutar(()->{
            Turno t=turnoSeleccionado(tabla);TurnoServicio.EmpleadoResumen empleado=seleccionarEmpleadoAsignado(t);
            verFicha(empleado.id());
            secciones.setSelectedIndex(0);
        }));
        refrescar.addActionListener(e->ejecutar(()->{cargarTurnos();cargarFichasEmpleados(contenedor);}));
        secciones.addTab("Turnos",pantallaTurnos);
        SwingUtilities.invokeLater(()->{
            try{cargarFichasEmpleados(contenedor);cargarTurnos();}catch(SQLException ex){/* Se carga al conectar la BD */}
        });
        return secciones;
    }
    private void cargarFichasEmpleados(JPanel contenedor) throws SQLException {
        contenedor.removeAll();
        for(PersonalServicio.EmpleadoFicha e:personal.listarEmpleados()) {
            JPanel ficha=new JPanel(new BorderLayout(8,8));
            ficha.setPreferredSize(new Dimension(320,210));
            ficha.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(12,12,12,12)));
            StringBuilder horarios=new StringBuilder("<html>");
            List<Turno> asignados=turnos.asignados(e.id());
            if(asignados.isEmpty())horarios.append("Sin horarios asignados");
            for(Turno t:asignados)horarios.append(escaparHtml(t.nombre())).append(" · ").append(t.inicio()).append("–").append(t.fin())
                .append(" (").append(escaparHtml(t.descripcionDias())).append(")<br>");
            horarios.append("</html>");
            JLabel nombre=new JLabel(e.nombre()+" · "+e.estado());
            nombre.setFont(nombre.getFont().deriveFont(Font.BOLD,15f));ficha.add(nombre,BorderLayout.NORTH);
            JPanel detalle=new JPanel(new GridLayout(0,1,3,3));
            detalle.add(new JLabel("Login: "+e.usuario()));
            detalle.add(new JLabel("Contraseña: •••••••• (protegida, restablecimiento disponible)"));
            detalle.add(new JLabel("DNI: "+e.dni()+" · Rol: "+e.rol()));
            detalle.add(new JLabel("Turnos:"));detalle.add(new JLabel(horarios.toString()));
            ficha.add(detalle,BorderLayout.CENTER);
            JPanel botones=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));
            JButton ver=new JButton("Ver ficha"),reiniciar=new JButton("Restablecer contraseña"),estado=new JButton(e.activo()?"Desactivar":"Reactivar");
            botones.add(ver);botones.add(reiniciar);botones.add(estado);ficha.add(botones,BorderLayout.SOUTH);
            ver.addActionListener(ev->ejecutar(()->verFicha(e.id())));
            reiniciar.addActionListener(ev->ejecutar(()->{
                if(JOptionPane.showConfirmDialog(this,"¿Generar una nueva contraseña temporal para "+e.nombre()+"?\nLa anterior dejará de funcionar.",
                    "Restablecer acceso",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
                String temporal=personal.restablecerClave(e.id());
                JTextField mostrar=new JTextField(temporal);mostrar.setEditable(false);
                JOptionPane.showMessageDialog(this,form("Clave temporal (copiala ahora)",mostrar),"Nueva contraseña temporal",JOptionPane.INFORMATION_MESSAGE);
            }));
            estado.addActionListener(ev->ejecutar(()->{
                if(e.id()==sesion.empleadoId() && e.activo())throw new IllegalArgumentException("No podés desactivar tu propia cuenta durante esta sesión");
                if(JOptionPane.showConfirmDialog(this,"¿"+(e.activo()?"Desactivar":"Reactivar")+" a "+e.nombre()+"?", "Confirmar",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
                if(e.activo())personal.desactivar(e.id());else personal.reactivar(e.id());
                cargarFichasEmpleados(contenedor);
            }));
            contenedor.add(ficha);
        }
        contenedor.revalidate();contenedor.repaint();
    }
    private static String escaparHtml(String valor){return valor.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private void verFicha(long empleadoId) throws SQLException {
        PersonalServicio.EmpleadoFicha e=personal.listarEmpleados().stream().filter(p->p.id()==empleadoId).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Empleado inexistente"));
        StringBuilder texto=new StringBuilder("Nombre: "+e.nombre()+"\nDNI: "+e.dni()+"\nLogin: "+e.usuario()+
            "\nRol: "+e.rol()+"\nEstado: "+e.estado()+"\n\nHorarios asignados:\n");
        for(Turno t:turnos.asignados(empleadoId))texto.append("· ").append(t.nombre()).append(" — ")
            .append(t.inicio()).append("–").append(t.fin()).append(" — ").append(t.descripcionDias()).append('\n');
        JOptionPane.showMessageDialog(this,texto.toString(),"Ficha de empleado",JOptionPane.INFORMATION_MESSAGE);
    }
    private TurnoServicio.EmpleadoResumen seleccionarEmpleadoAsignado(Turno turno) throws SQLException {
        List<TurnoServicio.EmpleadoResumen> asignados=new ArrayList<>();
        for(TurnoServicio.EmpleadoResumen e:turnos.empleadosActivos())
            if(turnos.asignados(e.id()).stream().anyMatch(t->t.id()==turno.id()))asignados.add(e);
        if(asignados.isEmpty())throw new IllegalArgumentException("El turno no tiene empleados activos asignados");
        JComboBox<TurnoServicio.EmpleadoResumen> combo=new JComboBox<>(asignados.toArray(TurnoServicio.EmpleadoResumen[]::new));
        if(JOptionPane.showConfirmDialog(this,combo,"Seleccioná un empleado asignado",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)
            throw new IllegalArgumentException("Operación cancelada");
        return (TurnoServicio.EmpleadoResumen)combo.getSelectedItem();
    }
    private void cargarTurnos() throws SQLException {
        tablaTurnos.setRowCount(0);
        for(Turno t:turnos.listar()){
            List<String> nombres=new ArrayList<>();
            for(PersonalServicio.EmpleadoFicha e:personal.listarEmpleados())
                if(turnos.asignados(e.id()).stream().anyMatch(x->x.id()==t.id()))nombres.add(e.nombre());
            tablaTurnos.addRow(new Object[]{t.id(),t.nombre(),t.inicio(),t.fin(),t.descripcionDias(),t.duracionMinutos()+" min",
                t.activo()?"ACTIVO":"INACTIVO",nombres.isEmpty()?"Sin asignar":String.join(", ",nombres)});
        }
    }
    private Turno turnoSeleccionado(JTable tabla) throws SQLException {
        int fila=tabla.getSelectedRow();if(fila<0)throw new IllegalArgumentException("Seleccioná un turno de la tabla");
        long id=((Number)tabla.getValueAt(fila,0)).longValue();
        Turno t=turnos.buscar(id);if(t==null)throw new IllegalArgumentException("El turno ya no existe");
        return t;
    }
    private TurnoServicio.EmpleadoResumen seleccionarEmpleado() throws SQLException {
        List<TurnoServicio.EmpleadoResumen> empleados=turnos.empleadosActivos();
        if(empleados.isEmpty())throw new IllegalArgumentException("No hay empleados activos");
        JComboBox<TurnoServicio.EmpleadoResumen> lista=new JComboBox<>(empleados.toArray(TurnoServicio.EmpleadoResumen[]::new));
        if(JOptionPane.showConfirmDialog(this,lista,"Seleccioná el empleado",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)
            throw new IllegalArgumentException("Operación cancelada");
        return (TurnoServicio.EmpleadoResumen)lista.getSelectedItem();
    }
    private void gestionarTurno(Turno anterior) throws Exception {
        JTextField nombre=new JTextField(anterior==null?"":anterior.nombre());
        JTextField inicio=new JTextField(anterior==null?"":anterior.inicio().toString());
        JTextField fin=new JTextField(anterior==null?"":anterior.fin().toString());
        JCheckBox[] checks=new JCheckBox[7];
        JPanel dias=new JPanel(new GridLayout(1,7,8,8));
        int indice=0;
        for(DiaSemana dia:DiaSemana.values()) {
            JCheckBox chk=new JCheckBox(dia.abreviatura());
            chk.setSelected(anterior!=null && anterior.dias().contains(dia));
            checks[indice++]=chk;dias.add(chk);
        }
        JPanel p=new JPanel(new BorderLayout(8,12));
        p.add(form("Nombre del turno",nombre,"Inicio HH:mm",inicio,"Fin HH:mm",fin),BorderLayout.NORTH);
        JPanel abajo=new JPanel(new BorderLayout());abajo.add(new JLabel("Días en los que COMIENZA el turno (L M X J V S D):"),BorderLayout.NORTH);
        abajo.add(dias,BorderLayout.CENTER);p.add(abajo,BorderLayout.SOUTH);
        String titulo=anterior==null?"Crear turno semanal":"Modificar turno #"+anterior.id();
        if(JOptionPane.showConfirmDialog(this,p,titulo,JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        EnumSet<DiaSemana> seleccionados=EnumSet.noneOf(DiaSemana.class);
        int j=0;for(DiaSemana dia:DiaSemana.values())if(checks[j++].isSelected())seleccionados.add(dia);
        LocalTime i=LocalTime.parse(inicio.getText().trim());LocalTime f=LocalTime.parse(fin.getText().trim());
        if(anterior==null) {long id=turnos.crear(nombre.getText(),i,f,seleccionados);mensaje("Turno #"+id+" creado. Ahora podés asignarlo a los empleados.");}
        else {turnos.modificar(anterior.id(),nombre.getText(),i,f,seleccionados);mensaje("Turno actualizado; las jornadas históricas conservan los horarios originales.");}
    }
    private JTabbedPane panelAnalisis(){
        JTabbedPane tabs=new JTabbedPane();
        tabs.addTab("Resumen",panelResumen());
        tabs.addTab("Historial de ventas",panelHistorialVentas());
        tabs.addTab("Historial de jornadas",panelHistorialJornadas());
        return tabs;
    }
    private JPanel panelResumen(){
        JPanel panel=new JPanel(new FlowLayout(FlowLayout.LEFT,15,15));
        JTextField desde=new JTextField(LocalDate.now().minusDays(30).toString(),12),
            hasta=new JTextField(LocalDate.now().toString(),12);
        JButton consultar=new JButton("Consultar ventas por período");
        JTextArea area=new JTextArea(12,65);area.setEditable(false);
        panel.add(new JLabel("Desde"));panel.add(desde);panel.add(new JLabel("Hasta"));panel.add(hasta);
        panel.add(consultar);panel.add(new JScrollPane(area));
        consultar.addActionListener(e->ejecutar(()->{
            LocalDate a=LocalDate.parse(desde.getText()),b=LocalDate.parse(hasta.getText());
            var r=analisis.resumen(a,b);
            StringBuilder sb=new StringBuilder("Cantidad de ventas: "+r.cantidadVentas()+
                "\nFacturación: "+Moneda.formato(r.montoTotal())+
                "\nTicket promedio: "+Moneda.formato(r.promedio())+"\n\nPagos por medio:\n");
            for(var pago:historiales.recaudacion(a,b))sb.append(pago.medio()).append(": ")
                .append(Moneda.formato(pago.importe())).append(" (pagos: ").append(pago.pagos()).append(")\n");
            area.setText(sb.toString());
        }));
        return panel;
    }
    private JPanel panelHistorialVentas(){
        JPanel raiz=new JPanel(new BorderLayout(8,8));raiz.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JTextField desde=new JTextField(LocalDate.now().minusDays(30).toString(),10),
            hasta=new JTextField(LocalDate.now().toString(),10);
        JComboBox<String> medio=new JComboBox<>(new String[]{"TODOS","EFECTIVO","TRANSFERENCIA","TARJETA","QR"});
        JButton buscar=new JButton("Consultar"),detalle=new JButton("Ver detalle y pagos");
        JPanel filtros=new JPanel(new FlowLayout(FlowLayout.LEFT));
        for(Component a:new Component[]{new JLabel("Desde (AAAA-MM-DD)"),desde,
            new JLabel("Hasta"),hasta,new JLabel("Medio"),medio,buscar,detalle})filtros.add(a);
        raiz.add(filtros,BorderLayout.NORTH);
        DefaultTableModel m=new DefaultTableModel(new Object[]{"Venta","Caja","Empleado","Fecha","Total"},0){
            @Override public boolean isCellEditable(int f,int c){return false;}
        };
        JTable tabla=new JTable(m);tabla.setAutoCreateRowSorter(true);
        raiz.add(new JScrollPane(tabla),BorderLayout.CENTER);
        buscar.addActionListener(e->ejecutar(()->{
            m.setRowCount(0);
            String elegido=String.valueOf(medio.getSelectedItem());
            for(var v:historiales.listar(LocalDate.parse(desde.getText()),LocalDate.parse(hasta.getText()),
                  null,"TODOS".equals(elegido)?null:elegido))
                m.addRow(new Object[]{v.id(),v.cajaId(),v.empleado(),v.fechaHora(),Moneda.formato(v.total())});
        }));
        Runnable ver=()->ejecutar(()->{
            int f=tabla.getSelectedRow();if(f<0)throw new IllegalArgumentException("Seleccioná una venta");
            long id=((Number)m.getValueAt(tabla.convertRowIndexToModel(f),0)).longValue();
            HistorialVentasServicio.Detalle d=historiales.detalle(id);
            StringBuilder sb=new StringBuilder("VENTA #"+id+"\n\nARTÍCULOS\n");
            for(var item:d.productos())sb.append(item.codigo()).append(" × ").append(item.cantidad())
                .append(" · Unitario ").append(Moneda.formato(item.unitario()))
                .append(" · Subtotal ").append(Moneda.formato(item.subtotal())).append('\n');
            sb.append("\nMEDIOS DE PAGO\n");
            for(var pago:d.pagos())sb.append(pago.medio()).append(" · ")
                .append(Moneda.formato(pago.monto())).append('\n');
            JTextArea texto=new JTextArea(sb.toString(),16,52);texto.setEditable(false);
            JOptionPane.showMessageDialog(this,new JScrollPane(texto),"Venta #"+id,JOptionPane.INFORMATION_MESSAGE);
        });
        detalle.addActionListener(e->ver.run());
        tabla.addMouseListener(new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent e){
            if(e.getClickCount()==2)ver.run();
        }});
        return raiz;
    }
    private JPanel panelHistorialJornadas(){
        JPanel raiz=new JPanel(new BorderLayout(8,8));raiz.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JTextField desde=new JTextField(LocalDate.now().minusDays(30).toString(),10),
            hasta=new JTextField(LocalDate.now().toString(),10);
        JComboBox<String> estado=new JComboBox<>(new String[]{"TODOS","ABIERTA","CERRADA"});
        JButton buscar=new JButton("Consultar"),detalle=new JButton("Ver detalle");
        JPanel filtros=new JPanel(new FlowLayout(FlowLayout.LEFT));
        for(Component a:new Component[]{new JLabel("Desde"),desde,new JLabel("Hasta"),hasta,new JLabel("Estado"),estado,buscar,detalle})filtros.add(a);
        raiz.add(filtros,BorderLayout.NORTH);
        DefaultTableModel m=new DefaultTableModel(new Object[]{"Caja","Empleado","Turno","Apertura","Cierre","Estado","Fondo inicial","Efectivo","MP cobrado","Diferencia"},0){
            @Override public boolean isCellEditable(int f,int c){return false;}
        };
        JTable tabla=new JTable(m);tabla.setAutoCreateRowSorter(true);
        raiz.add(new JScrollPane(tabla),BorderLayout.CENTER);
        buscar.addActionListener(e->ejecutar(()->{
            m.setRowCount(0);
            String filtro=String.valueOf(estado.getSelectedItem());
            for(var j:jornadas.listar(LocalDate.parse(desde.getText()),LocalDate.parse(hasta.getText()),
                null,"TODOS".equals(filtro)?null:filtro))
                m.addRow(new Object[]{j.cajaId(),j.empleado(),j.turno(),j.inicio(),j.fin(),j.estado(),
                    Moneda.formato(j.fondo()),Moneda.formato(j.efectivo()),Moneda.formato(j.digital()),Moneda.formato(j.diferencia())});
        }));
        detalle.addActionListener(e->ejecutar(()->{
            int f=tabla.getSelectedRow();if(f<0)throw new IllegalArgumentException("Seleccioná una jornada");
            long id=((Number)m.getValueAt(tabla.convertRowIndexToModel(f),0)).longValue();
            CajaServicio.Resumen r=cajas.resumen(id);
            CajaDigitalServicio.Resumen d=digital.resumen(id);
            JTextArea t=new JTextArea(desglose(r)+"\n\n"+desgloseDigital(d)+"\n\n"+cajas.historial(id)+
                (d==null?"":"\n"+digital.historial(id)),22,65);
            t.setEditable(false);JOptionPane.showMessageDialog(this,new JScrollPane(t),"Jornada caja #"+id,
                JOptionPane.INFORMATION_MESSAGE);
        }));
        return raiz;
    }
    private void actualizarAlertas() throws SQLException {
        if(sesion!=null)botonAlertas.setText("Avisos de stock ("+alertas.pendientes(sesion.empleadoId()).size()+")");
    }
    private void abrirAlertas() throws SQLException {
        List<AlertasServicio.Alerta> pendientes=alertas.pendientes(sesion.empleadoId());
        if(pendientes.isEmpty()){mensaje("No hay alertas de stock pendientes");return;}
        JPanel listado=new JPanel(new GridLayout(0,1,6,6));
        JDialog dialogo=new JDialog(this,"Notificaciones de stock",false);
        for(var alerta:pendientes){
            JPanel fila=new JPanel(new BorderLayout(10,2));
            fila.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
            if("SIN_STOCK".equals(alerta.nivel()))fila.setBackground(new Color(255,178,178));
            else if("BAJO".equals(alerta.nivel()))fila.setBackground(new Color(255,209,143));
            else fila.setBackground(new Color(235,240,247));
            JLabel info=new JLabel(alerta.nombre()+" · "+alerta.codigo()+" · Stock: "+alerta.stock()+
                " ["+alerta.nivel()+"]");
            JButton ver=new JButton("Ver producto"),cerrar=new JButton("X");
            JPanel acciones=new JPanel(new FlowLayout(FlowLayout.RIGHT));acciones.setOpaque(false);
            acciones.add(ver);acciones.add(cerrar);fila.add(info,BorderLayout.CENTER);fila.add(acciones,BorderLayout.EAST);
            ver.addActionListener(ev->ejecutar(()->{dialogo.dispose();seleccionarProducto(alerta.codigo());}));
            cerrar.addActionListener(ev->ejecutar(()->{
                alertas.descartar(sesion.empleadoId(),alerta);listado.remove(fila);
                listado.revalidate();listado.repaint();actualizarAlertas();
            }));
            listado.add(fila);
        }
        dialogo.add(new JScrollPane(listado));dialogo.setSize(740,Math.min(560,200+pendientes.size()*75));
        dialogo.setLocationRelativeTo(this);dialogo.setVisible(true);
    }
    private void seleccionarProducto(String codigo) throws SQLException {
        filtroProductos.setText(codigo);cargarProductos(codigo);pestañas.setSelectedIndex(1);
        for(int i=0;i<tablaProductos.getRowCount();i++){
            if(codigo.equals(String.valueOf(tablaProductos.getValueAt(i,0)))){
                int v=vistaProductos.convertRowIndexToView(i);
                vistaProductos.setRowSelectionInterval(v,v);
                vistaProductos.scrollRectToVisible(vistaProductos.getCellRect(v,0,true));break;
            }
        }
    }
    private static JPanel form(Object... campos){JPanel p=new JPanel(new GridLayout(campos.length/2,2,8,8));for(Object o:campos){if(o instanceof Component c)p.add(c);else p.add(new JLabel(o.toString()));}return p;}
    private static Integer enteroOpcional(JTextField t){return t.getText().isBlank()?null:Integer.parseInt(t.getText().trim());}
    private static BigDecimal importe(JTextField t){return Moneda.parsear(t.getText());}
    private static int enteroPositivo(JTextField t,String nombre) {
        int valor=enteroNoNegativo(t,nombre);if(valor==0)throw new IllegalArgumentException(nombre+" debe ser mayor que cero");return valor;
    }
    private static int enteroNoNegativo(JTextField t,String nombre) {
        try {int valor=Integer.parseInt(t.getText().trim());if(valor<0)throw new NumberFormatException();return valor;}
        catch(NumberFormatException e){throw new IllegalArgumentException(nombre+" debe ser un número entero igual o mayor que cero");}
    }
    private static void alCambiar(JTextField campo,Runnable accion) {
        campo.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e){SwingUtilities.invokeLater(accion);}
            public void removeUpdate(DocumentEvent e){SwingUtilities.invokeLater(accion);}
            public void changedUpdate(DocumentEvent e){SwingUtilities.invokeLater(accion);}
        });
    }
    private void mensaje(String m){JOptionPane.showMessageDialog(this,m,"SIGAM-POS",JOptionPane.INFORMATION_MESSAGE);}
    private void error(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Operación no realizada",JOptionPane.ERROR_MESSAGE);}
    @FunctionalInterface private interface Accion{void ejecutar()throws Exception;}
    private void ejecutar(Accion a){try{if(sesion==null)throw new IllegalStateException("Primero iniciar sesión");a.ejecutar();}catch(Exception e){error(e);}}
}
