package ar.com.sigampos;
import ar.com.sigampos.util.Moneda;
import java.math.BigDecimal;
public class PruebaImportes {
    public static void main(String[] args) {
        probar("2000", "2000.00");probar("2000,50","2000.50");
        probar("2.000,50","2000.50");probar("2000.50","2000.50");
        probar("10.000","10000.00");
        for(String invalido:new String[]{"","12,345","hola","-10","5.000.00"}){
            try{Moneda.parsear(invalido);throw new AssertionError("Debió rechazar: "+invalido);}
            catch(IllegalArgumentException esperado){}
        }
        System.out.println("OK: parseo de importes");
    }
    private static void probar(String dato,String esperado){
        BigDecimal actual=Moneda.parsear(dato);
        if(actual.compareTo(new BigDecimal(esperado))!=0)throw new AssertionError(dato+" -> "+actual);
    }
}
