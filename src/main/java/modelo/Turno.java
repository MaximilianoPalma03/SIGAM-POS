package ar.com.sigampos.modelo;

import java.time.Duration;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Horario semanal reutilizable. El día marcado siempre es el de INICIO. */
public final class Turno {
    private static final long MINUTOS_SEMANA = 7L * 24 * 60;
    private final long id;
    private final String nombre;
    private final LocalTime inicio;
    private final LocalTime fin;
    private final Set<DiaSemana> dias;
    private final boolean activo;

    public Turno(long id, String nombre, LocalTime inicio, LocalTime fin, Set<DiaSemana> dias, boolean activo) {
        if (nombre == null || nombre.isBlank() || nombre.trim().length() > 50)
            throw new IllegalArgumentException("El nombre del turno debe tener entre 1 y 50 caracteres");
        validarDuracion(inicio, fin);
        if (dias == null || dias.isEmpty()) throw new IllegalArgumentException("Seleccioná al menos un día del turno");
        this.id = id;
        this.nombre = nombre.trim();
        this.inicio = inicio;
        this.fin = fin;
        this.dias = Set.copyOf(dias);
        this.activo = activo;
    }
    public long id() { return id; }
    public String nombre() { return nombre; }
    public LocalTime inicio() { return inicio; }
    public LocalTime fin() { return fin; }
    public Set<DiaSemana> dias() { return dias; }
    public boolean activo() { return activo; }
    public long duracionMinutos() { return validarDuracion(inicio, fin); }
    public boolean cruzaMedianoche() { return fin.isBefore(inicio); }
    public String descripcionDias() {
        return java.util.Arrays.stream(DiaSemana.values()).filter(dias::contains)
            .map(DiaSemana::abreviatura).collect(Collectors.joining(", "));
    }
    public static long validarDuracion(LocalTime inicio, LocalTime fin) {
        if (inicio == null || fin == null) throw new IllegalArgumentException("Indicá inicio y finalización");
        if (inicio.equals(fin)) throw new IllegalArgumentException("Inicio y fin no pueden ser iguales");
        long minutos = Duration.between(inicio, fin).toMinutes();
        if (minutos < 0) minutos += 24 * 60;
        if (minutos <= 0 || minutos > 12 * 60)
            throw new IllegalArgumentException("Un turno debe durar más de 0 y como máximo 12 horas");
        return minutos;
    }
    /** Incluye cruces del domingo al lunes y del día anterior. Coincidencia exacta en borde NO se superpone. */
    public boolean seSuperponeCon(Turno otro) {
        for (DiaSemana dia : dias) {
            long desde = dia.ordinal() * 1440L + inicio.toSecondOfDay() / 60;
            long hasta = desde + duracionMinutos();
            for (DiaSemana otroDia : otro.dias) {
                long otroDesde = otroDia.ordinal() * 1440L + otro.inicio.toSecondOfDay() / 60;
                for (long semana = -1; semana <= 1; semana++) {
                    long a = otroDesde + semana * MINUTOS_SEMANA;
                    long b = a + otro.duracionMinutos();
                    if (desde < b && a < hasta) return true;
                }
            }
        }
        return false;
    }
}
