package ar.com.sigampos.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Formato argentino consistente; acepta importe simple o agrupado de forma local. */
public final class Moneda {
    private static final Locale ARGENTINA=Locale.forLanguageTag("es-AR");
    private Moneda() {}
    public static String formato(BigDecimal importe) {
        if(importe==null)return "—";
        NumberFormat f=NumberFormat.getCurrencyInstance(ARGENTINA);
        f.setMinimumFractionDigits(2);f.setMaximumFractionDigits(2);
        return f.format(importe);
    }
    public static String editable(BigDecimal importe) {
        return importe==null?"":importe.setScale(2,RoundingMode.HALF_UP).toPlainString().replace('.',',');
    }
    public static BigDecimal parsear(String texto) {
        if(texto==null || texto.isBlank())throw new IllegalArgumentException("Ingresá un importe válido");
        String n=texto.strip().replace("$","").replace(" ","").replace("\u00a0","").replace("\u202f","");
        // 10.000,50 / 10,50 / 10000.50 / 10000 / 10.000
        if(n.matches("[0-9]{1,3}(\\.[0-9]{3})+(,[0-9]{1,2})?"))n=n.replace(".","").replace(',','.');
        else if(n.matches("[0-9]+,[0-9]{1,2}"))n=n.replace(',','.');
        else if(!n.matches("[0-9]+(\\.[0-9]{1,2})?"))throw new IllegalArgumentException("Importe inválido. Ejemplos: 2000, 2000,50 o 2.000,50");
        try {return new BigDecimal(n).setScale(2,RoundingMode.UNNECESSARY);}
        catch(ArithmeticException | NumberFormatException e){throw new IllegalArgumentException("Importe inválido; usá máximo dos decimales");}
    }
}
