package ar.com.sigampos;

import ar.com.sigampos.ui.VentanaPrincipal;
import ar.com.sigampos.ui.AsistenteInicio;
import ar.com.sigampos.ui.TemaVisual;
import ar.com.sigampos.datos.BloqueoInstancia;
import ar.com.sigampos.datos.Conexion;
import ar.com.sigampos.datos.MysqlLocal;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Arranque: validar o preparar conexión ANTES de abrir la interfaz comercial. */
public final class Main {
    public static void main(String[] args) {
        TemaVisual.instalar();
        try {
            if (!BloqueoInstancia.adquirir()) {
                JOptionPane.showMessageDialog(null,
                    "SIGAM-POS ya está abierto en esta sesión de Windows.",
                    "Aplicación en uso",JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (java.io.IOException e) {
            JOptionPane.showMessageDialog(null,"No se pudo reservar la instancia: "+e.getMessage(),
                "Error de inicio",JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!AsistenteInicio.preparar()) return;
        if (Conexion.parametros()!=null && Conexion.parametros().local()) {
            Runtime.getRuntime().addShutdownHook(new Thread(
                MysqlLocal::detenerServidorLocal,"sigam-mysql-cierre"));
        }
        SwingUtilities.invokeLater(() -> new VentanaPrincipal().setVisible(true));
    }
}
