package ar.com.sigampos.ui;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import ar.com.sigampos.util.CampoMoneda;
import java.awt.*;

/**
 * Estilo visual de SIGAM-POS. La lógica comercial no depende de esta clase.
 * Si FlatLaf no está disponible, usa Nimbus para poder seguir ejecutando.
 */
public final class TemaVisual {
    public static final Color AZUL = new Color(22, 109, 191);
    public static final Color AZUL_OSCURO = new Color(15, 45, 75);
    public static final Color LATERAL = new Color(17, 40, 65);
    public static final Color FONDO = new Color(245, 247, 251);
    public static final Color BLANCO = Color.WHITE;
    public static final Color TEXTO = new Color(39, 54, 71);
    public static final Color SECUNDARIO = new Color(103, 118, 133);
    public static final Color BORDE = new Color(223, 230, 239);

    private TemaVisual() { }

    /** Configuración global, ejecutada antes de construir cualquier ventana Swing. */
    public static void instalar() {
        try {
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
        } catch (Exception flatNoDisponible) {
            try {
                for (UIManager.LookAndFeelInfo tema : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(tema.getName())) {
                        UIManager.setLookAndFeel(tema.getClassName());
                        break;
                    }
                }
            } catch (Exception ignored) {
                // Swing conservará el tema predeterminado si Nimbus tampoco está disponible.
            }
        }
        Font base = new Font("Segoe UI", Font.PLAIN, 13);
        UIManager.put("defaultFont", base);
        UIManager.put("Panel.background", FONDO);
        UIManager.put("Table.background", BLANCO);
        UIManager.put("Table.selectionBackground", new Color(218, 235, 251));
        UIManager.put("Table.selectionForeground", TEXTO);
        UIManager.put("TableHeader.background", new Color(240, 244, 250));
        UIManager.put("TableHeader.foreground", TEXTO);
        UIManager.put("Table.showHorizontalLines", Boolean.TRUE);
        UIManager.put("Table.showVerticalLines", Boolean.FALSE);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 11);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("ScrollBar.width", 11);
        UIManager.put("TabbedPane.tabHeight", 42);
        UIManager.put("TabbedPane.background", BLANCO);
        UIManager.put("TabbedPane.tabAreaBackground", BLANCO);
        UIManager.put("TabbedPane.contentAreaColor", BLANCO);
        UIManager.put("TabbedPane.selectedBackground", BLANCO);
        UIManager.put("TabbedPane.foreground", TEXTO);
        UIManager.put("TabbedPane.selectedForeground", AZUL_OSCURO);
        UIManager.put("TabbedPane.showTabSeparators", false);
        UIManager.put("TabbedPane.underlineColor", AZUL);
        UIManager.put("OptionPane.messageFont", base);
        UIManager.put("OptionPane.buttonFont", base);
    }

    /** Mejora los componentes existentes sin modificar sus acciones o modelos de datos. */
    public static void aplicar(Component componente) {
        if (componente instanceof JTable tabla) {
            tabla.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            tabla.setRowHeight(36);
            // Separadores minimalistas con esquinas suaves, también para columnas con renderizador propio.
            tabla.setShowGrid(false);
            tabla.setIntercellSpacing(new Dimension(2, 2));
            for(int i=0;i<tabla.getColumnCount();i++){
                javax.swing.table.TableColumn columna=tabla.getColumnModel().getColumn(i);
                TableCellRenderer actual=columna.getCellRenderer();
                if(actual==null)actual=tabla.getDefaultRenderer(tabla.getColumnClass(i));
                if(!(actual instanceof RenderizadorSuave))
                    columna.setCellRenderer(new RenderizadorSuave(actual));
            }
            tabla.setSelectionBackground(new Color(217, 235, 250));
            tabla.setSelectionForeground(TEXTO);
            tabla.setBackground(BLANCO);
            tabla.setFillsViewportHeight(true);
            JTableHeader cabecera = tabla.getTableHeader();
            if (cabecera != null) {
                cabecera.setPreferredSize(new Dimension(0, 41));
                cabecera.setBackground(new Color(235, 241, 248));
                cabecera.setForeground(AZUL_OSCURO);
                cabecera.setFont(new Font("Segoe UI", Font.BOLD, 12));
                cabecera.setReorderingAllowed(false);
            }
        } else if (componente instanceof JScrollPane desplazamiento) {
            desplazamiento.setBorder(BorderFactory.createLineBorder(BORDE));
            desplazamiento.getViewport().setBackground(BLANCO);
        } else if (componente instanceof JButton boton) {
            if (!"navegacion".equals(boton.getClientProperty("sigam.tipo"))) {
                boton.setFont(new Font("Segoe UI", Font.BOLD, 12));
                boton.setFocusPainted(false);
                boton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                boton.setMargin(new Insets(8, 13, 8, 13));
            }
        } else if (componente instanceof JTextField campo) {
            campo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            if(campo instanceof CampoMoneda dinero)dinero.ajustarMargen();
            else campo.setMargin(new Insets(5, 9, 5, 9));
        } else if (componente instanceof JComboBox<?> lista) {
            lista.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        }
        if (componente instanceof Container contenedor) {
            for (Component hijo : contenedor.getComponents()) aplicar(hijo);
        }
    }

    /** Aplica el mismo borde incluso a celdas que poseen un renderizador personalizado. */
    private static final class RenderizadorSuave implements TableCellRenderer {
        private static final javax.swing.border.Border BORDE_CELDA=
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(193,208,223,150),1,true),
                BorderFactory.createEmptyBorder(0,7,0,7));
        private final TableCellRenderer delegado;
        private RenderizadorSuave(TableCellRenderer delegado){this.delegado=delegado;}
        @Override public Component getTableCellRendererComponent(JTable tabla,Object valor,
            boolean seleccionado,boolean foco,int fila,int columna){
            Component celda=delegado.getTableCellRendererComponent(tabla,valor,seleccionado,foco,fila,columna);
            if(celda instanceof JComponent componente)componente.setBorder(BORDE_CELDA);
            return celda;
        }
    }

    public static void botonPrincipal(JButton boton) {
        boton.putClientProperty("JButton.buttonType", "roundRect");
        boton.setBackground(AZUL);
        boton.setForeground(BLANCO);
        boton.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }

    public static void tarjeta(JComponent componente) {
        componente.setOpaque(true);
        componente.setBackground(BLANCO);
        componente.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDE),
            BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));
    }
}
