package ar.com.sigampos;
import ar.com.sigampos.servicio.AlertasServicio;
/** Ejecutable sin MySQL. */
public final class PruebaAlertas {
    private static void verificar(int stock,Integer minimo,String esperado){
        String nivel=AlertasServicio.clasificar(stock,minimo);
        if(!java.util.Objects.equals(nivel,esperado))
            throw new AssertionError("Stock "+stock+" mínimo "+minimo+": "+nivel+" != "+esperado);
    }
    public static void main(String[] args){
        verificar(0,null,"SIN_STOCK");verificar(0,5,"SIN_STOCK");
        verificar(3,5,"BAJO");verificar(5,5,"BAJO");
        verificar(6,5,"PROXIMO");verificar(7,5,"PROXIMO");
        verificar(8,5,null);verificar(12,null,null);
        System.out.println("PruebaAlertas OK");
    }
}
