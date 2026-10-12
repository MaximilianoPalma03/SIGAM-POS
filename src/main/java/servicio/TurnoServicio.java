package ar.com.sigampos.servicio;

import ar.com.sigampos.datos.Conexion;
import ar.com.sigampos.modelo.DiaSemana;
import ar.com.sigampos.modelo.Turno;
import java.sql.*;
import java.time.LocalTime;
import java.util.*;

/** Administración de horarios reutilizables, días y asignaciones a empleados. */
public final class TurnoServicio {
    public record EmpleadoResumen(long id, String nombre, String dni) {
        @Override public String toString() { return nombre + " — DNI " + dni + " (#" + id + ")"; }
    }
    private static final String CAMPOS = "id,nombre,hora_inicio,hora_fin,lunes,martes,miercoles,jueves,viernes,sabado,domingo,estado";

    public List<EmpleadoResumen> empleadosActivos() throws SQLException {
        List<EmpleadoResumen> resultado = new ArrayList<>();
        try(Connection c=Conexion.abrir();PreparedStatement ps=c.prepareStatement("SELECT id,nombre,dni FROM Empleado WHERE estado='ACTIVO' ORDER BY nombre,id");ResultSet rs=ps.executeQuery()) {
            while(rs.next()) resultado.add(new EmpleadoResumen(rs.getLong(1),rs.getString(2),rs.getString(3)));
        }
        return resultado;
    }
    public List<Turno> listar() throws SQLException {
        try(Connection c=Conexion.abrir()) { return leerTodos(c,"SELECT " + CAMPOS + " FROM Turno ORDER BY estado,nombre,id"); }
    }
    public List<Turno> asignados(long empleadoId) throws SQLException {
        try(Connection c=Conexion.abrir()) { return asignados(c, empleadoId, -1); }
    }
    private List<Turno> asignados(Connection c, long empleadoId, long excluirId) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT T." + CAMPOS.replace(",", ",T.") + " FROM Turno T JOIN EmpleadoTurno ET ON ET.turno_id=T.id WHERE ET.empleado_id=? AND T.id<>?")) {
            ps.setLong(1,empleadoId);ps.setLong(2,excluirId);
            return leerLista(ps);
        }
    }
    public Turno buscar(long turnoId) throws SQLException {
        try(Connection c=Conexion.abrir()) { return buscar(c,turnoId); }
    }
    private Turno buscar(Connection c,long turnoId) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT "+CAMPOS+" FROM Turno WHERE id=?")) {
            ps.setLong(1,turnoId);
            try(ResultSet rs=ps.executeQuery()) { return rs.next()?leerTurno(rs):null; }
        }
    }
    public long crear(String nombre, LocalTime inicio, LocalTime fin, Set<DiaSemana> dias) throws SQLException {
        Turno t=new Turno(0,nombre,inicio,fin,dias,true);
        try(Connection c=Conexion.abrir();PreparedStatement ps=c.prepareStatement(
                "INSERT INTO Turno(nombre,hora_inicio,hora_fin,lunes,martes,miercoles,jueves,viernes,sabado,domingo,estado) VALUES(?,?,?,?,?,?,?,?,?,?,'ACTIVO')",
                Statement.RETURN_GENERATED_KEYS)) {
            cargarDatos(ps,t);ps.executeUpdate();return VentaServicio.idGenerado(ps);
        }
    }
    public void modificar(long turnoId,String nombre,LocalTime inicio,LocalTime fin,Set<DiaSemana> dias) throws SQLException {
        Turno actualizado=new Turno(turnoId,nombre,inicio,fin,dias,true);
        try(Connection c=Conexion.abrir()) {
            c.setAutoCommit(false);
            boolean lock=obtenerBloqueoTurnos(c);
            try {
                // Se bloquea el turno para evitar asignarlo mientras se lo modifica.
                try(PreparedStatement ps=c.prepareStatement("SELECT id FROM Turno WHERE id=? AND estado='ACTIVO' FOR UPDATE")) {
                    ps.setLong(1,turnoId);
                    try(ResultSet r=ps.executeQuery()) { if(!r.next()) throw new IllegalArgumentException("Turno inexistente o inactivo"); }
                }
                List<Long> empleados=new ArrayList<>();
                try(PreparedStatement ps=c.prepareStatement("SELECT empleado_id FROM EmpleadoTurno WHERE turno_id=? ORDER BY empleado_id")) {
                    ps.setLong(1,turnoId);
                    try(ResultSet r=ps.executeQuery()) { while(r.next()) empleados.add(r.getLong(1)); }
                }
                for(long empleado:empleados) bloquearEmpleado(c,empleado);
                for(long empleado:empleados) validarNoSuperpone(actualizado,asignados(c,empleado,turnoId));
                try(PreparedStatement ps=c.prepareStatement("UPDATE Turno SET nombre=?,hora_inicio=?,hora_fin=?,lunes=?,martes=?,miercoles=?,jueves=?,viernes=?,sabado=?,domingo=? WHERE id=?")) {
                    cargarDatos(ps,actualizado);ps.setLong(11,turnoId);ps.executeUpdate();
                }
                c.commit();
            } catch(SQLException | RuntimeException e) {c.rollback();throw e;}
            finally {if(lock) liberarBloqueoTurnos(c);}
        }
    }
    public void asignar(long empleadoId,long turnoId) throws SQLException {
        try(Connection c=Conexion.abrir()) {
            c.setAutoCommit(false);
            boolean lock=obtenerBloqueoTurnos(c);
            try {
                bloquearEmpleado(c,empleadoId);
                Turno turno=buscar(c,turnoId);
                if(turno==null || !turno.activo()) throw new IllegalArgumentException("El turno no existe o está inactivo");
                // Bloquear también el horario para sincronizarse con su edición.
                try(PreparedStatement ps=c.prepareStatement("SELECT id FROM Turno WHERE id=? FOR UPDATE")) {
                    ps.setLong(1,turnoId);try(ResultSet rs=ps.executeQuery()) {rs.next();}
                }
                // Releer el turno tras el bloqueo para no usar horarios obsoletos.
                turno=buscar(c,turnoId);
                List<Turno> existentes=asignados(c,empleadoId,-1);
                if(existentes.stream().anyMatch(t->t.id()==turnoId))throw new IllegalArgumentException("El empleado ya tiene asignado ese turno");
                validarNoSuperpone(turno,existentes);
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO EmpleadoTurno(empleado_id,turno_id) VALUES(?,?)")) {
                    ps.setLong(1,empleadoId);ps.setLong(2,turnoId);ps.executeUpdate();
                }
                c.commit();
            } catch(SQLException | RuntimeException e) {c.rollback();throw e;}
            finally {if(lock) liberarBloqueoTurnos(c);}
        }
    }
    public void quitarAsignacion(long empleadoId,long turnoId) throws SQLException {
        try(Connection c=Conexion.abrir();PreparedStatement ps=c.prepareStatement("DELETE FROM EmpleadoTurno WHERE empleado_id=? AND turno_id=?")) {
            ps.setLong(1,empleadoId);ps.setLong(2,turnoId);
            if(ps.executeUpdate()!=1)throw new IllegalArgumentException("El empleado no tiene asignado este turno");
        }
    }
    /** Serializa altas de asignaciones y ediciones compartidas entre varias terminales. */
    private static boolean obtenerBloqueoTurnos(Connection c) throws SQLException {
        try(Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT GET_LOCK('sigam_pos_edicion_turnos',10)")) {
            if(!rs.next() || rs.getInt(1)!=1)throw new SQLException("Otra operación de turnos está en curso. Reintentá.");
            return true;
        }
    }
    private static void liberarBloqueoTurnos(Connection c) throws SQLException {
        try(Statement st=c.createStatement()) {st.execute("SELECT RELEASE_LOCK('sigam_pos_edicion_turnos')");}
    }
    private static void bloquearEmpleado(Connection c,long empleadoId) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT id FROM Empleado WHERE id=? AND estado='ACTIVO' FOR UPDATE")) {
            ps.setLong(1,empleadoId);
            try(ResultSet rs=ps.executeQuery()) {if(!rs.next())throw new IllegalArgumentException("Empleado inexistente o inactivo");}
        }
    }
    private static void validarNoSuperpone(Turno candidato,List<Turno> existentes) {
        for(Turno anterior:existentes) {
            if(anterior.activo() && candidato.seSuperponeCon(anterior))
                throw new IllegalArgumentException("Se superpone con el turno '"+anterior.nombre()+"' (#"+anterior.id()+") del empleado");
        }
    }
    private static void cargarDatos(PreparedStatement ps,Turno t) throws SQLException {
        ps.setString(1,t.nombre());ps.setTime(2,Time.valueOf(t.inicio()));ps.setTime(3,Time.valueOf(t.fin()));
        int i=4;for(DiaSemana dia:DiaSemana.values())ps.setBoolean(i++,t.dias().contains(dia));
    }
    private static List<Turno> leerTodos(Connection c,String sql) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement(sql)) {return leerLista(ps);}
    }
    private static List<Turno> leerLista(PreparedStatement ps) throws SQLException {
        List<Turno> turnos=new ArrayList<>();
        try(ResultSet rs=ps.executeQuery()) {while(rs.next())turnos.add(leerTurno(rs));}
        return turnos;
    }
    private static Turno leerTurno(ResultSet r) throws SQLException {
        Set<DiaSemana> dias=EnumSet.noneOf(DiaSemana.class);
        for(DiaSemana dia:DiaSemana.values())if(r.getBoolean(dia.columna()))dias.add(dia);
        return new Turno(r.getLong("id"),r.getString("nombre"),r.getTime("hora_inicio").toLocalTime(),r.getTime("hora_fin").toLocalTime(),dias,"ACTIVO".equals(r.getString("estado")));
    }
}
