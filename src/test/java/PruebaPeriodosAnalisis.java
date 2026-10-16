package ar.com.sigampos;

import ar.com.sigampos.servicio.PeriodoAnalisis;
import ar.com.sigampos.servicio.AnalisisAvanzadoServicio;
import ar.com.sigampos.ui.PanelAnalisisComercial;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

/** Verifica ventanas móviles y gráficos con datos simulados sin esperar meses. */
public final class PruebaPeriodosAnalisis {
    private static void cierto(boolean condicion,String mensaje){
        if(!condicion)throw new AssertionError(mensaje);
    }
    private static long totalEnVentana(List<LocalDate> fechas,PeriodoAnalisis periodo,LocalDate hoy){
        return fechas.stream().filter(f->!f.isBefore(periodo.desde(hoy)) && !f.isAfter(hoy)).count();
    }
    public static void main(String[] args){
        LocalDate hoy=LocalDate.of(2026,10,15);
        var fechas=List.of(hoy.minusMonths(7),hoy.minusMonths(5),hoy.minusMonths(2),hoy.minusDays(10));
        cierto(totalEnVentana(fechas,PeriodoAnalisis.UN_MES,hoy)==1,"1 mes debe incluir solo los últimos registros");
        cierto(totalEnVentana(fechas,PeriodoAnalisis.TRES_MESES,hoy)==2,"3 meses debe cubrir registros recientes");
        cierto(totalEnVentana(fechas,PeriodoAnalisis.SEIS_MESES,hoy)==3,"6 meses debe excluir los 7 meses");
        cierto(PeriodoAnalisis.UN_MES.desde(LocalDate.of(2026,3,31)).equals(LocalDate.of(2026,3,1)),
            "Mes con 31 días no debe fallar");
        // Ventanas inclusivas: la fecha límite entra y la anterior no.
        for(PeriodoAnalisis p:PeriodoAnalisis.values()){
            var limite=p.desde(hoy);
            cierto(totalEnVentana(List.of(limite),p,hoy)==1,p.etiqueta()+": límite inclusivo");
            cierto(totalEnVentana(List.of(limite.minusDays(1)),p,hoy)==0,p.etiqueta()+": pasado excluido");
            AnalisisAvanzadoServicio.validarPeriodo(limite,hoy);
        }
        var indicadores=new AnalisisAvanzadoServicio.Indicadores(1,new BigDecimal("300"),new BigDecimal("300"),
            1,0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
        var lista=List.of(new AnalisisAvanzadoServicio.Dia(hoy.minusDays(8),1,new BigDecimal("120")),
            new AnalisisAvanzadoServicio.Dia(hoy.minusDays(7),1,new BigDecimal("180")));
        var informe=new AnalisisAvanzadoServicio.Informe(hoy.minusDays(29),hoy,indicadores,lista,
            List.of(),List.of(),List.of(),List.of());
        cierto(PanelAnalisisComercial.serieVentas(informe,true,PeriodoAnalisis.UN_MES).size()==2,
            "Gráfico mensual: puntos diarios");
        var semanas=PanelAnalisisComercial.serieVentas(informe,true,PeriodoAnalisis.SEIS_MESES);
        cierto(semanas.size()>=1&&semanas.size()<=2,"Gráfico semestral: agrupar semanalmente");
        double suma=semanas.stream().mapToDouble(p->p.valor()).sum();
        cierto(Math.abs(suma-300)<.001,"La agrupación no debe cambiar la facturación");
        System.out.println("Ventanas 1/3/6 meses y agregación de gráficos: OK");
    }
}
