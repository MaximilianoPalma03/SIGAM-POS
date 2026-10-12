package ar.com.sigampos;

import ar.com.sigampos.modelo.*;
import java.time.LocalTime;
import java.util.EnumSet;

/** Pruebas puras, sin servidor MySQL. Ejecutar con -ea. */
public final class PruebaTurnos {
    private static LocalTime t(String hora) {return LocalTime.parse(hora);}
    private static Turno turno(String inicio,String fin,DiaSemana... dias) {
        return new Turno(0,"Prueba",t(inicio),t(fin),EnumSet.copyOf(java.util.Arrays.asList(dias)),true);
    }
    private static void invalido(String ini,String fin) {
        boolean rechazado=false;
        try {turno(ini,fin,DiaSemana.LUNES);}catch(IllegalArgumentException ex){rechazado=true;}
        assert rechazado : "Debería rechazar "+ini+"-"+fin;
    }
    public static void main(String[] args) {
        assert turno("08:00","16:00",DiaSemana.LUNES).duracionMinutos()==480;
        assert turno("22:00","06:00",DiaSemana.LUNES).duracionMinutos()==480;
        assert turno("20:00","08:00",DiaSemana.LUNES).duracionMinutos()==720;
        assert turno("23:00","10:00",DiaSemana.MARTES).duracionMinutos()==660;
        invalido("16:00","15:00"); // 23 horas
        invalido("08:00","22:00"); // 14 horas
        invalido("16:00","16:00"); // duración cero
        Turno lunesNoche=turno("22:00","06:00",DiaSemana.LUNES);
        assert lunesNoche.seSuperponeCon(turno("05:30","09:00",DiaSemana.MARTES));
        assert !lunesNoche.seSuperponeCon(turno("06:00","09:00",DiaSemana.MARTES));
        Turno domingoNoche=turno("22:00","06:00",DiaSemana.DOMINGO);
        assert domingoNoche.seSuperponeCon(turno("05:00","08:00",DiaSemana.LUNES));
        assert !domingoNoche.seSuperponeCon(turno("06:00","08:00",DiaSemana.LUNES));
        assert !turno("16:00","18:00",DiaSemana.LUNES,DiaSemana.MARTES,DiaSemana.MIERCOLES,
            DiaSemana.JUEVES,DiaSemana.VIERNES).seSuperponeCon(turno("10:00","12:45",DiaSemana.SABADO));
        boolean diasVacios=false;
        try {new Turno(0,"Prueba",t("08:00"),t("09:00"),EnumSet.noneOf(DiaSemana.class),true);}
        catch(IllegalArgumentException e){diasVacios=true;}
        assert diasVacios;
        System.out.println("Pruebas de turnos semanales/nocturnos: OK");
    }
}
