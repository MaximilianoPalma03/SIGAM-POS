# SIGAM-POS — Sistema de Gestión Comercial

**Versión académica 1.0.0 (RC1 validada en pruebas de instalación)** · Aplicación de escritorio para Windows · Java Swing + MySQL

SIGAM-POS es un sistema de punto de venta y gestión comercial desarrollado para el trabajo práctico de Seminario de Práctica de Informática (AP3/TP3), como continuación del análisis y diseño de AP1/AP2.

## Descargar e instalar

1. Ir a **Releases** de este repositorio y descargar `SIGAM-POS-Setup.exe` de la versión publicada.
2. Ejecutar el instalador en **Windows 10/11 de 64 bits** y completar los pasos indicados.
3. Abrir **SIGAM-POS** desde el acceso directo.
4. En la primera ejecución, elegir según corresponda:
   - **Usar base existente:** conectarse a una base `sigam_pos` compatible, utilizando el servidor, puerto y credenciales MySQL vigentes. La aplicación comprueba el esquema sin recrear las tablas.
   - **Crear instalación local nueva:** inicializar MySQL incluido y crear las tablas necesarias en una nueva instalación. Elegir esta opción únicamente si nunca hubo una base local de SIGAM-POS en ese equipo.
5. Seguir las indicaciones para configurar la cuenta administrativa inicial cuando corresponda.

**Para instalar y utilizar el programa no es necesario instalar Java, Maven, MySQL Server ni MySQL Workbench por separado**: el instalador incorpora Java y una instancia local de MySQL. Windows puede solicitar autorización para instalar componentes.

> **Conservación de datos:** la desinstalación no elimina intencionalmente la carpeta de datos y configuración de SIGAM-POS ubicada en `%LOCALAPPDATA%\SIGAM-POS`. Al reinstalar, la aplicación intenta reutilizar la configuración anterior. Se recomienda mantener respaldos independientes antes de actualizar, reinstalar o intervenir en el sistema.

## Funcionalidades

- Inicio de sesión, usuarios, empleados, roles y turnos.
- Gestión de productos: altas, cambios de precios y costos, inventario, estados y alertas de stock.
- Ventas con carrito, distintos medios de pago y pagos mixtos; registro de operaciones y actualización de existencias.
- Gestión de caja física y digital, movimientos, jornadas, arqueos y cierres.
- Historial y análisis comercial por períodos: ventas, medios de pago, márgenes con costos históricos disponibles, inventario y desempeño.
- Exportación de informes CSV de inventario, ventas, jornadas y movimientos.
- Respaldo de MySQL desde la aplicación mediante `mysqldump`, con detección de la herramienta incluida.
- Asistente de instalación no destructivo: verifica la compatibilidad antes de utilizar una base existente.

## Seguridad de los datos y reinstalación

- **No seleccionar “Crear instalación local nueva”** si existen datos previos que se deben conservar.
- Ante una base existente incompatible, SIGAM-POS informa el problema sin eliminar las tablas.
- La instancia MySQL incluida utiliza almacenamiento persistente fuera de la carpeta de instalación.
- Un respaldo SQL puede contener datos personales y credenciales derivadas: no debe agregarse al repositorio ni compartirse públicamente.
- El desinstalador elimina los componentes de la aplicación, **no** los datos persistentes del comercio. El usuario es responsable de conservar respaldos.

## Compilar desde el código fuente (solo desarrollo)

Requisitos en el equipo desarrollador: **JDK 17 o superior**, Maven, Windows x64, Inno Setup 6, y el ZIP oficial de **MySQL Community Server 9.7.1 LTS** para Windows x64. Se requiere conexión inicial para descargar dependencias de compilación y el Visual C++ Redistributable oficial, salvo que este último se proporcione localmente.

```powershell
mvn clean test-compile
```

Para ejecutar desde el código fuente se debe configurar la conexión MySQL correspondiente al entorno de desarrollo, por ejemplo mediante `SIGAM_DB_URL`, `SIGAM_DB_USER` y `SIGAM_DB_PASSWORD` en la terminal actual. No guardar contraseñas en Git.

Para construir el instalador en PowerShell (reemplazar la ruta del ZIP por la real):

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\distribucion\Crear-SIGAM-POS-Windows.ps1 -MySqlZip "C:\Descargas\mysql-9.7.1-winx64.zip"
```

Si la construcción finaliza correctamente, el instalador se encontrará en `dist\SIGAM-POS-Setup.exe`. El constructor usa `jpackage` para incorporar el runtime Java e **Inno Setup** para crear el instalador y registrar la desinstalación.

El código fuente conserva los scripts SQL en `sql/`. La compilación incorpora al classpath únicamente el recurso de inicialización necesario para instalaciones nuevas.

## Pruebas realizadas

Se verificó la instalación del ejecutable en un equipo con una base MySQL existente, la detección de compatibilidad, la conservación de registros después de desinstalar y reinstalar, y la primera instalación con MySQL incluido en otro equipo sin instalación previa de SIGAM-POS. En esta última se comprobó la persistencia de un producto después de cerrar y volver a abrir. El plan de pruebas y las comprobaciones adicionales están en `distribucion/PLAN-PRUEBAS-WINDOWS-v1.0.0.md`.

La restauración de un respaldo SQL se verificó previamente en una base de pruebas separada; nunca debe probarse una restauración sobre la base de producción sin un procedimiento controlado.

## Alcance

**Proyecto académico**. SIGAM-POS no emite comprobantes fiscales electrónicos ni debe presentarse como solución certificada de facturación o sistema auditado para producción comercial. Algunas funciones de versiones futuras (como servicios técnicos, señas o integración fiscal) quedan fuera de este alcance.

El instalador de Windows se publica como **archivo adjunto en GitHub Releases**, no dentro del árbol de código fuente.
