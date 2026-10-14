package ar.com.sigampos.servicio;

import ar.com.sigampos.datos.Conexion;
import ar.com.sigampos.modelo.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.DayOfWeek;
import java.time.LocalDate;

/** Cada caja abierta pertenece a una jornada REAL del empleado, no al patrón semanal. */
public final class CajaServicio {
    public record Resumen(BigDecimal fondoInicial, BigDecimal ventasEfectivo,
                          BigDecimal entradas, BigDecimal salidas, BigDecimal esperado) { }

    public long abrir(long turnoId, long empleadoId, BigDecimal fondo) throws SQLException {
        if(fondo==null || fondo.signum()<0 || fondo.scale()>2)throw new IllegalArgumentException("Fondo inicial inválido");
        try(Connection c=Conexion.abrir()) {
            c.setAutoCommit(false);
            boolean bloqueo=false;
            try {
                // GET_LOCK serializa aperturas en el mismo servidor incluso si aún no hay Caja abierta.
                try(PreparedStatement ps=c.prepareStatement("SELECT GET_LOCK('sigam_pos_apertura_caja', 10)")) {
                    try(ResultSet rs=ps.executeQuery()) {
                        rs.next(); if(rs.getInt(1)!=1)throw new SQLException("No se pudo reservar la apertura de caja");
                        bloqueo=true;
                    }
                }
                String sql="SELECT T.hora_inicio,T.hora_fin,T.lunes,T.martes,T.miercoles,T.jueves,T.viernes,T.sabado,T.domingo " +
                    "FROM Turno T JOIN EmpleadoTurno ET ON ET.turno_id=T.id " +
                    "JOIN Empleado E ON E.id=ET.empleado_id " +
                    "WHERE T.id=? AND ET.empleado_id=? AND T.estado='ACTIVO' AND E.estado='ACTIVO' FOR UPDATE";
                Time inicio,fin;
                try(PreparedStatement ps=c.prepareStatement(sql)) {
                    ps.setLong(1,turnoId);ps.setLong(2,empleadoId);
                    try(ResultSet rs=ps.executeQuery()) {
                        if(!rs.next())throw new IllegalArgumentException("Turno no asignado a este empleado, inactivo o inexistente");
                        DayOfWeek hoy=LocalDate.now().getDayOfWeek();
                        String columna=DiaSemana.desde(hoy).columna();
                        if(!rs.getBoolean(columna)) throw new IllegalArgumentException("Este turno no está asignado al día de hoy");
                        inicio=rs.getTime("hora_inicio");fin=rs.getTime("hora_fin");
                    }
                }
                try(PreparedStatement ps=c.prepareStatement("SELECT id FROM Caja WHERE estado='ABIERTA' LIMIT 1 FOR UPDATE");ResultSet rs=ps.executeQuery()) {
                    if(rs.next())throw new IllegalStateException("Ya hay una caja abierta: cerrala antes de abrir otra");
                }
                long jornada;
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO JornadaLaboral(empleado_id,turno_id,fecha_programada,hora_inicio_programada,hora_fin_programada,inicio_real) " +
                    "VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1,empleadoId);ps.setLong(2,turnoId);
                    ps.setDate(3,Date.valueOf(LocalDate.now()));ps.setTime(4,inicio);ps.setTime(5,fin);
                    ps.executeUpdate();jornada=VentaServicio.idGenerado(ps);
                }
                long cajaId;
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO Caja(turno_id,jornada_id,fondo_inicial) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1,turnoId);ps.setLong(2,jornada);ps.setBigDecimal(3,fondo);
                    ps.executeUpdate();cajaId=VentaServicio.idGenerado(ps);
                }
                c.commit();return cajaId;
            } catch(SQLException | RuntimeException e) {c.rollback();throw e;}
            finally {
                if(bloqueo) try(Statement s=c.createStatement()) {s.execute("SELECT RELEASE_LOCK('sigam_pos_apertura_caja')");}
            }
        }
    }
    /** Al iniciar otra sesión, permite recuperar la caja ABIERTA de la jornada propia. */
    public Long cajaAbiertaDe(long empleadoId) throws SQLException {
        try(Connection c=Conexion.abrir();PreparedStatement ps=c.prepareStatement(
                "SELECT C.id FROM Caja C JOIN JornadaLaboral J ON J.id=C.jornada_id " +
                "WHERE J.empleado_id=? AND C.estado='ABIERTA' AND J.estado='ABIERTA' ORDER BY C.id DESC LIMIT 1")) {
            ps.setLong(1,empleadoId);
            try(ResultSet r=ps.executeQuery()) {return r.next()?r.getLong(1):null;}
        }
    }
    public void registrar(long cajaId,long empleadoId,MovimientoCaja movimiento) throws SQLException {
        if(movimiento==null)throw new IllegalArgumentException("Falta el movimiento");
        if(movimiento.getMotivo().isBlank())throw new IllegalArgumentException("El motivo es obligatorio");
        if(movimiento.getMotivo().length()>255)throw new IllegalArgumentException("Motivo demasiado largo");
        if(movimiento instanceof IngresoCaja ingreso && ingreso.tipo().equals("INGRESO_VENTA"))
            throw new IllegalArgumentException("El efectivo por venta solo puede registrarlo VentaServicio");
        try(Connection c=Conexion.abrir()) {
            c.setAutoCommit(false);
            try {
                VentaServicio.comprobarCaja(c,cajaId,empleadoId);
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO MovimientoCaja(caja_id,empleado_id,tipo,monto,motivo) VALUES(?,?,?,?,?)")) {
                    ps.setLong(1,cajaId);ps.setLong(2,empleadoId);ps.setString(3,movimiento.tipo());
                    ps.setBigDecimal(4,movimiento.getMonto());ps.setString(5,movimiento.getMotivo());ps.executeUpdate();
                }
                c.commit();
            }catch(SQLException | RuntimeException e){c.rollback();throw e;}
        }
    }
    /** Historial de caja ordenado para verificar operaciones y corregir errores de carga. */
    public String historial(long cajaId) throws SQLException {
        String sql="SELECT M.fecha_hora,M.tipo,M.monto,M.motivo,E.nombre FROM MovimientoCaja M " +
            "JOIN Empleado E ON E.id=M.empleado_id WHERE M.caja_id=? ORDER BY M.id";
        StringBuilder sb=new StringBuilder("Movimientos de caja #"+cajaId+"\n\n");
        try(Connection c=Conexion.abrir();PreparedStatement ps=c.prepareStatement(sql)) {
            ps.setLong(1,cajaId);
            try(ResultSet r=ps.executeQuery()){
                while(r.next())sb.append(r.getTimestamp(1)).append(" | ").append(r.getString(2)).append(" | ")
                    .append(ar.com.sigampos.util.Moneda.formato(r.getBigDecimal(3))).append(" | ")
                    .append(r.getString(4)).append(" | ").append(r.getString(5)).append('\n');
            }
        }
        return sb.toString();
    }
    public Resumen resumen(long cajaId) throws SQLException {
        try(Connection c=Conexion.abrir()) {return resumen(c,cajaId);}
    }
    private Resumen resumen(Connection c,long cajaId) throws SQLException {
        String sql="SELECT C.fondo_inicial, " +
            "COALESCE(SUM(CASE WHEN M.tipo='INGRESO_VENTA' THEN M.monto ELSE 0 END),0), " +
            "COALESCE(SUM(CASE WHEN M.tipo='ENTRADA' THEN M.monto ELSE 0 END),0), " +
            "COALESCE(SUM(CASE WHEN M.tipo='SALIDA' THEN M.monto ELSE 0 END),0) " +
            "FROM Caja C LEFT JOIN MovimientoCaja M ON M.caja_id=C.id WHERE C.id=? GROUP BY C.id,C.fondo_inicial";
        try(PreparedStatement ps=c.prepareStatement(sql)) {
            ps.setLong(1,cajaId);
            try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next())throw new IllegalArgumentException("Caja inexistente");
                BigDecimal fondo=rs.getBigDecimal(1),ventas=rs.getBigDecimal(2),entradas=rs.getBigDecimal(3),salidas=rs.getBigDecimal(4);
                return new Resumen(fondo,ventas,entradas,salidas,fondo.add(ventas).add(entradas).subtract(salidas));
            }
        }
    }
    public BigDecimal esperado(long cajaId) throws SQLException {return resumen(cajaId).esperado();}
    public BigDecimal cerrar(long cajaId,long empleadoId,BigDecimal contado,BigDecimal proximo) throws SQLException {
        return cerrar(cajaId,empleadoId,contado,proximo,null);
    }
    public BigDecimal cerrar(long cajaId,long empleadoId,BigDecimal contado,BigDecimal proximo,String observaciones) throws SQLException {
        if(contado==null || proximo==null || contado.signum()<0 || proximo.signum()<0 || contado.scale()>2 || proximo.scale()>2)
            throw new IllegalArgumentException("Los importes del cierre deben ser positivos y con máximo dos decimales");
        if(proximo.compareTo(contado)>0)
            throw new IllegalArgumentException("El fondo entregado al siguiente turno no puede superar el efectivo contado");
        if(observaciones!=null && observaciones.length()>500)throw new IllegalArgumentException("Observación demasiado extensa (máximo 500 caracteres)");
        try(Connection c=Conexion.abrir()) {
            c.setAutoCommit(false);
            try {
                VentaServicio.comprobarCaja(c,cajaId,empleadoId);
                Resumen r=resumen(c,cajaId);
                BigDecimal diferencia=contado.subtract(r.esperado());
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO ArqueoCaja(caja_id,empleado_id,efectivo_esperado,efectivo_contado,diferencia,fondo_proximo_turno,observaciones) VALUES(?,?,?,?,?,?,?)")) {
                    ps.setLong(1,cajaId);ps.setLong(2,empleadoId);ps.setBigDecimal(3,r.esperado());ps.setBigDecimal(4,contado);
                    ps.setBigDecimal(5,diferencia);ps.setBigDecimal(6,proximo);ps.setString(7,observaciones);ps.executeUpdate();
                }
                try(PreparedStatement ps=c.prepareStatement("UPDATE Caja SET estado='CERRADA' WHERE id=? AND estado='ABIERTA'")) {
                    ps.setLong(1,cajaId);if(ps.executeUpdate()!=1)throw new SQLException("Caja ya cerrada");
                }
                try(PreparedStatement ps=c.prepareStatement("UPDATE JornadaLaboral J JOIN Caja C ON C.jornada_id=J.id " +
                        "SET J.estado='CERRADA',J.fin_real=NOW() WHERE C.id=? AND J.empleado_id=? AND J.estado='ABIERTA'")) {
                    ps.setLong(1,cajaId);ps.setLong(2,empleadoId);
                    if(ps.executeUpdate()!=1)throw new SQLException("No se pudo finalizar la jornada laboral");
                }
                c.commit();return diferencia;
            }catch(SQLException | RuntimeException e){c.rollback();throw e;}
        }
    }
}
