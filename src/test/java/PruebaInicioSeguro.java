package ar.com.sigampos;

import ar.com.sigampos.datos.*;
import java.util.*;
import java.nio.file.*;

/** Pruebas sin MySQL de compatibilidad y del esquema para una instalación nueva. */
public final class PruebaInicioSeguro {
    public static void main(String[] args) throws Exception {
        if (ValidadorEsquema.cantidadTablas()!=17) throw new AssertionError("Se esperaban 17 tablas de negocio");
        Map<String,Set<String>> vacio=Map.of();
        if (ValidadorEsquema.diferencias(vacio).size()!=17)
            throw new AssertionError("Una BD vacía no debe reconocerse como compatible");
        if (ValidadorEsquema.diferencias(Map.of("usuario",Set.of("nombre_usuario"))).size()<17)
            throw new AssertionError("Esquema parcial reconocido por error");
        var instrucciones=EsquemaInicial.instrucciones();
        long tablas=instrucciones.stream().filter(s->s.toUpperCase(Locale.ROOT).startsWith("CREATE TABLE")).count();
        if (tablas!=18) throw new AssertionError("Esquema de instalación incompleto: "+tablas);
        if (instrucciones.stream().anyMatch(s->s.toUpperCase(Locale.ROOT).contains("DROP TABLE")))
            throw new AssertionError("La instalación incluye un DROP TABLE destructivo");
        if (instrucciones.stream().anyMatch(s->s.toUpperCase(Locale.ROOT).contains("CREATE DATABASE")))
            throw new AssertionError("El recurso no debe recrear la base por sí solo");
        if (instrucciones.stream().anyMatch(s->s.contains("QA-MOUSE01") || s.contains("Administrador inicial")))
            throw new AssertionError("Datos de usuarios reales o de pruebas no deben incluirse en la instalación");
        if (!MysqlLocal.jdbc().contains("33307"))throw new AssertionError("Se requiere un puerto independiente");
        if (!ConfiguracionBD.archivo().startsWith(ConfiguracionBD.datosAplicacion()))
            throw new AssertionError("La configuración debe guardarse fuera del programa");
        System.out.println("PruebaInicioSeguro OK: validación y creación no destructiva");
    }
}
