package ar.com.sigampos.ui;

import ar.com.sigampos.modelo.*;
import ar.com.sigampos.servicio.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import ar.com.sigampos.util.Moneda;
import ar.com.sigampos.util.CampoMoneda;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.*;
import java.util.List;

/** Interfaz Swing inspirada en la distribución de trabajo de un POS*/
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
    private final ConfiguracionProductos configuracionProductos=new ConfiguracionProductos();
    private final ExportacionReportesServicio exportacion=new ExportacionReportesServicio();
    private final BackupServicio backups=new BackupServicio();
    private final AtomicBoolean backupEnCurso=new AtomicBoolean(false);
    private Instant ultimoIntentoBackup=Instant.EPOCH;
    private final JButton botonConfiguracion=new JButton("Configuraciones");
    private final JButton botonAlertas=new JButton("Avisos de stock");
    private JTable vistaProductos;
    private final JLabel conteoProductos=new JLabel("Cargando productos...");
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
    private final JButton[] botonesNavegacion=new JButton[5];
    private final JLabel tituloSeccion=new JLabel("Ventas");
    private static final String[] MODULOS={"Ventas","Productos","Caja","Personal / Turnos","Análisis"};
    private static final IconoSeccion.Tipo[] ICONOS={IconoSeccion.Tipo.VENTAS,
        IconoSeccion.Tipo.PRODUCTOS, IconoSeccion.Tipo.CAJA,
        IconoSeccion.Tipo.PERSONAL, IconoSeccion.Tipo.ANALISIS};

    public VentanaPrincipal() {
        super("SIGAM-POS — Gestión comercial");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { salirDelPrograma(); }
        });
        setSize(1380, 840);
        setMinimumSize(new Dimension(1080, 660));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(TemaVisual.FONDO);
        caja.setEditable(false);

        // Cabecera despejada: navegación lateral y operaciones de usuario a la derecha.
        JPanel cabecera=new JPanel(new BorderLayout(14,0));
        cabecera.setBackground(TemaVisual.BLANCO);
        cabecera.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0,0,1,0,TemaVisual.BORDE),
            BorderFactory.createEmptyBorder(14,24,14,24)));
        tituloSeccion.setFont(new Font("Segoe UI",Font.BOLD,23));
        tituloSeccion.setForeground(TemaVisual.AZUL_OSCURO);
        cabecera.add(tituloSeccion,BorderLayout.WEST);
        sesionLabel.setForeground(TemaVisual.SECUNDARIO);
        JPanel acceso=new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
        acceso.setOpaque(false);
        JButton cambiarClave=new JButton("Mis credenciales"),cerrarSesion=new JButton("Cerrar sesión");
        acceso.add(sesionLabel);
        acceso.add(botonConfiguracion);
        acceso.add(cambiarClave);
        acceso.add(cerrarSesion);
        cabecera.add(acceso,BorderLayout.EAST);
        botonConfiguracion.addActionListener(e->ejecutar(()->abrirConfiguraciones()));
        cambiarClave.addActionListener(e->ejecutar(()->editarMisCredenciales()));
        cerrarSesion.addActionListener(e->cerrarSesion());
        add(cabecera,BorderLayout.NORTH);

        pestañas.addTab("Ventas",panelVentas());
        pestañas.addTab("Productos",panelProductos());
        pestañas.addTab("Caja",panelCaja());
        pestañas.addTab("Personal / Turnos",panelPersonal());
        pestañas.addTab("Análisis",panelAnalisis());
        // Conserva el JTabbedPane (y sus permisos) sin duplicar los encabezados en pantalla.
        pestañas.setUI(new PestanasOcultasUI());
        add(pestañas,BorderLayout.CENTER);
        add(crearBarraNavegacion(),BorderLayout.WEST);
        pestañas.addChangeListener(e->{
            actualizarNavegacion();
            if(sesion!=null && pestañas.getSelectedIndex()==0)
                ejecutar(()->cargarCatalogo(""));
        });
        TemaVisual.aplicar(pestañas);
        TemaVisual.aplicar(cabecera);
        actualizarNavegacion();
        // Cuando la aplicación queda abierta todo el día, comprueba cada hora el respaldo automático.
        javax.swing.Timer relojBackups=new javax.swing.Timer(60*60*1000,e->revisarBackupAutomatico());
        relojBackups.start();
        SwingUtilities.invokeLater(()->{
            try {
                if(personal.crearAdminInicialSiFalta()) mensaje("Primera instalación: usuario Admin y contraseña temporal 1234. Cambiala desde Mis credenciales.");
                if(!iniciarSesion()) {dispose();return;}
                actualizarPermisos();
                cargarProductos("");cargarCatalogo("");actualizarAlertas();
                // Respaldo opcional al iniciar: nunca bloquea la interfaz.
                revisarBackupAutomatico();
            } catch(Exception e) {error(e);dispose();}
        });
    }
    /** Navegación unificada respeta los mismos índices y permisos que las pestañas originales. */
    private JPanel crearBarraNavegacion() {
        JPanel lateral=new JPanel();
        lateral.setBackground(TemaVisual.LATERAL);
        lateral.setLayout(new BoxLayout(lateral,BoxLayout.Y_AXIS));
        lateral.setPreferredSize(new Dimension(225,0));
        lateral.setBorder(BorderFactory.createEmptyBorder(23,12,18,12));

        JLabel marca=new JLabel("  SIGAM-POS");
        marca.setAlignmentX(Component.LEFT_ALIGNMENT);
        marca.setFont(new Font("Segoe UI",Font.BOLD,20));
        marca.setForeground(Color.WHITE);
        lateral.add(marca);
        JLabel subtitulo=new JLabel("      Sistema de gestión");
        subtitulo.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitulo.setForeground(new Color(148,178,203));
        subtitulo.setFont(new Font("Segoe UI",Font.PLAIN,12));
        lateral.add(Box.createVerticalStrut(5));
        lateral.add(subtitulo);
        lateral.add(Box.createVerticalStrut(38));

        JLabel encabezado=new JLabel("     MÓDULOS");
        encabezado.setAlignmentX(Component.LEFT_ALIGNMENT);
        encabezado.setForeground(new Color(135,161,182));
        encabezado.setFont(new Font("Segoe UI",Font.BOLD,11));
        lateral.add(encabezado);
        lateral.add(Box.createVerticalStrut(12));

        for(int i=0;i<MODULOS.length;i++) {
            final int indice=i;
            JButton boton=new JButton(MODULOS[i]);
            boton.putClientProperty("sigam.tipo","navegacion");
            boton.setAlignmentX(Component.LEFT_ALIGNMENT);
            boton.setMaximumSize(new Dimension(200,49));
            boton.setPreferredSize(new Dimension(200,46));
            boton.setHorizontalAlignment(SwingConstants.LEFT);
            boton.setIconTextGap(13);
            boton.setFocusPainted(false);
            boton.setOpaque(true);
            boton.setBorderPainted(false);
            boton.setContentAreaFilled(true);
            boton.setBorder(BorderFactory.createEmptyBorder(12,15,12,10));
            boton.setFont(new Font("Segoe UI",Font.BOLD,13));
            boton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            boton.addActionListener(e->{
                if(pestañas.isEnabledAt(indice))pestañas.setSelectedIndex(indice);
            });
            botonesNavegacion[i]=boton;
            lateral.add(boton);
            lateral.add(Box.createVerticalStrut(5));
        }
        lateral.add(Box.createVerticalGlue());
        JLabel version=new JLabel("  v1.0.0 RC1  ·  Versión académica");
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        version.setFont(new Font("Segoe UI",Font.PLAIN,11));
        version.setForeground(new Color(150,171,189));
        lateral.add(version);
        return lateral;
    }

    /** Colorea la selección y bloquea módulos según el rol de la sesión. */
    private void actualizarNavegacion() {
        int seleccionado=pestañas.getSelectedIndex();
        if(seleccionado<0)return;
        tituloSeccion.setText(MODULOS[seleccionado]);
        for(int i=0;i<botonesNavegacion.length;i++){
            JButton boton=botonesNavegacion[i];
            if(boton==null)continue;
            boolean permitido=pestañas.isEnabledAt(i);
            boolean activo=i==seleccionado;
            boton.setEnabled(permitido);
            boton.setBackground(activo?TemaVisual.AZUL:TemaVisual.LATERAL);
            Color texto=!permitido?new Color(101,119,135):activo?Color.WHITE:new Color(195,210,224);
            boton.setForeground(texto);
            boton.setIcon(new IconoSeccion(ICONOS[i],texto,20));
        }
    }

    /** El contenido se controla mediante JTabbedPane; los botones son el menú visible. */
    private static final class PestanasOcultasUI extends BasicTabbedPaneUI {
        @Override protected int calculateTabAreaHeight(int placement,int runs,int maxHeight){return 0;}
        @Override protected int calculateTabAreaWidth(int placement,int runs,int maxWidth){return 0;}
        @Override protected void paintTabArea(Graphics g,int placement,int index){/* menú lateral */}
        @Override protected Insets getContentBorderInsets(int placement){return new Insets(0,0,0,0);}
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
        pestañas.setEnabledAt(1,true); // Vendedor puede consultar stock, solo admin lo modifica.
        botonConfiguracion.setVisible(sesion.administrador());
        for(JButton b:accionesProductos)b.setEnabled(sesion.administrador());
        pestañas.setEnabledAt(3,sesion.administrador());
        pestañas.setEnabledAt(4,sesion.administrador());
        pestañas.setSelectedIndex(0);
        actualizarNavegacion();
        revisarBackupAutomatico();
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
    /** Configuraciones administrativas la opción se conserva entre reinicios en esta PC. */
    private void abrirConfiguraciones(){
        if(sesion==null || !sesion.administrador())
            throw new SecurityException("Solo el administrador puede cambiar la configuración");
        JCheckBox automatico=new JCheckBox("Calcular precio de venta según un porcentaje de recargo");
        automatico.setSelected(configuracionProductos.precioPorRecargoActivo());
        automatico.setOpaque(false);
        JLabel explicacion=new JLabel("<html>Al activar esta opción, el formulario de productos calculará " +
            "el precio final a partir del costo y el porcentaje que ingreses.<br>" +
            "<b>Ejemplo:</b> costo $ 10.000,00 + recargo del 30% = precio $ 13.000,00.<br>" +
            "No modifica automáticamente productos que ya estén registrados.<br>" +
            "Esta preferencia se guarda localmente para este usuario de Windows.</html>");
        explicacion.setForeground(TemaVisual.SECUNDARIO);
        JPanel producto=new JPanel(new BorderLayout(0,14));
        producto.setBorder(BorderFactory.createEmptyBorder(20,18,20,18));
        producto.add(automatico,BorderLayout.NORTH);
        producto.add(explicacion,BorderLayout.CENTER);
        JTabbedPane ajustes=new JTabbedPane();
        ajustes.addTab("Productos",producto);
        JPanel seguridad=new JPanel(new BorderLayout(0,12));
        seguridad.setBorder(BorderFactory.createEmptyBorder(14,16,14,16));
        JCheckBox respaldoAutomatico=new JCheckBox("Crear un respaldo cada 24 horas mientras SIGAM-POS esté abierto");
        respaldoAutomatico.setSelected(backups.automatico());
        JPanel seleccionCarpeta=new JPanel(new BorderLayout(8,0));
        JTextField carpeta=new JTextField(backups.carpeta().toString());
        JButton examinar=new JButton("Elegir carpeta...");
        seleccionCarpeta.add(carpeta,BorderLayout.CENTER);
        seleccionCarpeta.add(examinar,BorderLayout.EAST);
        examinar.addActionListener(e->{
            JFileChooser elegir=new JFileChooser(carpeta.getText());
            elegir.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if(elegir.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)
                carpeta.setText(elegir.getSelectedFile().toPath().toString());
        });
        // Si el sistema está instalado fuera de las carpetas habituales, se puede elegir la herramienta.
        JTextField rutaDump=new JTextField(backups.ejecutableConfigurado());
        JButton buscarDump=new JButton("Examinar...");
        JPanel seleccionDump=new JPanel(new BorderLayout(8,0));
        seleccionDump.add(rutaDump,BorderLayout.CENTER);
        seleccionDump.add(buscarDump,BorderLayout.EAST);
        buscarDump.addActionListener(e->{
            JFileChooser selector=new JFileChooser();
            selector.setFileSelectionMode(JFileChooser.FILES_ONLY);
            selector.setDialogTitle("Seleccionar mysqldump.exe");
            if(selector.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)
                rutaDump.setText(selector.getSelectedFile().getAbsolutePath());
        });
        Path localizado=backups.localizarEjecutable();
        JLabel informacionDump=new JLabel(rutaDump.getText().isBlank()
            ?(localizado==null?"No se detectó mysqldump; seleccioná el archivo con Examinar."
                :"Detección automática: "+localizado)
            :"Usando ubicación elegida manualmente");
        informacionDump.setForeground(TemaVisual.SECUNDARIO);
        informacionDump.setFont(new Font("Segoe UI",Font.PLAIN,11));
        JButton crearAhora=new JButton("Crear respaldo ahora (.sql)");
        TemaVisual.botonPrincipal(crearAhora);
        crearAhora.addActionListener(e->ejecutar(()->{
            backups.configurar(Path.of(carpeta.getText()),respaldoAutomatico.isSelected());
            backups.configurarEjecutable(rutaDump.getText());
            crearBackupEnSegundoPlano();
        }));
        String ultimo=backups.ultimo()==null?"Todavía no hay respaldos registrados":
            "Último respaldo: "+backups.ultimo().atZone(ZoneId.systemDefault());
        JTextArea ayuda=new JTextArea("""
            Incluye datos y estructura completa de la base MySQL.
            Busca mysqldump.exe automáticamente; si no aparece, elegilo con Examinar.
            El nuevo respaldo NO fija el nombre de la base: admite restaurar en otra base de prueba.
            Los archivos SQL incluyen datos privados: guardalos fuera de GitHub.
            Para validar el backup, restauralo en sigam_pos_prueba, nunca en la base de producción.
            """);
        ayuda.setEditable(false);ayuda.setOpaque(false);ayuda.setFont(new Font("Segoe UI",Font.PLAIN,12));
        JPanel seguridadSuperior=new JPanel(new GridLayout(0,1,4,6));
        seguridadSuperior.add(new JLabel("Carpeta de respaldos:"));
        seguridadSuperior.add(seleccionCarpeta);
        seguridadSuperior.add(respaldoAutomatico);
        seguridadSuperior.add(new JLabel("mysqldump.exe (vacío = detectar automáticamente):"));
        seguridadSuperior.add(seleccionDump);
        seguridadSuperior.add(informacionDump);
        seguridadSuperior.add(new JLabel(ultimo));
        seguridad.add(seguridadSuperior,BorderLayout.NORTH);
        seguridad.add(ayuda,BorderLayout.CENTER);
        seguridad.add(crearAhora,BorderLayout.SOUTH);
        ajustes.addTab("Copias de seguridad",seguridad);
        ajustes.setPreferredSize(new Dimension(760,445));
        if(JOptionPane.showConfirmDialog(this,ajustes,"Configuraciones",
            JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
            configuracionProductos.establecerPrecioPorRecargo(automatico.isSelected());
            backups.configurar(Path.of(carpeta.getText()),respaldoAutomatico.isSelected());
            backups.configurarEjecutable(rutaDump.getText());
            mensaje("Configuración guardada.");
            revisarBackupAutomatico();
        }
    }

    /** Respaldo independiente de la interfaz y sin mostrar credenciales en pantalla. */
    private void crearBackupEnSegundoPlano(){
        if(sesion==null || !sesion.administrador())throw new SecurityException("Solo administradores pueden crear respaldos");
        if(!backupEnCurso.compareAndSet(false,true))return;
        new SwingWorker<Path,Void>(){
            @Override protected Path doInBackground() throws Exception {return backups.crear();}
            @Override protected void done(){
                backupEnCurso.set(false);
                try {mensaje("Respaldo guardado correctamente en:\n"+get());}
                catch(Exception e){Throwable causa=e.getCause()==null?e:e.getCause();
                    error(new IOExceptionConMensaje("No pudo completarse el respaldo: "+causa.getMessage()));}
            }
        }.execute();
    }
    private static final class IOExceptionConMensaje extends Exception {
        IOExceptionConMensaje(String mensaje){super(mensaje);}
    }
    private void revisarBackupAutomatico(){
        if(sesion==null || !sesion.administrador() || !backups.tocaAutomatico())return;
        if(ultimoIntentoBackup.isAfter(Instant.now().minusSeconds(3600)))return;
        ultimoIntentoBackup=Instant.now();
        crearBackupEnSegundoPlano();
    }

    /** El diálogo de guardado permite elegir destino y no exporta silenciosamente a una ruta fija. */
    private Path destinoCsv(String sugerido){
        JFileChooser elegir=new JFileChooser();
        elegir.setSelectedFile(new java.io.File(sugerido));
        elegir.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos CSV (.csv)","csv"));
        if(elegir.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return null;
        Path destino=elegir.getSelectedFile().toPath();
        if(!destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
            destino=destino.resolveSibling(destino.getFileName()+".csv");
        if(Files.exists(destino) && JOptionPane.showConfirmDialog(this,
            "El archivo existe. ¿Deseás reemplazarlo?","Confirmar exportación",
            JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return null;
        return destino;
    }

    private JPanel panelVentas(){
        JPanel raiz=new JPanel(new BorderLayout(14,14));raiz.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        JTextField buscar=new JTextField(24); JSpinner cantidad=new JSpinner(new SpinnerNumberModel(1,1,999999,1));
        JPanel superior=new JPanel(new BorderLayout(8,6));
        TemaVisual.tarjeta(superior);
        JPanel busqueda=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));
        busqueda.setOpaque(false);
        busqueda.add(new JLabel("Buscar por nombre / código:"));busqueda.add(buscar);
        busqueda.add(new JLabel("Cantidad:"));busqueda.add(cantidad);
        superior.add(busqueda,BorderLayout.NORTH);
        JPanel controlesCatalogo=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));
        controlesCatalogo.setOpaque(false);
        JButton agregar=new JButton("Agregar seleccionado"),quitar=new JButton("Quitar producto"),
            sumar=new JButton("+ 1"),restar=new JButton("− 1"),editarCantidad=new JButton("Editar cantidad");
        TemaVisual.botonPrincipal(agregar);
        controlesCatalogo.add(agregar);controlesCatalogo.add(botonAlertas);
        superior.add(controlesCatalogo,BorderLayout.SOUTH);
        botonAlertas.addActionListener(e->ejecutar(()->abrirAlertas()));
        raiz.add(superior,BorderLayout.NORTH);

        JTable catalogo=new JTable(tablaCatalogo);catalogo.setRowHeight(25);
        catalogo.setAutoCreateRowSorter(true);catalogo.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JTable tabla=new JTable(tablaCarrito);tabla.setRowHeight(25);
        JSplitPane medio=new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JPanel arriba=new JPanel(new BorderLayout(0,12));
        TemaVisual.tarjeta(arriba);
        JLabel tituloCatalogo=new JLabel("CATÁLOGO  ·  Doble clic para agregar");
        tituloCatalogo.setForeground(TemaVisual.AZUL_OSCURO);
        tituloCatalogo.setFont(new Font("Segoe UI",Font.BOLD,13));
        arriba.add(tituloCatalogo,BorderLayout.NORTH);
        arriba.add(new JScrollPane(catalogo),BorderLayout.CENTER);
        JPanel abajo=new JPanel(new BorderLayout(0,12));
        TemaVisual.tarjeta(abajo);
        JLabel tituloCarrito=new JLabel("CARRITO DE VENTA");
        tituloCarrito.setForeground(TemaVisual.AZUL_OSCURO);
        tituloCarrito.setFont(new Font("Segoe UI",Font.BOLD,13));
        abajo.add(tituloCarrito,BorderLayout.NORTH);
        abajo.add(new JScrollPane(tabla),BorderLayout.CENTER);
        JPanel controlesCarrito=new JPanel(new FlowLayout(FlowLayout.LEFT,8,5));
        controlesCarrito.add(new JLabel("Producto seleccionado:"));
        for(JButton boton:new JButton[]{restar,sumar,editarCantidad,quitar})controlesCarrito.add(boton);
        abajo.add(controlesCarrito,BorderLayout.SOUTH);
        medio.setTopComponent(arriba);medio.setBottomComponent(abajo);medio.setResizeWeight(0.48);
        medio.setBorder(BorderFactory.createEmptyBorder());
        medio.setDividerSize(10);
        SwingUtilities.invokeLater(()->medio.setDividerLocation(0.48));
        raiz.add(medio,BorderLayout.CENTER);

        JPanel inferior=new JPanel(new BorderLayout());
        TemaVisual.tarjeta(inferior);
        total.setForeground(TemaVisual.AZUL_OSCURO);
        total.setFont(new Font("Segoe UI",Font.BOLD,26));
        inferior.add(total,BorderLayout.WEST);
        JPanel acciones=new JPanel(new FlowLayout(FlowLayout.RIGHT));acciones.add(new JLabel("Caja activa:"));acciones.add(caja);
        JButton cobrar=new JButton("COBRAR");
        TemaVisual.botonPrincipal(cobrar);
        cobrar.setPreferredSize(new Dimension(138,43));
        acciones.add(cobrar);
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
        sumar.addActionListener(e->ejecutar(()->{
            int idx=indiceCarritoSeleccionado(tabla);
            actualizarCantidadCarrito(tabla,Math.addExact(carrito.get(idx).cantidad(),1));
        }));
        restar.addActionListener(e->ejecutar(()->{
            int idx=indiceCarritoSeleccionado(tabla);
            actualizarCantidadCarrito(tabla,carrito.get(idx).cantidad()-1);
        }));
        editarCantidad.addActionListener(e->ejecutar(()->{
            int idx=indiceCarritoSeleccionado(tabla);
            LineaVenta linea=carrito.get(idx);
            Producto actual=productos.buscar(linea.producto().codigo());
            if(actual==null)throw new IllegalArgumentException("Producto no disponible");
            JSpinner editor=new JSpinner(new SpinnerNumberModel(linea.cantidad(),0,999999,1));
            JPanel panel=form("Nueva cantidad (0 = quitar)",editor);
            if(JOptionPane.showConfirmDialog(this,panel,"Editar cantidad - "+actual.nombre(),
                JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            try{editor.commitEdit();}catch(java.text.ParseException ex){
                throw new IllegalArgumentException("Ingresá una cantidad entera válida");
            }
            actualizarCantidadCarrito(tabla,((Number)editor.getValue()).intValue());
        }));
        quitar.addActionListener(e->ejecutar(()->actualizarCantidadCarrito(tabla,0)));
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
        try {cantidad.commitEdit();}
        catch(java.text.ParseException e){throw new IllegalArgumentException("Cantidad inválida: ingresá un número entero");}
        int q=((Number)cantidad.getValue()).intValue();
        if(q<=0)throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        CarritoOperaciones.agregar(carrito,p,q);
        refrescarCarrito();cantidad.setValue(1);
    }
    private int indiceCarritoSeleccionado(JTable tabla) {
        int fila=tabla.getSelectedRow();
        if(fila<0)throw new IllegalArgumentException("Seleccioná un producto en el carrito");
        return tabla.convertRowIndexToModel(fila);
    }
    private void actualizarCantidadCarrito(JTable tabla,int nuevaCantidad) throws SQLException {
        int indice=indiceCarritoSeleccionado(tabla);
        String codigo=carrito.get(indice).producto().codigo();
        Producto p=productos.buscar(codigo);
        if(p==null && nuevaCantidad!=0)
            throw new IllegalArgumentException("El producto ya no existe");
        CarritoOperaciones.fijarCantidad(carrito,p==null?carrito.get(indice).producto():p,nuevaCantidad);
        refrescarCarrito();
        if(tabla.getRowCount()>0){
            int fila=Math.min(indice,tabla.getRowCount()-1);
            tabla.setRowSelectionInterval(fila,fila);
        }
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
        JPanel arriba=new JPanel(new BorderLayout(8,8));
        TemaVisual.tarjeta(arriba);
        JPanel busqueda=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));
        busqueda.setOpaque(false);
        busqueda.add(new JLabel("Buscar nombre o código:"));busqueda.add(filtro);
        arriba.add(busqueda,BorderLayout.NORTH);
        JButton nuevo=new JButton("Crear producto"),editar=new JButton("Editar seleccionado"),baja=new JButton("Desactivar"),activar=new JButton("Reactivar");
        TemaVisual.botonPrincipal(nuevo);
        JPanel accionesListado=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));
        accionesListado.setOpaque(false);
        for(JButton b:new JButton[]{nuevo,editar,baja,activar}){accionesListado.add(b);accionesProductos.add(b);}
        arriba.add(accionesListado,BorderLayout.SOUTH);
        raiz.add(arriba,BorderLayout.NORTH);
        JTable tabla=new JTable(tablaProductos);vistaProductos=tabla;tabla.setRowHeight(34);tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setAutoCreateRowSorter(true);
        // El estado se distingue sin depender exclusivamente del color.
        tabla.getColumnModel().getColumn(4).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable t,Object v,boolean seleccionado,
                boolean foco,int fila,int columna){
                super.getTableCellRendererComponent(t,v,seleccionado,foco,fila,columna);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(getFont().deriveFont(Font.BOLD));
                if(!seleccionado)setForeground("ACTIVO".equals(String.valueOf(v))?
                    new Color(25,111,77):TemaVisual.SECUNDARIO);
                return this;
            }
        });
        raiz.add(new JScrollPane(tabla),BorderLayout.CENTER);
        JPanel pieProductos=new JPanel(new BorderLayout());
        pieProductos.setOpaque(false);
        conteoProductos.setFont(new Font("Segoe UI",Font.PLAIN,12));
        conteoProductos.setForeground(TemaVisual.SECUNDARIO);
        pieProductos.add(conteoProductos,BorderLayout.WEST);
        JButton exportarInventario=new JButton("Exportar inventario CSV");
        accionesProductos.add(exportarInventario);
        pieProductos.add(exportarInventario,BorderLayout.EAST);
        raiz.add(pieProductos,BorderLayout.SOUTH);
        exportarInventario.addActionListener(e->ejecutar(()->{
            if(!sesion.administrador())throw new SecurityException("Solo administradores pueden exportar inventario");
            Path destino=destinoCsv("inventario_SIGAM-POS.csv");
            if(destino!=null)mensaje("Productos exportados: "+exportacion.inventario(destino,filtro.getText()));
        }));
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
        int activos=0;
        for(Producto p:productos.listar(filtro)){
            tablaProductos.addRow(new Object[]{p.codigo(),p.nombre(),Moneda.formato(p.precioVenta()),p.stock(),p.activo()?"ACTIVO":"INACTIVO"});
            if(p.activo())activos++;
        }
        conteoProductos.setText(tablaProductos.getRowCount()+" productos encontrados · "+activos+" activos");
    }
    private void editarProducto(Producto anterior,String filtro)throws Exception {
        JTextField codigo=new JTextField(anterior==null?"":anterior.codigo());
        JTextField nombre=new JTextField(anterior==null?"":anterior.nombre());
        CampoMoneda precio=new CampoMoneda(anterior==null?BigDecimal.ZERO:anterior.precioVenta());
        CampoMoneda costo=new CampoMoneda(anterior==null?BigDecimal.ZERO:anterior.costo());
        JTextField stock=new JTextField(anterior==null?"0":String.valueOf(anterior.stock()));
        JTextField min=new JTextField(anterior==null||anterior.stockMinimo()==null?"":anterior.stockMinimo().toString());
        JTextField max=new JTextField(anterior==null||anterior.stockMaximo()==null?"":anterior.stockMaximo().toString());
        codigo.setEditable(anterior==null);

        boolean calcularPorcentaje=configuracionProductos.precioPorRecargoActivo();
        JPanel panel;
        JSpinner recargo=null;
        if(calcularPorcentaje){
            BigDecimal inicial=anterior==null?new BigDecimal("30"):
                ConfiguracionProductos.recargoExistente(anterior.costo(),anterior.precioVenta());
            recargo=new JSpinner(new SpinnerNumberModel(inicial.doubleValue(),0d,10000d,0.5d));
            ((JSpinner.DefaultEditor)recargo.getEditor()).getTextField().setColumns(10);
            JLabel vistaPrecio=new JLabel();
            vistaPrecio.setFont(new Font("Segoe UI",Font.BOLD,17));
            vistaPrecio.setForeground(TemaVisual.AZUL);
            final JSpinner selectorRecargo=recargo;
            Runnable actualizar=()->{
                try{
                    BigDecimal nuevoCosto=costo.valor();
                    BigDecimal porcentaje=new BigDecimal(String.valueOf(selectorRecargo.getValue()));
                    boolean sinCambios=anterior!=null && nuevoCosto.compareTo(anterior.costo())==0
                        && porcentaje.compareTo(inicial)==0;
                    BigDecimal sugerido=sinCambios?anterior.precioVenta():
                        ConfiguracionProductos.precioSugerido(nuevoCosto,porcentaje);
                    vistaPrecio.setText(Moneda.formato(sugerido));
                }catch(RuntimeException ex){vistaPrecio.setText("Ingresá un costo válido");}
            };
            recargo.addChangeListener(e->actualizar.run());
            costo.getDocument().addDocumentListener(new DocumentListener(){
                private void cambio(){SwingUtilities.invokeLater(actualizar);}
                public void insertUpdate(DocumentEvent e){cambio();}
                public void removeUpdate(DocumentEvent e){cambio();}
                public void changedUpdate(DocumentEvent e){cambio();}
            });
            actualizar.run();
            panel=form("Código de producto",codigo,"Nombre",nombre,"Costo ($)",costo,
                "Recargo sobre costo (%)",recargo,"Stock",stock,"Stock mínimo (opcional)",min,
                "Stock máximo (opcional)",max,"Precio de venta (previsualización)",vistaPrecio);
            panel.setToolTipText("Recargo sobre costo, distinto del margen sobre la venta");
        } else {
            panel=form("Código de producto",codigo,"Nombre",nombre,"Precio venta ($)",precio,
                "Costo ($)",costo,"Stock",stock,"Stock mínimo (opcional)",min,
                "Stock máximo (opcional)",max);
        }
        if(JOptionPane.showConfirmDialog(this,panel,anterior==null?"Crear producto":"Editar producto",
            JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        String clave=codigo.getText().trim();
        if(anterior==null && productos.buscar(clave)!=null)
            throw new IllegalArgumentException("Ya existe un producto con ese código. Seleccionalo y elegí Editar.");
        BigDecimal costoIngresado=costo.valor();
        BigDecimal precioIngresado;
        if(calcularPorcentaje){
            recargo.commitEdit();
            BigDecimal porcentaje=new BigDecimal(String.valueOf(recargo.getValue()));
            BigDecimal porcentajeInicial=anterior==null?new BigDecimal("30"):
                ConfiguracionProductos.recargoExistente(anterior.costo(),anterior.precioVenta());
            boolean sinCambios=anterior!=null && costoIngresado.compareTo(anterior.costo())==0
                && porcentaje.compareTo(porcentajeInicial)==0;
            precioIngresado=sinCambios?anterior.precioVenta():
                ConfiguracionProductos.precioSugerido(costoIngresado,porcentaje);
        } else precioIngresado=precio.valor();
        Producto producto=new Producto(clave,nombre.getText(),precioIngresado,costoIngresado,
            enteroNoNegativo(stock,"Stock"),enteroOpcional(min),enteroOpcional(max),
            anterior==null||anterior.activo());
        productos.guardar(producto);
        cargarProductos(filtro);cargarCatalogo("");mensaje("Producto guardado correctamente");
    }
    private JPanel panelCaja(){
        JPanel raiz=new JPanel(new BorderLayout(12,12));
        raiz.setBorder(BorderFactory.createEmptyBorder(18,18,18,18));

        JLabel estado=new JLabel("Sin caja activa");
        estado.setFont(new Font("Segoe UI",Font.BOLD,17));
        estado.setForeground(TemaVisual.AZUL_OSCURO);
        JPanel encabezado=new JPanel(new BorderLayout(10,4));
        TemaVisual.tarjeta(encabezado);
        JLabel titulo=new JLabel("JORNADA DE CAJA");
        titulo.setFont(new Font("Segoe UI",Font.BOLD,11));
        titulo.setForeground(TemaVisual.SECUNDARIO);
        encabezado.add(titulo,BorderLayout.NORTH);
        encabezado.add(estado,BorderLayout.CENTER);
        JButton esperado=new JButton("Actualizar saldos");
        JPanel botonesEncabezado=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        botonesEncabezado.setOpaque(false);
        JButton exportarMovimientos=new JButton("Exportar movimientos CSV");
        botonesEncabezado.add(exportarMovimientos);
        botonesEncabezado.add(esperado);
        encabezado.add(botonesEncabezado,BorderLayout.EAST);
        exportarMovimientos.addActionListener(e->ejecutar(()->{
            if(!sesion.administrador())throw new SecurityException("Solo administradores pueden exportar caja");
            long id=cajaActual();
            Path destino=destinoCsv("movimientos_caja_"+id+".csv");
            if(destino!=null)mensaje("Movimientos exportados: "+exportacion.movimientosCaja(destino,id));
        }));
        raiz.add(encabezado,BorderLayout.NORTH);

        TarjetaSaldoCaja tarjetaEfectivo=new TarjetaSaldoCaja("Efectivo · Caja física",
            new String[]{"Fondo inicial","Cobros por ventas","Entradas manuales","Salidas manuales"});
        TarjetaSaldoCaja tarjetaDigital=new TarjetaSaldoCaja("Mercado Pago · Saldo declarado",
            new String[]{"Saldo inicial","Cobros digitales","Entradas manuales","Salidas manuales"});
        JPanel tarjetas=new JPanel(new GridLayout(1,2,12,0));
        tarjetas.setOpaque(false);
        tarjetas.add(tarjetaEfectivo);
        tarjetas.add(tarjetaDigital);

        JPanel actividad=new JPanel(new BorderLayout(0,8));
        TemaVisual.tarjeta(actividad);
        JLabel tituloActividad=new JLabel("ÚLTIMOS MOVIMIENTOS DE LA JORNADA");
        tituloActividad.setFont(new Font("Segoe UI",Font.BOLD,12));
        tituloActividad.setForeground(TemaVisual.AZUL_OSCURO);
        actividad.add(tituloActividad,BorderLayout.NORTH);
        JTextArea movimientos=new JTextArea("Abrí una jornada para ver sus movimientos.");
        movimientos.setEditable(false);
        movimientos.setLineWrap(true);
        movimientos.setWrapStyleWord(true);
        movimientos.setFont(new Font("Segoe UI",Font.PLAIN,13));
        movimientos.setBackground(TemaVisual.BLANCO);
        movimientos.setForeground(TemaVisual.TEXTO);
        actividad.add(new JScrollPane(movimientos),BorderLayout.CENTER);

        JPanel centro=new JPanel(new BorderLayout(0,12));
        centro.setOpaque(false);
        centro.add(tarjetas,BorderLayout.NORTH);
        centro.add(actividad,BorderLayout.CENTER);
        raiz.add(centro,BorderLayout.CENTER);

        JPanel acciones=new JPanel(new GridLayout(2,3,10,10));
        JButton abrir=new JButton("Iniciar jornada / Abrir caja"),
            entrada=new JButton("Registrar entrada (+)"),salida=new JButton("Registrar salida (-)"),
            historial=new JButton("Historial de movimientos"),
            movimientoDigital=new JButton("Movimiento Mercado Pago"),
            cerrar=new JButton("Finalizar jornada (arqueo)");
        TemaVisual.botonPrincipal(abrir);
        for(JButton b:new JButton[]{abrir,entrada,salida,movimientoDigital,historial,cerrar})acciones.add(b);
        JPanel pie=new JPanel(new BorderLayout(0,9));
        TemaVisual.tarjeta(pie);
        pie.add(acciones,BorderLayout.CENTER);
        JLabel aclaracion=new JLabel("Cada operación registra caja de destino, importe, motivo y responsable.");
        aclaracion.setForeground(TemaVisual.SECUNDARIO);
        aclaracion.setFont(new Font("Segoe UI",Font.PLAIN,12));
        pie.add(aclaracion,BorderLayout.SOUTH);
        raiz.add(pie,BorderLayout.SOUTH);

        Runnable actualizar=()->{
            if(caja.getText().isBlank()) {
                estado.setText("Sin caja activa · Iniciá una jornada para operar");
                tarjetaEfectivo.sinDatos("Sin jornada abierta");
                tarjetaDigital.sinDatos("Sin jornada abierta");
                movimientos.setText("Abrí una jornada para consultar sus movimientos.");
                return;
            }
            try {
                long id=cajaActual();
                estado.setText("Caja #"+id+" · Responsable: "+(sesion==null?"—":sesion.nombre()));
                CajaServicio.Resumen efectivo=cajas.resumen(id);
                tarjetaEfectivo.mostrar(efectivo.esperado(),new BigDecimal[]{
                    efectivo.fondoInicial(),efectivo.ventasEfectivo(),
                    efectivo.entradas(),efectivo.salidas()},"Efectivo esperado · Cálculo local");
                CajaDigitalServicio.Resumen mp=digital.resumen(id);
                if(mp==null)tarjetaDigital.sinDatos("No habilitada en esta jornada");
                else tarjetaDigital.mostrar(mp.esperado(),new BigDecimal[]{
                    mp.inicial(),mp.cobros(),mp.entradas(),mp.salidas()},
                    "Saldo esperado declarado · Sin API externa");
                String texto=cajas.historial(id);
                if(mp!=null)texto+="\n\n"+digital.historial(id);
                movimientos.setText(texto);
                movimientos.setCaretPosition(0);
            } catch(Exception ex) {
                movimientos.setText("No fue posible consultar la jornada: "+ex.getMessage());
            }
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
        entrada.addActionListener(e->ejecutar(()->{registrarMovimiento(true,false);actualizar.run();}));
        salida.addActionListener(e->ejecutar(()->{registrarMovimiento(false,false);actualizar.run();}));
        esperado.addActionListener(e->ejecutar(()->{cajaActual();actualizar.run();}));
        movimientoDigital.addActionListener(e->ejecutar(()->{registrarMovimiento(null,true);actualizar.run();}));
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
    /** CAJ-07: cada movimiento exige elegir explícitamente caja y, si es digital, su medio. */
    private void registrarMovimiento(Boolean entradaFija,boolean preferirDigital) throws SQLException {
        long id=cajaActual();
        boolean disponibleDigital=digital.habilitada(id);
        JComboBox<String> tipo=new JComboBox<>(new String[]{"ENTRADA","SALIDA"});
        if(entradaFija!=null){
            tipo.setSelectedIndex(entradaFija?0:1);
            tipo.setEnabled(false);
        }
        JComboBox<String> destino=new JComboBox<>(new String[]{"Efectivo (caja física)","Mercado Pago (caja digital)"});
        if(preferirDigital)destino.setSelectedIndex(1);
        JComboBox<CajaDigitalServicio.MedioRegistro> medio=new JComboBox<>(CajaDigitalServicio.MedioRegistro.values());
        JLabel advertencia=new JLabel("Solo movimientos acreditados o debitados en la cuenta declarada.");
        advertencia.setFont(advertencia.getFont().deriveFont(11f));
        Runnable actualizarDestino=()->{
            boolean esDigital=destino.getSelectedIndex()==1;
            medio.setEnabled(esDigital);
            advertencia.setVisible(esDigital);
        };
        destino.addActionListener(ev->actualizarDestino.run());
        actualizarDestino.run();
        CampoMoneda monto=new CampoMoneda();JTextField motivo=new JTextField();
        JPanel formulario=new JPanel(new BorderLayout(8,8));
        formulario.add(form("Operación",tipo,"Caja de destino",destino,
            "Medio digital (si aplica)",medio,"Importe",monto,"Motivo obligatorio",motivo),BorderLayout.NORTH);
        formulario.add(advertencia,BorderLayout.SOUTH);
        String titulo=entradaFija==null?"Movimiento de caja":(entradaFija?"Registrar entrada":"Registrar salida");
        if(JOptionPane.showConfirmDialog(this,formulario,titulo,JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        boolean entrada="ENTRADA".equals(tipo.getSelectedItem());
        boolean esDigital=destino.getSelectedIndex()==1;
        if(esDigital && !disponibleDigital)
            throw new IllegalStateException("No hay caja digital abierta. Abrí Mercado Pago al iniciar una nueva jornada.");
        BigDecimal valor=monto.valor();
        if(valor.signum()<=0)throw new IllegalArgumentException("El importe debe ser mayor que cero");
        String descripcion=motivo.getText().trim();
        if(descripcion.isEmpty()||descripcion.length()>255)
            throw new IllegalArgumentException("Ingresá un motivo (máximo 255 caracteres)");
        CajaDigitalServicio.MedioRegistro medioElegido=esDigital?(CajaDigitalServicio.MedioRegistro)medio.getSelectedItem():null;
        String destinoElegido=esDigital?"Mercado Pago / "+medioElegido:"Efectivo";
        String resumen="Operación: "+(entrada?"ENTRADA":"SALIDA")+"\n"+
            "Destino: "+destinoElegido+"\nImporte: "+Moneda.formato(valor)+"\nMotivo: "+descripcion;
        if(JOptionPane.showConfirmDialog(this,resumen,"Confirmar movimiento",JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE)!=JOptionPane.YES_OPTION)return;
        if(esDigital)digital.registrar(id,sesion.empleadoId(),entrada,valor,descripcion,medioElegido);
        else cajas.registrar(id,sesion.empleadoId(),entrada?new IngresoCaja(valor,descripcion,false):new EgresoCaja(valor,descripcion));
        mensaje("Movimiento registrado en "+destinoElegido+": "+Moneda.formato(valor));
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
        // GridBag conserva el tamaño natural de las fichas incluso cuando hay pocos empleados.
        JPanel contenedor=new JPanel(new GridBagLayout());
        contenedor.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel empleados=new JPanel(new BorderLayout(10,10));
        empleados.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JButton alta=new JButton("Crear empleado"),recargar=new JButton("Actualizar empleados");
        TemaVisual.botonPrincipal(alta);
        JPanel cabecera=new JPanel(new FlowLayout(FlowLayout.LEFT,10,6));
        cabecera.setOpaque(false);
        cabecera.add(alta);cabecera.add(recargar);
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
        JPanel acciones=new JPanel(new FlowLayout(FlowLayout.LEFT,9,8));
        acciones.setBackground(TemaVisual.BLANCO);
        JButton nuevo=new JButton("Crear turno"),editar=new JButton("Modificar turno"),
            asignar=new JButton("Asignar turno"),quitar=new JButton("Quitar asignación"),
            ver=new JButton("Ver empleado asignado"),refrescar=new JButton("Actualizar horarios");
        for(JButton button:new JButton[]{nuevo,editar,asignar,quitar,ver,refrescar})acciones.add(button);
        pantallaTurnos.add(acciones,BorderLayout.NORTH);
        JTable tabla=new JTable(tablaTurnos);tabla.setRowHeight(35);tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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
        int numeroFicha=0;
        for(PersonalServicio.EmpleadoFicha e:personal.listarEmpleados()) {
            JPanel ficha=new JPanel(new BorderLayout(8,8));
            ficha.setPreferredSize(new Dimension(402,230));
            TemaVisual.tarjeta(ficha);
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
            GridBagConstraints ubicacion=new GridBagConstraints();
            ubicacion.gridx=numeroFicha%2;ubicacion.gridy=numeroFicha/2;
            ubicacion.anchor=GridBagConstraints.NORTHWEST;
            ubicacion.insets=new Insets(5,5,5,5);
            contenedor.add(ficha,ubicacion);
            numeroFicha++;
        }
        GridBagConstraints espacio=new GridBagConstraints();
        espacio.gridx=2;espacio.gridy=numeroFicha/2+1;
        espacio.weightx=1;espacio.weighty=1;
        contenedor.add(Box.createGlue(),espacio);
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
        tabs.addTab("Panel comercial",new PanelAnalisisComercial(
            codigo->ejecutar(()->seleccionarProducto(codigo))));
        tabs.addTab("Resumen",panelResumen());
        tabs.addTab("Historial de ventas",panelHistorialVentas());
        tabs.addTab("Historial de jornadas",panelHistorialJornadas());
        return tabs;
    }
    /** Resumen con indicadores independientes y pagos desglosados, sin bloques de texto fijo. */
    private JPanel panelResumen(){
        JPanel raiz=new JPanel(new BorderLayout(14,14));
        raiz.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        JTextField desde=new JTextField(LocalDate.now().minusDays(30).toString(),12);
        JTextField hasta=new JTextField(LocalDate.now().toString(),12);
        JButton consultar=new JButton("Consultar período");
        TemaVisual.botonPrincipal(consultar);
        JPanel filtros=new JPanel(new FlowLayout(FlowLayout.LEFT,10,5));
        TemaVisual.tarjeta(filtros);
        filtros.add(new JLabel("Desde"));filtros.add(desde);
        filtros.add(new JLabel("Hasta"));filtros.add(hasta);filtros.add(consultar);
        raiz.add(filtros,BorderLayout.NORTH);

        JLabel cantidad=new JLabel("—"),facturacion=new JLabel("—"),promedio=new JLabel("—");
        JPanel indicadores=new JPanel(new GridLayout(1,3,12,0));
        indicadores.setOpaque(false);
        indicadores.add(tarjetaResumen("VENTAS DEL PERÍODO",cantidad));
        indicadores.add(tarjetaResumen("FACTURACIÓN NOMINAL",facturacion));
        indicadores.add(tarjetaResumen("TICKET PROMEDIO",promedio));

        DefaultTableModel modeloPagos=new DefaultTableModel(
            new Object[]{"Medio de pago","Cantidad de cobros","Importe nominal"},0){
            @Override public boolean isCellEditable(int fila,int columna){return false;}
        };
        JTable tablaPagos=new JTable(modeloPagos);
        tablaPagos.setAutoCreateRowSorter(true);
        JPanel pagos=new JPanel(new BorderLayout(0,12));
        TemaVisual.tarjeta(pagos);
        JLabel titulo=new JLabel("RECAUDACIÓN POR MEDIO DE PAGO");
        titulo.setFont(new Font("Segoe UI",Font.BOLD,13));
        titulo.setForeground(TemaVisual.AZUL_OSCURO);
        pagos.add(titulo,BorderLayout.NORTH);
        pagos.add(new JScrollPane(tablaPagos),BorderLayout.CENTER);

        JPanel contenido=new JPanel(new BorderLayout(0,12));
        contenido.setOpaque(false);
        contenido.add(indicadores,BorderLayout.NORTH);
        contenido.add(pagos,BorderLayout.CENTER);
        raiz.add(contenido,BorderLayout.CENTER);
        JLabel nota=new JLabel("Importes nominales. Los cobros mixtos se separan por medio, sin duplicar las ventas.");
        nota.setFont(new Font("Segoe UI",Font.PLAIN,12));
        nota.setForeground(TemaVisual.SECUNDARIO);
        raiz.add(nota,BorderLayout.SOUTH);

        consultar.addActionListener(e->ejecutar(()->{
            LocalDate a=LocalDate.parse(desde.getText()),b=LocalDate.parse(hasta.getText());
            var resumen=analisis.resumen(a,b);
            cantidad.setText(String.valueOf(resumen.cantidadVentas()));
            facturacion.setText(Moneda.formato(resumen.montoTotal()));
            promedio.setText(Moneda.formato(resumen.promedio()));
            modeloPagos.setRowCount(0);
            for(var pago:historiales.recaudacion(a,b))
                modeloPagos.addRow(new Object[]{pago.medio(),pago.operaciones(),Moneda.formato(pago.monto())});
        }));
        return raiz;
    }

    private static JPanel tarjetaResumen(String titulo,JLabel dato){
        JPanel panel=new JPanel(new BorderLayout(0,9));
        TemaVisual.tarjeta(panel);
        JLabel nombre=new JLabel(titulo);
        nombre.setFont(new Font("Segoe UI",Font.BOLD,11));
        nombre.setForeground(TemaVisual.SECUNDARIO);
        dato.setFont(new Font("Segoe UI",Font.BOLD,23));
        dato.setForeground(TemaVisual.AZUL_OSCURO);
        panel.add(nombre,BorderLayout.NORTH);
        panel.add(dato,BorderLayout.CENTER);
        return panel;
    }

    private JPanel panelHistorialVentas(){
        JPanel raiz=new JPanel(new BorderLayout(8,8));raiz.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JTextField desde=new JTextField(LocalDate.now().minusDays(30).toString(),10),
            hasta=new JTextField(LocalDate.now().toString(),10);
        JComboBox<String> medio=new JComboBox<>(new String[]{"TODOS","EFECTIVO","TRANSFERENCIA","TARJETA","QR"});
        JButton buscar=new JButton("Consultar"),detalle=new JButton("Ver detalle y pagos"),
            exportarVentas=new JButton("Exportar ventas CSV");
        JPanel filtros=new JPanel(new FlowLayout(FlowLayout.LEFT));
        for(Component a:new Component[]{new JLabel("Desde (AAAA-MM-DD)"),desde,
            new JLabel("Hasta"),hasta,new JLabel("Medio"),medio,buscar,detalle,exportarVentas})filtros.add(a);
        TemaVisual.tarjeta(filtros);
        raiz.add(filtros,BorderLayout.NORTH);
        JLabel estadoConsulta=new JLabel("Seleccioná un período para consultar las ventas.");
        estadoConsulta.setForeground(TemaVisual.SECUNDARIO);
        estadoConsulta.setFont(new Font("Segoe UI",Font.PLAIN,12));
        raiz.add(estadoConsulta,BorderLayout.SOUTH);
        DefaultTableModel m=new DefaultTableModel(new Object[]{"Venta","Caja","Empleado","Fecha","Total"},0){
            @Override public boolean isCellEditable(int f,int c){return false;}
        };
        JTable tabla=new JTable(m);tabla.setAutoCreateRowSorter(true);
        raiz.add(new JScrollPane(tabla),BorderLayout.CENTER);
        buscar.addActionListener(e->ejecutar(()->{
            m.setRowCount(0);
            for(var v:historiales.listar(LocalDate.parse(desde.getText()),LocalDate.parse(hasta.getText()),
                  String.valueOf(medio.getSelectedItem())))
                m.addRow(new Object[]{v.id(),v.cajaId(),v.empleado(),v.fecha(),Moneda.formato(v.total())});
            estadoConsulta.setText(m.getRowCount()+" ventas encontradas · Doble clic para ver el detalle.");
        }));
        exportarVentas.addActionListener(e->ejecutar(()->{
            if(!sesion.administrador())throw new SecurityException("Solo administradores pueden exportar ventas");
            Path destino=destinoCsv("ventas_SIGAM-POS.csv");
            if(destino!=null)mensaje("Ventas exportadas: "+exportacion.historialVentas(destino,
                LocalDate.parse(desde.getText()),LocalDate.parse(hasta.getText()),String.valueOf(medio.getSelectedItem())));
        }));
        Runnable ver=()->ejecutar(()->{
            int f=tabla.getSelectedRow();if(f<0)throw new IllegalArgumentException("Seleccioná una venta");
            long id=((Number)m.getValueAt(tabla.convertRowIndexToModel(f),0)).longValue();
            JTextArea texto=new JTextArea(historiales.detalle(id),16,52);texto.setEditable(false);
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
        JButton buscar=new JButton("Consultar"),detalle=new JButton("Ver detalle"),
            exportarJornadas=new JButton("Exportar caja CSV");
        JPanel filtros=new JPanel(new FlowLayout(FlowLayout.LEFT));
        for(Component a:new Component[]{new JLabel("Desde"),desde,new JLabel("Hasta"),hasta,new JLabel("Estado"),estado,buscar,detalle,exportarJornadas})filtros.add(a);
        TemaVisual.tarjeta(filtros);
        raiz.add(filtros,BorderLayout.NORTH);
        JLabel estadoConsulta=new JLabel("Seleccioná un período para consultar las jornadas.");
        estadoConsulta.setForeground(TemaVisual.SECUNDARIO);
        estadoConsulta.setFont(new Font("Segoe UI",Font.PLAIN,12));
        raiz.add(estadoConsulta,BorderLayout.SOUTH);
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
            estadoConsulta.setText(m.getRowCount()+" jornadas encontradas · Seleccioná una para ver los movimientos.");
        }));
        exportarJornadas.addActionListener(e->ejecutar(()->{
            if(!sesion.administrador())throw new SecurityException("Solo administradores pueden exportar caja");
            Path destino=destinoCsv("jornadas_SIGAM-POS.csv");
            if(destino!=null)mensaje("Jornadas exportadas: "+exportacion.historialJornadas(destino,
                LocalDate.parse(desde.getText()),LocalDate.parse(hasta.getText()),String.valueOf(estado.getSelectedItem())));
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
    /** Formulario consistente para alta, edición y credenciales (sin cambios de validación). */
    private static JPanel form(Object... campos) {
        if(campos.length%2!=0)throw new IllegalArgumentException("Cada campo necesita una etiqueta");
        JPanel panel=new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));
        GridBagConstraints c=new GridBagConstraints();
        c.fill=GridBagConstraints.HORIZONTAL;
        c.anchor=GridBagConstraints.WEST;
        c.insets=new Insets(6,7,6,7);
        for(int i=0;i<campos.length;i+=2) {
            c.gridy=i/2;c.gridx=0;c.weightx=0;
            JLabel etiqueta=new JLabel(String.valueOf(campos[i]));
            etiqueta.setFont(new Font("Segoe UI",Font.PLAIN,12));
            etiqueta.setForeground(TemaVisual.TEXTO);
            panel.add(etiqueta,c);
            c.gridx=1;c.weightx=1;
            Component control=campos[i+1] instanceof Component valor?valor:new JLabel(String.valueOf(campos[i+1]));
            if(control instanceof JTextField campo)campo.setColumns(Math.max(campo.getColumns(),18));
            panel.add(control,c);
        }
        return panel;
    }
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
