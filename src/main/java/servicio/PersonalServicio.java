package ar.com.sigampos.servicio;
import ar.com.sigampos.datos.Conexion;
import java.sql.*;
import java.time.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.*;

public final class PersonalServicio {
    public record Sesion(long empleadoId,String nombre,String rol) { public boolean administrador(){return "ADMINISTRADOR".equals(rol);} }
    public static String hash(String clave) {
        if (clave==null || clave.length()<8) throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres");
        return hashInterno(clave);
    }
    private static String hashInterno(String clave) {
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        try {return "pbkdf2$120000$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(derivar(clave,salt,120000));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static byte[] derivar(String clave,byte[] salt,int vueltas)throws Exception {
        PBEKeySpec spec=new PBEKeySpec(clave.toCharArray(),salt,vueltas,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        finally{spec.clearPassword();}
    }
    private static boolean verificar(String clave,String guardado){
        try {String[] partes=guardado.split("\\$");if(partes.length!=4 || !partes[0].equals("pbkdf2"))return false;
            byte[] salt=Base64.getDecoder().decode(partes[2]);byte[] correcto=Base64.getDecoder().decode(partes[3]);
            return MessageDigest.isEqual(correcto,derivar(clave,salt,Integer.parseInt(partes[1])));
        }catch(Exception e){return false;}
    }
    public long inicializarAdministrador(String nombre,String dni,String usuario,String clave)throws SQLException {
        // Inicialización controlada del primer usuario: nunca permite crear otro admin sin autenticarse.
        try(Connection c=Conexion.abrir();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM Usuario")){
            r.next();if(r.getLong(1)!=0)throw new IllegalStateException("El administrador inicial ya existe");
        }
        return crearEmpleadoYUsuario(nombre,dni,usuario,clave,"ADMINISTRADOR");
    }
    /** Alta inicial únicamente cuando la tabla Usuario está vacía. Credenciales temporales de demostración. */
    public boolean crearAdminInicialSiFalta() throws SQLException {
        try(Connection c=Conexion.abrir()) {
            c.setAutoCommit(false);
            try(Statement lock=c.createStatement()) {
                // Serializa el alta inicial entre instancias que apuntan al mismo servidor.
                try(ResultSet r=lock.executeQuery("SELECT GET_LOCK('sigam_pos_admin_inicial', 10)")) {
                    r.next(); if(r.getInt(1)!=1) throw new SQLException("No se pudo bloquear el alta inicial");
                }
                try {
                    try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT COUNT(*) FROM Usuario")) {
                        r.next(); if(r.getLong(1)>0) {c.commit();return false;}
                    }
                    long id;
                    try(PreparedStatement ps=c.prepareStatement("INSERT INTO Empleado(nombre,dni) VALUES('Administrador inicial','00000000')",Statement.RETURN_GENERATED_KEYS)) {
                        ps.executeUpdate();id=VentaServicio.idGenerado(ps);
                    }
                    try(PreparedStatement ps=c.prepareStatement("INSERT INTO Usuario(empleado_id,nombre_usuario,contrasena_hash,rol) VALUES(?,'Admin',?,'ADMINISTRADOR')")) {
                        ps.setLong(1,id);ps.setString(2,hashInterno("1234"));ps.executeUpdate();
                    }
                    c.commit();return true;
                } finally {
                    try(Statement unlock=c.createStatement()) {unlock.execute("SELECT RELEASE_LOCK('sigam_pos_admin_inicial')");}
                }
            } catch(SQLException | RuntimeException e){c.rollback();throw e;}
        }
    }
    /** Empleados: acceso con DNI; PIN temporal igual a sus cuatro últimas cifras. */
    public long crearEmpleadoConAccesoDni(String nombre,String dni) throws SQLException {
        String d=dni==null?"":dni.trim();
        if(!d.matches("[0-9]{7,8}"))throw new IllegalArgumentException("DNI inválido (7 u 8 dígitos)");
        if(nombre==null || nombre.isBlank())throw new IllegalArgumentException("Nombre obligatorio");
        String pin=d.substring(d.length()-4);
        return insertarPersonal(nombre,d,d,hashInterno(pin),"VENDEDOR");
    }
    public void cambiarCredenciales(long empleadoId,String nombreUsuario,String nuevaClave) throws SQLException {
        if(nombreUsuario==null || nombreUsuario.isBlank())throw new IllegalArgumentException("Usuario obligatorio");
        String nuevoHash=hash(nuevaClave);
        try(Connection c=Conexion.abrir();PreparedStatement ps=c.prepareStatement("UPDATE Usuario SET nombre_usuario=?,contrasena_hash=? WHERE empleado_id=?")){
            ps.setString(1,nombreUsuario.trim());ps.setString(2,nuevoHash);ps.setLong(3,empleadoId);
            if(ps.executeUpdate()!=1)throw new IllegalArgumentException("Cuenta inexistente");
        }
    }
    public Sesion autenticar(String usuario,String clave)throws SQLException {
        try(Connection c=Conexion.abrir();PreparedStatement ps=c.prepareStatement(
            "SELECT U.empleado_id,E.nombre,U.rol,U.contrasena_hash FROM Usuario U JOIN Empleado E ON E.id=U.empleado_id WHERE U.nombre_usuario=? AND E.estado='ACTIVO'")){
            ps.setString(1,usuario);try(ResultSet rs=ps.executeQuery()){
                if(!rs.next() || !verificar(clave,rs.getString("contrasena_hash")))throw new IllegalArgumentException("Credenciales inválidas");
                return new Sesion(rs.getLong("empleado_id"),rs.getString("nombre"),rs.getString("rol"));
            }
        }
    }
    public long crearEmpleadoYUsuario(String nombre,String dni,String usuario,String clave,String rol)throws SQLException {
        if(!List.of("ADMINISTRADOR","VENDEDOR").contains(rol))throw new IllegalArgumentException("Rol inválido");
        if(nombre.isBlank() || dni.isBlank() || usuario.isBlank())throw new IllegalArgumentException("Datos incompletos");
        String secreto=hash(clave);
        return insertarPersonal(nombre,dni,usuario,secreto,rol);
    }
    private long insertarPersonal(String nombre,String dni,String usuario,String secreto,String rol)throws SQLException {
        try(Connection c=Conexion.abrir()){
            c.setAutoCommit(false);
            try{
                long id;
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO Empleado(nombre,dni) VALUES(?,?)",Statement.RETURN_GENERATED_KEYS)){
                    ps.setString(1,nombre.trim());ps.setString(2,dni.trim());ps.executeUpdate();id=VentaServicio.idGenerado(ps);
                }
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO Usuario(empleado_id,nombre_usuario,contrasena_hash,rol) VALUES(?,?,?,?)")){
                    ps.setLong(1,id);ps.setString(2,usuario.trim());ps.setString(3,secreto);ps.setString(4,rol);ps.executeUpdate();
                }
                c.commit();return id;
            }catch(SQLException | RuntimeException e){c.rollback();throw e;}
        }
    }
    public void desactivar(long empleadoId)throws SQLException {
        try(Connection c=Conexion.abrir();PreparedStatement ps=c.prepareStatement("UPDATE Empleado SET estado='INACTIVO' WHERE id=?")){
            ps.setLong(1,empleadoId);if(ps.executeUpdate()!=1)throw new IllegalArgumentException("Empleado inexistente");
        }
    }
}
