package ar.com.sigampos.ui;

import ar.com.sigampos.modelo.*;
import ar.com.sigampos.servicio.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
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
    private PersonalServicio.Sesion sesion;
    private final List<LineaVenta> carrito=new ArrayList<>();
    private final DefaultTableModel tablaCarrito=new DefaultTableModel(new Object[]{"Código","Producto","Cantidad","Unitario","Subtotal"},0) {
        @Override public boolean isCellEditable(int r,int c){return false;}
    };
    private final DefaultTableModel tablaProductos=new DefaultTableModel(new Object[]{"Código","Producto","Precio","Stock","Estado"},0) {
        @Override public boolean isCellEditable(int r,int c){return false;}
    };
    private final JTextField caja=new JTextField(6);
    private final DefaultTableModel tablaTurnos=new DefaultTableModel(new Object[]{"ID","Nombre","Inicio","Fin","Días","Duración","Estado"},0) {
        @Override public boolean isCellEditable(int r,int c){return false;}
    };
    private final JLabel total=new JLabel("TOTAL $ 0,00");
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
        SwingUtilities.invokeLater(()->{
            try {
                if(personal.crearAdminInicialSiFalta()) mensaje("Primera instalación: usuario Admin y contraseña temporal 1234. Cambiala desde Mis credenciales.");
                if(!iniciarSesion()) {dispose();return;}
                actualizarPermisos();
                cargarProductos("");
            } catch(Exception e) {error(e);dispose();}
        });
    }
    private boolean iniciarSesion(){
        while(true){
            JTextField usuario=new JTextField();JPasswordField clave=new JPasswordField();
            JPanel panel=form("Usuario",usuario,"Contraseña",clave);
            int opcion=JOptionPane.showConfirmDialog(this,panel,"Inicio de sesión",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
            if(opcion!=JOptionPane.OK_OPTION)return false;
            try {sesion=personal.autenticar(usuario.getText(),new String(clave.getPassword()));return true;}
            catch(Exception e){error(e);}
        }
    }
    private void actualizarPermisos(){
        sesionLabel.setText(sesion.nombre()+"  |  "+sesion.rol()+"  ");
        pestañas.setEnabledAt(1,sesion.administrador());
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
        if(iniciarSesion()){actualizarPermisos();try{cargarProductos("");}catch(SQLException e){error(e);}}
        else dispose();
    }
    private void salirDelPrograma(){if(confirmarSalir()){carrito.clear();sesion=null;dispose();}}
    private void editarMisCredenciales() throws SQLException {
        JTextField usuario=new JTextField();JPasswordField clave=new JPasswordField(),repetir=new JPasswordField();
        JPanel panel=form("Nuevo usuario/login",usuario,"Nueva contraseña (8+)",clave,"Repetir contraseña",repetir);
        if(JOptionPane.showConfirmDialog(this,panel,"Mis credenciales",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        char[] a=clave.getPassword(),b=repetir.getPassword();
        try{
            if(!java.util.Arrays.equals(a,b))throw new IllegalArgumentException("Las contraseñas no coinciden");
            personal.cambiarCredenciales(sesion.empleadoId(),usuario.getText(),new String(a));
            mensaje("Credenciales actualizadas. Se aplicarán en el próximo inicio de sesión.");
        }finally{java.util.Arrays.fill(a,'\0');java.util.Arrays.fill(b,'\0');}
    }
    private JPanel panelVentas(){
        JPanel raiz=new JPanel(new BorderLayout(12,12));raiz.setBorder(BorderFactory.createEmptyBorder(15,15,15,15));
        JPanel superior=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField codigo=new JTextField(18),cantidad=new JTextField("1",4);
        superior.add(new JLabel("Código producto:"));superior.add(codigo);superior.add(new JLabel("Cantidad:"));superior.add(cantidad);
        JButton agregar=new JButton("Agregar al carrito");superior.add(agregar);
        JButton quitar=new JButton("Quitar seleccionado");superior.add(quitar);raiz.add(superior,BorderLayout.NORTH);
        JTable tabla=new JTable(tablaCarrito);tabla.setRowHeight(26);raiz.add(new JScrollPane(tabla),BorderLayout.CENTER);
        JPanel inferior=new JPanel(new BorderLayout());total.setFont(total.getFont().deriveFont(Font.BOLD,30f));inferior.add(total,BorderLayout.WEST);
        JPanel acciones=new JPanel();acciones.add(new JLabel("Caja activa:"));acciones.add(caja);JButton cobrar=new JButton("COBRAR");cobrar.setFont(cobrar.getFont().deriveFont(Font.BOLD,17f));acciones.add(cobrar);
        inferior.add(acciones,BorderLayout.EAST);raiz.add(inferior,BorderLayout.SOUTH);
        agregar.addActionListener(e->ejecutar(()->{
            Producto p=productos.listar(codigo.getText()).stream().filter(x->x.codigo().equals(codigo.getText().trim())).findFirst().orElse(null);
            if(p==null || !p.activo())throw new IllegalArgumentException("Producto no encontrado o inactivo");
            int q=Integer.parseInt(cantidad.getText());if(q<=0)throw new IllegalArgumentException("Cantidad inválida");
            int existentes=carrito.stream().filter(l->l.producto().codigo().equals(p.codigo())).mapToInt(LineaVenta::cantidad).sum();
            if(existentes+q>p.stock())throw new IllegalArgumentException("Stock insuficiente");
            carrito.add(new LineaVenta(p,q));refrescarCarrito();codigo.setText("");codigo.requestFocusInWindow();
        }));
        quitar.addActionListener(e->{int fila=tabla.getSelectedRow();if(fila>=0){carrito.remove(fila);refrescarCarrito();}});
        cobrar.addActionListener(e->ejecutar(()->cobrar()));return raiz;
    }
    private BigDecimal totalCarrito(){return carrito.stream().map(LineaVenta::subtotal).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private void refrescarCarrito(){tablaCarrito.setRowCount(0);for(LineaVenta l:carrito)tablaCarrito.addRow(new Object[]{l.producto().codigo(),l.producto().nombre(),l.cantidad(),l.producto().precioVenta(),l.subtotal()});total.setText("TOTAL $ "+totalCarrito());}
    private void cobrar()throws Exception {
        if(carrito.isEmpty())throw new IllegalArgumentException("Carrito vacío");
        JPanel panel=new JPanel(new GridLayout(5,2,8,8));
        panel.add(new JLabel("TOTAL:"));panel.add(new JLabel("$ "+totalCarrito()));
        Map<MedioPago,JTextField> importes=new LinkedHashMap<>();
        for(MedioPago medio:MedioPago.values()){JTextField tf=new JTextField("0");importes.put(medio,tf);panel.add(new JLabel(medio.name()));panel.add(tf);}
        if(JOptionPane.showConfirmDialog(this,panel,"Finalizar venta - pagos divididos",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        List<PagoVenta> pagos=new ArrayList<>();
        for(var entry:importes.entrySet()){BigDecimal m=importe(entry.getValue());if(m.signum()>0)pagos.add(new PagoVenta(entry.getKey(),m));}
        long id=ventas.confirmar(Long.parseLong(caja.getText()),sesion.empleadoId(),List.copyOf(carrito),pagos);
        carrito.clear();refrescarCarrito();cargarProductos("");mensaje("Venta #"+id+" registrada y stock actualizado");
    }
    private JPanel panelProductos(){
        JPanel raiz=new JPanel(new BorderLayout(8,8));raiz.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JTextField filtro=new JTextField(18);JPanel arriba=new JPanel(new FlowLayout(FlowLayout.LEFT));arriba.add(new JLabel("Buscar:"));arriba.add(filtro);
        JButton buscar=new JButton("Buscar"),nuevo=new JButton("Crear / Modificar"),baja=new JButton("Desactivar");arriba.add(buscar);arriba.add(nuevo);arriba.add(baja);raiz.add(arriba,BorderLayout.NORTH);
        JTable tabla=new JTable(tablaProductos);raiz.add(new JScrollPane(tabla),BorderLayout.CENTER);
        buscar.addActionListener(e->ejecutar(()->cargarProductos(filtro.getText())));
        nuevo.addActionListener(e->ejecutar(()->editarProducto()));
        baja.addActionListener(e->ejecutar(()->{int f=tabla.getSelectedRow();if(f<0)throw new IllegalArgumentException("Elegí un producto");productos.desactivar(tabla.getValueAt(f,0).toString());cargarProductos(filtro.getText());}));
        return raiz;
    }
    private void cargarProductos(String filtro)throws SQLException {
        tablaProductos.setRowCount(0);for(Producto p:productos.listar(filtro))tablaProductos.addRow(new Object[]{p.codigo(),p.nombre(),p.precioVenta(),p.stock(),p.activo()?"ACTIVO":"INACTIVO"});
    }
    private void editarProducto()throws Exception {
        JTextField codigo=new JTextField(),nombre=new JTextField(),precio=new JTextField(),costo=new JTextField(),stock=new JTextField("0"),min=new JTextField(),max=new JTextField();
        JPanel panel=form("Código",codigo,"Nombre",nombre,"Precio venta",precio,"Costo",costo,"Stock",stock,"Stock mínimo (opcional)",min,"Stock máximo (opcional)",max);
        if(JOptionPane.showConfirmDialog(this,panel,"Producto: alta / modificación",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        Producto p=new Producto(codigo.getText(),nombre.getText(),importe(precio),importe(costo),Integer.parseInt(stock.getText()),enteroOpcional(min),enteroOpcional(max),true);
        productos.guardar(p);cargarProductos("");mensaje("Producto guardado; historial de precio actualizado si corresponde");
    }
    private JPanel panelCaja(){
        JPanel panel=new JPanel(new GridLayout(0,2,10,10));panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        JTextField monto=new JTextField("0"),motivo=new JTextField();
        panel.add(new JLabel("Caja activa (jornada del empleado):"));
        JTextField campoCaja=new JTextField();campoCaja.setDocument(caja.getDocument());campoCaja.setEditable(false);panel.add(campoCaja);
        panel.add(new JLabel("Importe para abrir / entrada / salida:"));panel.add(monto);
        panel.add(new JLabel("Motivo de entrada / salida:"));panel.add(motivo);
        JButton abrir=new JButton("Iniciar jornada y abrir caja"),entrada=new JButton("Entrada (+)"),salida=new JButton("Salida (-)"),
            esperado=new JButton("Ver desglose de caja"),cerrar=new JButton("Finalizar jornada (arqueo)");
        panel.add(abrir);panel.add(esperado);panel.add(entrada);panel.add(salida);panel.add(cerrar);
        abrir.addActionListener(e->ejecutar(()->{
            List<Turno> asignados=turnos.asignados(sesion.empleadoId()).stream()
                .filter(t->t.activo() && t.dias().contains(DiaSemana.desde(LocalDate.now().getDayOfWeek()))).toList();
            if(asignados.isEmpty())throw new IllegalArgumentException("No tenés turnos activos asignados para hoy. Pedile al administrador que los asigne.");
            JComboBox<TurnoOpcion> lista=new JComboBox<>();
            for(Turno t:asignados)lista.addItem(new TurnoOpcion(t));
            JPanel p=form("Turno de hoy",lista,"Fondo inicial ($)",monto);
            if(JOptionPane.showConfirmDialog(this,p,"Iniciar jornada",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            TurnoOpcion elegido=(TurnoOpcion)lista.getSelectedItem();
            long id=cajas.abrir(elegido.turno().id(),sesion.empleadoId(),importe(monto));
            caja.setText(String.valueOf(id));mensaje("Jornada iniciada. Caja #"+id+" abierta.");
        }));
        entrada.addActionListener(e->ejecutar(()->{cajas.registrar(cajaActual(),sesion.empleadoId(),new IngresoCaja(importe(monto),motivo.getText(),false));mensaje("Entrada registrada");}));
        salida.addActionListener(e->ejecutar(()->{cajas.registrar(cajaActual(),sesion.empleadoId(),new EgresoCaja(importe(monto),motivo.getText()));mensaje("Salida registrada");}));
        esperado.addActionListener(e->ejecutar(()->mensaje(desglose(cajas.resumen(cajaActual())))));
        cerrar.addActionListener(e->ejecutar(()->finalizarJornada()));
        return panel;
    }
    private record TurnoOpcion(Turno turno) {
        @Override public String toString(){return turno.nombre()+" ("+turno.inicio()+"–"+turno.fin()+", "+turno.descripcionDias()+")";}
    }
    private long cajaActual(){
        if(caja.getText().isBlank())throw new IllegalStateException("No hay caja activa. Iniciá tu jornada en Caja.");
        return Long.parseLong(caja.getText());
    }
    private String desglose(CajaServicio.Resumen r){
        return "Fondo inicial: $ "+r.fondoInicial()+"\nVentas en efectivo: $ "+r.ventasEfectivo()+
            "\nEntradas manuales: $ "+r.entradas()+"\nSalidas manuales: $ "+r.salidas()+
            "\n-------------------------\nEFECTIVO ESPERADO: $ "+r.esperado();
    }
    private void finalizarJornada() throws SQLException {
        if(!confirmarSalir())return;
        long id=cajaActual();
        CajaServicio.Resumen resumen=cajas.resumen(id);
        JTextField contado=new JTextField(),proximo=new JTextField(),observaciones=new JTextField();
        JTextArea detalle=new JTextArea(desglose(resumen));detalle.setEditable(false);detalle.setOpaque(false);
        JPanel p=new JPanel(new BorderLayout(0,12));p.add(detalle,BorderLayout.NORTH);
        p.add(form("Efectivo contado ($)",contado,"Fondo para el próximo turno ($)",proximo,"Observaciones (opcional)",observaciones),BorderLayout.CENTER);
        if(JOptionPane.showConfirmDialog(this,p,"Arqueo y cierre obligatorio",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        BigDecimal diferencia=cajas.cerrar(id,sesion.empleadoId(),importe(contado),importe(proximo),observaciones.getText());
        caja.setText("");
        carrito.clear();refrescarCarrito();
        mensaje("Caja y jornada cerradas. Diferencia del arqueo: $ "+diferencia+". Se cerrará tu sesión.");
        cerrarSesion();
    }
    private JPanel panelPersonal(){
        JPanel raiz=new JPanel(new BorderLayout(8,8));raiz.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel acciones=new JPanel(new FlowLayout(FlowLayout.LEFT,8,8));
        JButton crear=new JButton("Alta empleado + usuario"),desactivar=new JButton("Desactivar empleado"),
            nuevo=new JButton("Crear turno"),editar=new JButton("Modificar turno"),
            asignar=new JButton("Asignar al empleado"),quitar=new JButton("Quitar asignación"),
            ver=new JButton("Ver turnos del empleado"),refrescar=new JButton("Actualizar horarios");
        for(JButton b:new JButton[]{crear,desactivar,nuevo,editar,asignar,quitar,ver,refrescar})acciones.add(b);
        raiz.add(acciones,BorderLayout.NORTH);
        JTable tabla=new JTable(tablaTurnos);tabla.setRowHeight(26);tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        raiz.add(new JScrollPane(tabla),BorderLayout.CENTER);
        crear.addActionListener(e->ejecutar(()->{
            JTextField nombre=new JTextField(),dni=new JTextField();
            JPanel p=form("Nombre",nombre,"DNI (login)",dni);
            if(JOptionPane.showConfirmDialog(this,p,"Crear empleado vendedor",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){
                long id=personal.crearEmpleadoConAccesoDni(nombre.getText(),dni.getText());
                mensaje("Empleado #"+id+" creado. Login: DNI. Contraseña temporal: últimos 4 dígitos del DNI. Debe cambiarla.");
            }
        }));
        desactivar.addActionListener(e->ejecutar(()->{long id=seleccionarEmpleado().id();personal.desactivar(id);mensaje("Empleado desactivado, historial conservado");}));
        nuevo.addActionListener(e->ejecutar(()->{gestionarTurno(null);cargarTurnos();}));
        editar.addActionListener(e->ejecutar(()->{gestionarTurno(turnoSeleccionado(tabla));cargarTurnos();}));
        asignar.addActionListener(e->ejecutar(()->{
            Turno t=turnoSeleccionado(tabla);TurnoServicio.EmpleadoResumen empleado=seleccionarEmpleado();
            turnos.asignar(empleado.id(),t.id());mensaje("Asignaste '"+t.nombre()+"' a "+empleado.nombre());
        }));
        quitar.addActionListener(e->ejecutar(()->{
            Turno t=turnoSeleccionado(tabla);TurnoServicio.EmpleadoResumen empleado=seleccionarEmpleado();
            if(JOptionPane.showConfirmDialog(this,"Quitar '"+t.nombre()+"' a "+empleado.nombre()+"?", "Confirmar",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
            turnos.quitarAsignacion(empleado.id(),t.id());mensaje("Asignación quitada sin borrar jornadas anteriores");
        }));
        ver.addActionListener(e->ejecutar(()->{
            TurnoServicio.EmpleadoResumen empleado=seleccionarEmpleado();
            List<Turno> lista=turnos.asignados(empleado.id());
            StringBuilder sb=new StringBuilder("Turnos de "+empleado.nombre()+":\n\n");
            for(Turno t:lista)sb.append(t.nombre()).append("  ").append(t.inicio()).append("–").append(t.fin())
                .append("  [").append(t.descripcionDias()).append("]\n");
            if(lista.isEmpty())sb.append("Sin turnos asignados");
            mensaje(sb.toString());
        }));
        refrescar.addActionListener(e->ejecutar(()->cargarTurnos()));
        SwingUtilities.invokeLater(()->{try{cargarTurnos();}catch(SQLException ex){/* la base podría no estar configurada aún */}});
        return raiz;
    }
    private void cargarTurnos() throws SQLException {
        tablaTurnos.setRowCount(0);
        for(Turno t:turnos.listar())tablaTurnos.addRow(new Object[]{t.id(),t.nombre(),t.inicio(),t.fin(),t.descripcionDias(),t.duracionMinutos()+" min",t.activo()?"ACTIVO":"INACTIVO"});
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
    private JPanel panelAnalisis(){
        JPanel panel=new JPanel(new FlowLayout(FlowLayout.LEFT,15,15));JTextField desde=new JTextField(LocalDate.now().minusDays(30).toString(),12),hasta=new JTextField(LocalDate.now().toString(),12);
        JButton consultar=new JButton("Consultar ventas por período");JTextArea area=new JTextArea(8,65);area.setEditable(false);
        panel.add(new JLabel("Desde"));panel.add(desde);panel.add(new JLabel("Hasta"));panel.add(hasta);panel.add(consultar);panel.add(new JScrollPane(area));
        consultar.addActionListener(e->ejecutar(()->{var r=analisis.resumen(LocalDate.parse(desde.getText()),LocalDate.parse(hasta.getText()));area.setText("Cantidad de ventas: "+r.cantidadVentas()+"\nFacturación: $ "+r.montoTotal()+"\nTicket promedio: $ "+r.promedio());}));
        return panel;
    }
    private static JPanel form(Object... campos){JPanel p=new JPanel(new GridLayout(campos.length/2,2,8,8));for(Object o:campos){if(o instanceof Component c)p.add(c);else p.add(new JLabel(o.toString()));}return p;}
    private static Integer enteroOpcional(JTextField t){return t.getText().isBlank()?null:Integer.parseInt(t.getText().trim());}
    private static BigDecimal importe(JTextField t){return new BigDecimal(t.getText().trim().replace(",","." )).setScale(2,java.math.RoundingMode.UNNECESSARY);}
    private void mensaje(String m){JOptionPane.showMessageDialog(this,m,"SIGAM-POS",JOptionPane.INFORMATION_MESSAGE);}
    private void error(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Operación no realizada",JOptionPane.ERROR_MESSAGE);}
    @FunctionalInterface private interface Accion{void ejecutar()throws Exception;}
    private void ejecutar(Accion a){try{if(sesion==null)throw new IllegalStateException("Primero iniciar sesión");a.ejecutar();}catch(Exception e){error(e);}}
}
