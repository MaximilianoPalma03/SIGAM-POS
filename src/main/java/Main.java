package ar.com.sigampos;
import ar.com.sigampos.ui.VentanaPrincipal;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
public final class Main {
    public static void main(String[] args) {
        try {UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(Exception ignored){}
        SwingUtilities.invokeLater(()->new VentanaPrincipal().setVisible(true));
    }
}
