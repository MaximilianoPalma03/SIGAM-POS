package ar.com.sigampos.ui;

import ar.com.sigampos.datos.*;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.sql.Connection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Primer inicio no destructivo. En una base existente se valida sin ejecutar DDL. */
public final class AsistenteInicio {
    private AsistenteInicio() { }

    private static <T> T enInterfaz(Callable<T> accion) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return accion.call();
        AtomicReference<T> resultado = new AtomicReference<>();
        AtomicReference<Exception> problema = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { resultado.set(accion.call()); }
            catch (Exception e) { problema.set(e); }
        });
        if (problema.get() != null) throw problema.get();
        return resultado.get();
    }

    private static void mensaje(String texto, int tipo) {
        try { enInterfaz(() -> { JOptionPane.showMessageDialog(null, texto, "SIGAM-POS", tipo); return null; }); }
        catch (Exception e) { System.err.println("SIGAM-POS: "+texto); }
    }

    private static <T> T esperar(String titulo, Callable<T> trabajo) throws Exception {
        final JDialog[] dialogo = new JDialog[1];
        FutureTask<T> tarea = new FutureTask<>(() -> {
            try { return trabajo.call(); }
            finally { SwingUtilities.invokeLater(() -> { if (dialogo[0]!=null) dialogo[0].dispose(); }); }
        });
        enInterfaz(() -> {
            JDialog d = new JDialog((Frame)null, "Preparando SIGAM-POS", true);
            d.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            JPanel p = new JPanel(new BorderLayout(10,10));
            p.setBorder(BorderFactory.createEmptyBorder(20,22,20,22));
            p.add(new JLabel(titulo), BorderLayout.NORTH);
            JProgressBar bar = new JProgressBar(); bar.setIndeterminate(true);
            p.add(bar, BorderLayout.SOUTH);
            d.add(p); d.pack(); d.setSize(Math.max(440,d.getWidth()),d.getHeight());
            d.setLocationRelativeTo(null); dialogo[0] = d;
            new Thread(tarea,"sigam-inicializador").start();
            d.setVisible(true);
            return null;
        });
        try { return tarea.get(); }
        catch (ExecutionException e) {
            Throwable causa=e.getCause();
            if (causa instanceof Exception ex) throw ex;
            throw new IOException("Error interno de instalación",causa);
        }
    }

    public static boolean preparar() {
        try {
            ConfiguracionBD.Credenciales desdeEntorno = ConfiguracionBD.desdeEntorno();
            if (desdeEntorno != null) {
                // Desarrollo: respetar estrictamente SIGAM_DB_URL/USER/PASSWORD. Nunca recrear la BD.
                comprobar(desdeEntorno);
                ConfiguracionBD.activar(desdeEntorno);
                return true;
            }
            ConfiguracionBD.Credenciales guardada = ConfiguracionBD.cargar();
            if (guardada != null) {
                esperar("Comprobando tu instalación anterior y sus datos...", () -> {
                    if (guardada.local()) MysqlLocal.asegurarIniciado(guardada);
                    else comprobar(guardada);
                    return null;
                });
                ConfiguracionBD.activar(guardada);
                return true;
            }
            // La pérdida del archivo de conexión NO significa que los datos locales hayan desaparecido.
            // Nunca ofrecer inicializar otra base sobre una carpeta MySQL existente.
            if (java.nio.file.Files.exists(MysqlLocal.datos())) {
                mensaje("Se encontraron datos de una instalación local anterior en:\n"+
                    MysqlLocal.datos()+"\n\n"+
                    "Falta la configuración protegida de conexión. No se creará una base nueva. " +
                    "Recuperá la configuración o restaurá un respaldo con asistencia.",
                    JOptionPane.ERROR_MESSAGE);
                return false;
            }
            // Sin credenciales NO es posible inspeccionar un MySQL ajeno sin autorización.
            return asistentePrimeraVez();
        } catch (Exception e) {
            mensaje("No se pudo iniciar sin riesgos para los datos:\n"+mensajeError(e)+
                "\n\nSIGAM-POS no recreó ni modificó tu base existente.",JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private static String mensajeError(Throwable e) {
        String m=e.getMessage();
        return m==null ? e.getClass().getSimpleName() : m;
    }

    private static void comprobar(ConfiguracionBD.Credenciales c) throws Exception {
        try (Connection conexion=ConfiguracionBD.conectar(c)) {
            ValidadorEsquema.comprobar(conexion);
        }
    }

    private static boolean asistentePrimeraVez() throws Exception {
        while (true) {
            String[] opciones = MysqlLocal.incluido()
                ? new String[]{"Usar base existente", "Crear instalación local nueva", "Cancelar"}
                : new String[]{"Usar base existente", "Cancelar"};
            int respuesta = enInterfaz(() -> JOptionPane.showOptionDialog(null,
                "Bienvenido a SIGAM-POS.\n\n" +
                "Si ya utilizabas SIGAM-POS, elegí USAR BASE EXISTENTE para conservar tus registros.\n" +
                "Solo elegí instalación nueva si nunca tuviste una base de SIGAM-POS.\n\n" +
                (MysqlLocal.incluido() ? "La distribución incluye MySQL local." :
                "No se encontró MySQL incluido: instalá el paquete Windows completo."),
                "Preparación inicial", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, opciones, opciones[0]));
            if (respuesta<0 || respuesta==opciones.length-1) return false;
            if (respuesta==0) {
                ConfiguracionBD.Credenciales c = solicitarExistente();
                if (c == null) continue;
                try {
                    esperar("Verificando las tablas de la base existente...", () -> {comprobar(c);return null;});
                    ConfiguracionBD.guardar(c); // No escribe nada en MySQL.
                    ConfiguracionBD.activar(c);
                    mensaje("Base compatible encontrada. Se conservaron todos tus datos.",JOptionPane.INFORMATION_MESSAGE);
                    return true;
                } catch (Exception error) {
                    mensaje("No se pudo validar la base:\n"+mensajeError(error)+
                        "\n\nNo se ha creado ni borrado ninguna tabla.",JOptionPane.WARNING_MESSAGE);
                }
            } else {
                boolean confirmado = enInterfaz(() -> JOptionPane.showConfirmDialog(null,
                    "Se creará una INSTALACIÓN LOCAL NUEVA de MySQL en:\n"+
                    ConfiguracionBD.datosAplicacion()+"\n\n"+
                    "No se tocará ninguna base MySQL existente en el puerto 3306.\n"+
                    "Los datos permanecerán aunque se reemplace la aplicación.\n\n¿Continuar?",
                    "Crear instalación nueva", JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE))
                    == JOptionPane.YES_OPTION;
                if (!confirmado) continue;
                try {
                    ConfiguracionBD.Credenciales c = esperar("Instalando y verificando MySQL local. Puede tardar unos minutos...",
                        MysqlLocal::instalarNueva);
                    ConfiguracionBD.activar(c);
                    mensaje("Base instalada correctamente. Al iniciar, crearemos el administrador inicial.",
                        JOptionPane.INFORMATION_MESSAGE);
                    return true;
                } catch (Exception error) {
                    mensaje("Falló la instalación local:\n"+mensajeError(error)+
                        "\n\nLa carpeta de datos no se eliminará ni inicializará nuevamente. " +
                        "Guardá el registro de errores para recuperarla.",JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        }
    }

    private static ConfiguracionBD.Credenciales solicitarExistente() throws Exception {
        JTextField servidor = new JTextField("127.0.0.1");
        JTextField puerto = new JTextField("3306");
        JTextField usuario = new JTextField("root");
        JPasswordField clave = new JPasswordField();
        JPanel p = new JPanel(new GridLayout(0,2,8,8));
        p.add(new JLabel("Servidor:"));p.add(servidor);
        p.add(new JLabel("Puerto:"));p.add(puerto);
        p.add(new JLabel("Usuario MySQL:"));p.add(usuario);
        p.add(new JLabel("Contraseña MySQL:"));p.add(clave);
        int confirmar = enInterfaz(() -> JOptionPane.showConfirmDialog(null,p,
            "Conectar a una instalación existente",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE));
        if (confirmar != JOptionPane.OK_OPTION) return null;
        int num;
        try { num = Integer.parseInt(puerto.getText().trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Puerto MySQL incorrecto"); }
        if (num<1 || num>65535 || !servidor.getText().trim().matches("[a-zA-Z0-9.\\-]+"))
            throw new IllegalArgumentException("Servidor o puerto no válido");
        char[] secreto=clave.getPassword();
        try {
            return new ConfiguracionBD.Credenciales("jdbc:mysql://"+servidor.getText().trim()+":"+num+
                "/sigam_pos?connectionTimeZone=LOCAL",usuario.getText().trim(),new String(secreto),false);
        } finally {java.util.Arrays.fill(secreto,'\0');clave.setText("");}
    }
}
