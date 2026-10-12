package ar.com.sigampos;
import ar.com.sigampos.modelo.*;
import java.math.BigDecimal;

/** Prueba independiente de MySQL; ejecutar con -ea. */
public final class PruebaDominio {
    public static void main(String[] args) {
        MovimientoCaja entrada = new IngresoCaja(new BigDecimal("1500.00"),"Cambio",false);
        MovimientoCaja salida = new EgresoCaja(new BigDecimal("400.00"),"Insumos");
        assert entrada.impactoEfectivo().compareTo(new BigDecimal("1500.00"))==0;
        assert salida.impactoEfectivo().compareTo(new BigDecimal("-400.00"))==0;
        assert entrada.impactoEfectivo().add(salida.impactoEfectivo()).compareTo(new BigDecimal("1100.00"))==0;
        Producto p = new Producto("A1","Cable USB",new BigDecimal("3000.00"),new BigDecimal("2000.00"),0,null,null,true);
        assert p.stock()==0; assert p.rentabilidadPorcentaje().compareTo(new BigDecimal("50.00"))==0;
        boolean rechazo = false;
        try {new Producto("A2","Inválido",new BigDecimal("0"),BigDecimal.ONE,-1,null,null,true);}catch(IllegalArgumentException ex){rechazo=true;}
        assert rechazo;
        System.out.println("Pruebas del dominio: OK");
    }
}
