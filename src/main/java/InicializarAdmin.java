package ar.com.sigampos;
import ar.com.sigampos.servicio.PersonalServicio;
import java.io.Console;
public final class InicializarAdmin {
    public static void main(String[] args)throws Exception {
        Console c=System.console();if(c==null){System.err.println("Ejecutar desde una terminal interactiva");return;}
        String nombre=c.readLine("Nombre: "),dni=c.readLine("DNI: "),usuario=c.readLine("Usuario admin: ");
        char[] clave=c.readPassword("Contraseña (8+): ");
        try {long id=new PersonalServicio().inicializarAdministrador(nombre,dni,usuario,new String(clave));System.out.println("Administrador inicial creado. Empleado #"+id);}
        finally{java.util.Arrays.fill(clave,'\0');}
    }
}
