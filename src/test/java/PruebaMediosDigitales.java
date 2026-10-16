package ar.com.sigampos;

import ar.com.sigampos.servicio.CajaDigitalServicio;
import java.math.BigDecimal;

public final class PruebaMediosDigitales {
    public static void main(String[] args) {
        for(CajaDigitalServicio.MedioRegistro medio:CajaDigitalServicio.MedioRegistro.values()) {
            CajaDigitalServicio.validarMovimientoManual(new BigDecimal("500.00"),"Ajuste QA",medio);
        }
        comprobar(CajaDigitalServicio.MedioRegistro.values().length==3,"Faltan medios digitales");
        esperarRechazo(()->CajaDigitalServicio.validarMovimientoManual(new BigDecimal("0"),"Ajuste QA",CajaDigitalServicio.MedioRegistro.QR));
        esperarRechazo(()->CajaDigitalServicio.validarMovimientoManual(new BigDecimal("100")," ",CajaDigitalServicio.MedioRegistro.TRANSFERENCIA));
        esperarRechazo(()->CajaDigitalServicio.validarMovimientoManual(new BigDecimal("100"),"Ajuste QA",null));
        System.out.println("PruebaMediosDigitales: OK");
    }
    private static void esperarRechazo(Runnable operacion){
        boolean rechazado=false;
        try{operacion.run();}catch(IllegalArgumentException e){rechazado=true;}
        comprobar(rechazado,"Se aceptó un movimiento digital inválido");
    }
    private static void comprobar(boolean condicion,String mensaje){if(!condicion)throw new AssertionError(mensaje);}
}
