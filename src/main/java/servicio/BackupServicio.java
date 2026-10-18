package ar.com.sigampos.servicio;

import ar.com.sigampos.datos.Conexion;
import ar.com.sigampos.datos.ConfiguracionBD;
import ar.com.sigampos.datos.MysqlLocal;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Respaldos completos de MySQL mediante mysqldump, sin contraseñas en argumentos de procesos. */
public final class BackupServicio {
    private static final String CLAVE_CARPETA="backup.carpeta";
    private static final String CLAVE_AUTOMATICO="backup.auto";
    private static final String CLAVE_ULTIMO="backup.ultimo";
    private static final String CLAVE_EJECUTABLE="backup.mysqldump";
    private static final DateTimeFormatter NOMBRE=DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private final Preferences preferencias;

    public BackupServicio(){this(Preferences.userNodeForPackage(BackupServicio.class));}
    public BackupServicio(Preferences preferencias){this.preferencias=Objects.requireNonNull(preferencias);}

    public Path carpeta(){
        String porDefecto=Path.of(System.getProperty("user.home"),"Documents","SIGAM-POS","Backups").toString();
        return Path.of(preferencias.get(CLAVE_CARPETA,porDefecto));
    }
    public boolean automatico(){return preferencias.getBoolean(CLAVE_AUTOMATICO,false);}

    /** Ruta opcional elegida por el administrador, útil en instalaciones no habituales. */
    public String ejecutableConfigurado(){return preferencias.get(CLAVE_EJECUTABLE,"");}

    public void configurarEjecutable(String ruta){
        String elegido=ruta==null?"":ruta.trim();
        if(elegido.isEmpty())preferencias.remove(CLAVE_EJECUTABLE);
        else {
            Path archivo=Path.of(elegido).toAbsolutePath().normalize();
            if(!Files.isRegularFile(archivo))
                throw new IllegalArgumentException("No se encontró mysqldump en: "+archivo);
            preferencias.put(CLAVE_EJECUTABLE,archivo.toString());
        }
        guardarPreferencias();
    }

    /** Busca la herramienta sin necesidad de instalarla en el PATH. */
    public Path localizarEjecutable(){
        String entorno=System.getenv("SIGAM_MYSQLDUMP");
        if(entorno!=null && !entorno.isBlank()){
            Path archivo=Path.of(entorno.trim()).toAbsolutePath().normalize();
            return Files.isRegularFile(archivo)?archivo:null;
        }
        String configurado=ejecutableConfigurado();
        if(!configurado.isBlank()){
            Path archivo=Path.of(configurado).toAbsolutePath().normalize();
            return Files.isRegularFile(archivo)?archivo:null;
        }
        Path incluido=MysqlLocal.clienteDump();
        if(Files.isRegularFile(incluido))return incluido;
        return localizarEnCarpetas(carpetasCandidatas());
    }

    /** Separado para poder probar la detección sin depender de Windows ni MySQL instalado. */
    public static Path localizarEnCarpetas(List<Path> directorios){
        String nombre=System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win")
            ?"mysqldump.exe":"mysqldump";
        for(Path carpeta:directorios){
            if(carpeta==null)continue;
            Path encontrado=carpeta.resolve(nombre);
            if(Files.isRegularFile(encontrado))return encontrado.toAbsolutePath().normalize();
            // También admite el nombre Windows en entornos de prueba de otras plataformas.
            if(!nombre.endsWith(".exe") && Files.isRegularFile(carpeta.resolve("mysqldump.exe")))
                return carpeta.resolve("mysqldump.exe").toAbsolutePath().normalize();
        }
        return null;
    }

    private static List<Path> carpetasCandidatas(){
        List<Path> directorios=new ArrayList<>();
        String path=System.getenv("PATH");
        if(path!=null)for(String parte:path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))){
            if(!parte.isBlank())try{directorios.add(Path.of(parte));}catch(Exception ignored){}
        }
        String mysqlHome=System.getenv("MYSQL_HOME");
        if(mysqlHome!=null && !mysqlHome.isBlank())directorios.add(Path.of(mysqlHome,"bin"));
        for(String variable:List.of("ProgramFiles","ProgramFiles(x86)")){
            String raiz=System.getenv(variable);
            if(raiz==null || raiz.isBlank())continue;
            Path instalada=Path.of(raiz);
            agregarBinDeSubdirectorios(instalada.resolve("MySQL"),"MySQL Server",directorios);
            agregarBinDeSubdirectorios(instalada,"MariaDB",directorios);
        }
        String drive=System.getenv().getOrDefault("SystemDrive","C:");
        directorios.add(Path.of(drive+"\\xampp\\mysql\\bin"));
        agregarBinDeSubdirectorios(Path.of(drive+"\\wamp64\\bin\\mysql"),"mysql",directorios);
        agregarBinDeSubdirectorios(Path.of(drive+"\\laragon\\bin\\mysql"),"mysql",directorios);
        return directorios;
    }

    private static void agregarBinDeSubdirectorios(Path carpeta,String prefijo,List<Path> salida){
        if(!Files.isDirectory(carpeta))return;
        try(DirectoryStream<Path> entradas=Files.newDirectoryStream(carpeta)){
            for(Path sub:entradas){
                if(Files.isDirectory(sub) && sub.getFileName().toString().startsWith(prefijo))
                    salida.add(sub.resolve("bin"));
            }
        }catch(IOException | SecurityException ignored){ /* Ruta opcional: continuar buscando */ }
    }

    public Instant ultimo(){long epoch=preferencias.getLong(CLAVE_ULTIMO,0);return epoch==0?null:Instant.ofEpochMilli(epoch);}
    public boolean tocaAutomatico(){return automatico() && (ultimo()==null || ultimo().isBefore(Instant.now().minusSeconds(86400)));}

    public void configurar(Path carpeta,boolean automatico){
        if(carpeta==null || carpeta.toString().isBlank())throw new IllegalArgumentException("Elegí una carpeta de respaldo");
        preferencias.put(CLAVE_CARPETA,carpeta.toAbsolutePath().normalize().toString());
        preferencias.putBoolean(CLAVE_AUTOMATICO,automatico);
        guardarPreferencias();
    }

    public Path crear() throws IOException,InterruptedException {
        ConfiguracionBD.Credenciales config=Conexion.parametros();
        if(config==null)throw new IOException("No hay conexión MySQL configurada");
        String url=config.url();
        String usuario=config.usuario();
        String clave=config.clave();
        URI uri;
        try{uri=URI.create(url.substring("jdbc:".length()));}
        catch(Exception e){throw new IOException("URL MySQL incorrecta",e);}
        if(!"mysql".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null)
            throw new IOException("El respaldo requiere una conexión MySQL TCP/IP válida");
        String bd=uri.getPath().replaceFirst("^/","");
        if(!bd.matches("[a-zA-Z0-9_]+"))throw new IOException("Nombre de base de datos no admitido");
        Path carpeta=carpeta().toAbsolutePath().normalize();
        Files.createDirectories(carpeta);
        String nombre="SIGAM-POS_"+NOMBRE.format(LocalDateTime.now())+"_"+
            Long.toUnsignedString(System.nanoTime(),36)+".sql";
        Path destino=carpeta.resolve(nombre);
        Path temporal=Files.createTempFile(carpeta,".sigam-respaldo-",".tmp");
        Path errores=Files.createTempFile(carpeta,".sigam-error-",".tmp");
        try {
            Path ejecutable=localizarEjecutable();
            if(ejecutable==null)throw new IOException("No se encontró mysqldump. " +
                "Instalá MySQL Server o seleccioná mysqldump.exe en Configuraciones → Copias de seguridad.");
            ProcessBuilder proceso=new ProcessBuilder(ejecutable.toString(),
                "--host="+uri.getHost(),"--port="+(uri.getPort()>0?uri.getPort():3306),
                "--user="+usuario,"--default-character-set=utf8mb4",
                "--single-transaction","--quick","--triggers","--no-tablespaces",
                bd); // Sin --databases: el SQL puede restaurarse en sigam_pos_prueba sin redirigir a producción.
            // La clave NO forma parte de la línea de comandos ni se escribe en el reporte.
            proceso.environment().put("MYSQL_PWD",clave);
            proceso.redirectOutput(temporal.toFile());
            proceso.redirectError(errores.toFile());
            Process hijo;
            try { hijo=proceso.start(); }
            catch(IOException e){throw new IOException("No se pudo ejecutar mysqldump: "+ejecutable,e);}
            if(!hijo.waitFor(180,TimeUnit.SECONDS)){
                hijo.destroyForcibly();
                throw new IOException("El respaldo superó el tiempo de espera de 3 minutos");
            }
            if(hijo.exitValue()!=0){
                String detalle=Files.readString(errores);
                throw new IOException("Falló mysqldump (código "+hijo.exitValue()+"): "+
                    detalle.substring(0,Math.min(400,detalle.length())));
            }
            if(Files.size(temporal)<500)
                throw new IOException("El archivo de respaldo parece incompleto o vacío");
            try(var contenido=Files.lines(temporal)){
                if(contenido.noneMatch(linea->linea.contains("CREATE TABLE")))
                    throw new IOException("No se encontraron estructuras de tablas en el respaldo");
            }
            try{Files.move(temporal,destino,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporal,destino);}
            preferencias.putLong(CLAVE_ULTIMO,Instant.now().toEpochMilli());
            guardarPreferencias();
            return destino;
        } finally {
            Files.deleteIfExists(temporal);
            Files.deleteIfExists(errores);
        }
    }

    private void guardarPreferencias(){
        try{preferencias.flush();}
        catch(BackingStoreException e){throw new IllegalStateException("No se guardaron las preferencias de respaldo",e);}
    }
}
