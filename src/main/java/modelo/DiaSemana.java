package ar.com.sigampos.modelo;

import java.time.DayOfWeek;

/** El ordinal coincide con DayOfWeek: lunes = 1, domingo = 7. */
public enum DiaSemana {
    LUNES("L", "lunes"), MARTES("M", "martes"), MIERCOLES("X", "miercoles"),
    JUEVES("J", "jueves"), VIERNES("V", "viernes"), SABADO("S", "sabado"), DOMINGO("D", "domingo");

    private final String abreviatura;
    private final String columna;
    DiaSemana(String abreviatura, String columna) { this.abreviatura = abreviatura; this.columna = columna; }
    public String abreviatura() { return abreviatura; }
    public String columna() { return columna; }
    public static DiaSemana desde(DayOfWeek dia) { return values()[dia.getValue() - 1]; }
}
