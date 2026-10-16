package ar.com.sigampos;

import ar.com.sigampos.servicio.AnalisisAvanzadoServicio;
import ar.com.sigampos.servicio.ReglasAnalisis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Pruebas deterministas sin MySQL: periodos, rentabilidad, cobertura, horas y oportunidades. */
public final class PruebaAnalisis {
    private static BigDecimal b(String v){return new BigDecimal(v);}
    private static void iguales(Object a,Object b){
        if(!java.util.Objects.equals(a,b))throw new AssertionError("Esperado "+b+", recibido "+a);
    }
    public static void main(String[] args){
        iguales(ReglasAnalisis.porcentaje(b("250.00"),b("1000.00")),b("25.00"));
        iguales(ReglasAnalisis.porcentaje(b("100.00"),b("0")),null);
        iguales(ReglasAnalisis.diasCobertura(10,5,10),b("20.0"));
        iguales(ReglasAnalisis.diasCobertura(0,4,10),b("0.0"));
        iguales(ReglasAnalisis.diasCobertura(10,0,10),null);
        iguales(ReglasAnalisis.oportunidad(false,0,3,10,10,10,b("100"),b("50")),"INACTIVO");
        iguales(ReglasAnalisis.oportunidad(true,0,3,10,10,10,b("100"),b("50")),"SIN STOCK");
        iguales(ReglasAnalisis.oportunidad(true,3,5,10,10,10,b("100"),b("50")),"STOCK BAJO");
        iguales(ReglasAnalisis.oportunidad(true,8,2,16,10,16,b("100"),b("50")),
            "REPONER: hasta 7 días de cobertura");
        iguales(ReglasAnalisis.oportunidad(true,40,2,0,30,0,b("0"),b("0")),"SIN VENTAS EN PERÍODO");
        iguales(ReglasAnalisis.oportunidad(true,40,2,4,10,4,b("100"),b("10")),
            "REVISAR MARGEN: inferior al 15%");
        // Sin el costo de todas las unidades, no inventar alerta por margen.
        iguales(ReglasAnalisis.oportunidad(true,40,2,4,10,2,b("40"),b("1")),"SIN ALERTAS");
        var k=new AnalisisAvanzadoServicio.Indicadores(2,b("100.00"),b("50.00"),
            4,2,b("40.00"),b("30.00"),b("10.00"));
        iguales(k.coberturaCostosPorcentaje(),b("50.00"));
        if(k.costoCompleto())throw new AssertionError("Cobertura parcial informada como completa");
        var horas=List.of(new AnalisisAvanzadoServicio.Hora(10,1,b("100")),
            new AnalisisAvanzadoServicio.Hora(18,2,b("150")),
            new AnalisisAvanzadoServicio.Hora(19,1,b("150")));
        var p1=new AnalisisAvanzadoServicio.ProductoFila("A","A",true,0,2,5,b("100"),
            5,b("100"),b("30"),b("0.0"),"SIN STOCK");
        var p2=new AnalisisAvanzadoServicio.ProductoFila("B","B",false,0,2,5,b("100"),
            5,b("100"),b("30"),b("0.0"),"INACTIVO");
        var informe=new AnalisisAvanzadoServicio.Informe(LocalDate.of(2026,10,1),LocalDate.of(2026,10,2),
            k,List.of(),horas,List.of(),List.of(p2,p1),List.of());
        iguales(informe.horaPico().hora(),18);
        var horasInflacion=List.of(new AnalisisAvanzadoServicio.Hora(10,1,b("500000")),
            new AnalisisAvanzadoServicio.Hora(18,5,b("25000")));
        var informeHoras=new AnalisisAvanzadoServicio.Informe(LocalDate.of(2026,10,1),LocalDate.of(2026,10,2),
            k,List.of(),horasInflacion,List.of(),List.of(),List.of());
        iguales(informeHoras.horaPico().hora(),18); // cantidad de operaciones, no precios inflados
        iguales(informe.oportunidades().size(),1);
        iguales(informe.oportunidades().get(0).codigo(),"A");
        boolean invalido=false;
        try{AnalisisAvanzadoServicio.validarPeriodo(LocalDate.of(2026,10,2),LocalDate.of(2026,10,1));}
        catch(IllegalArgumentException ex){invalido=true;}
        if(!invalido)throw new AssertionError("Aceptó un rango de fechas invertido");
        invalido=false;
        try{AnalisisAvanzadoServicio.validarPeriodo(LocalDate.of(2025,1,1),LocalDate.of(2026,10,1));}
        catch(IllegalArgumentException ex){invalido=true;}
        if(!invalido)throw new AssertionError("Aceptó más de 366 días");
        System.out.println("Pruebas de analisis comercial: OK");
    }
}
