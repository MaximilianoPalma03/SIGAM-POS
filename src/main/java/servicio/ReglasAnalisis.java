package ar.com.sigampos.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Reglas reproducibles del tablero; no constituyen predicciones ni ganancia neta. */
public final class ReglasAnalisis {
    private ReglasAnalisis() {}

    public static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if(parte==null || total==null || total.signum()<=0) return null;
        return parte.multiply(BigDecimal.valueOf(100)).divide(total,2,RoundingMode.HALF_UP);
    }

    /** Días de stock según el ritmo promedio de unidades vendidas en el período. */
    public static BigDecimal diasCobertura(int stock,long unidadesVendidas,long dias) {
        if(stock<0 || unidadesVendidas<=0 || dias<=0)return null;
        return BigDecimal.valueOf(stock).multiply(BigDecimal.valueOf(dias))
            .divide(BigDecimal.valueOf(unidadesVendidas),1,RoundingMode.HALF_UP);
    }

    public static String oportunidad(boolean activo,int stock,Integer minimo,long unidades,
                                     long dias,long unidadesConCosto,BigDecimal facturacionConCosto,
                                     BigDecimal gananciaConocida) {
        if(!activo)return "INACTIVO";
        if(stock==0)return "SIN STOCK";
        if(minimo!=null && stock<=minimo)return "STOCK BAJO";
        BigDecimal cobertura=diasCobertura(stock,unidades,dias);
        if(cobertura!=null && cobertura.compareTo(BigDecimal.valueOf(7))<=0)
            return "REPONER: hasta 7 días de cobertura";
        if(unidades==0)return "SIN VENTAS EN PERÍODO";
        // Si faltan costos históricos, no sugerimos cambios de precio por margen.
        if(unidadesConCosto==unidades) {
            BigDecimal margen=porcentaje(gananciaConocida,facturacionConCosto);
            if(margen!=null && margen.compareTo(BigDecimal.valueOf(15))<0)
                return "REVISAR MARGEN: inferior al 15%";
        }
        return "SIN ALERTAS";
    }

    public static int prioridad(String tipo) {
        if("SIN STOCK".equals(tipo))return 0;
        if("STOCK BAJO".equals(tipo))return 1;
        if(tipo.startsWith("REPONER"))return 2;
        if(tipo.startsWith("REVISAR MARGEN"))return 3;
        if("SIN VENTAS EN PERÍODO".equals(tipo))return 4;
        return 5;
    }
}
