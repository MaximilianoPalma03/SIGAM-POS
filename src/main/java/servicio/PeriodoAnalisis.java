package ar.com.sigampos.servicio;

import java.time.LocalDate;
import java.util.Objects;

/** Ventanas móviles de meses calendario: incluyen hoy, nunca borran datos históricos. */
public enum PeriodoAnalisis {
    UN_MES("1 mes",1), TRES_MESES("3 meses",3), SEIS_MESES("6 meses",6);
    private final String etiqueta;
    private final int meses;
    PeriodoAnalisis(String etiqueta,int meses){this.etiqueta=etiqueta;this.meses=meses;}
    public String etiqueta(){return etiqueta;}
    public int meses(){return meses;}
    public LocalDate desde(LocalDate hasta){
        Objects.requireNonNull(hasta,"Fecha de corte obligatoria");
        return hasta.minusMonths(meses).plusDays(1);
    }
    public int dias(LocalDate hasta){return (int)java.time.temporal.ChronoUnit.DAYS.between(desde(hasta),hasta)+1;}
}
