package ar.com.sigampos.util;
import javax.swing.*;
import java.awt.event.*;
import java.math.BigDecimal;
/** Campo con formato es-AR al perder foco, flexible mientras se escribe. */
public final class CampoMoneda extends JTextField {
    public CampoMoneda(){this(BigDecimal.ZERO);}
    public CampoMoneda(BigDecimal inicial){super(Moneda.formato(inicial),12);
        addFocusListener(new FocusAdapter(){
            @Override public void focusGained(FocusEvent e){
                try {setText(Moneda.editable(valor()));}catch(RuntimeException ignored){}
                selectAll();
            }
            @Override public void focusLost(FocusEvent e){
                try {setText(Moneda.formato(valor()));}catch(RuntimeException ignored){}
            }
        });
    }
    public BigDecimal valor(){return Moneda.parsear(getText());}
}
