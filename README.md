# SIGAM-POS — Sistema de Gestión Comercial

**Versión TP 1.0.0 ** · Aplicación de escritorio para Windows · Java Swing + MySQL

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

 **Conservación de datos:** la desinstalación no elimina intencionalmente la carpeta de datos y configuración de SIGAM-POS ubicada en `%LOCALAPPDATA%\SIGAM-POS`. Al reinstalar, la aplicación intenta reutilizar la configuración anterior. Se recomienda mantener respaldos independientes antes de actualizar, reinstalar o intervenir en el sistema.

## Funcionalidades

- Inicio de sesión, usuarios, empleados, roles y turnos.
- Gestión de productos: altas, cambios de precios y costos, inventario, estados y alertas de stock.
- Ventas con carrito, distintos medios de pago y pagos mixtos; registro de operaciones y actualización de existencias.
- Gestión de caja física y digital, movimientos, jornadas, arqueos y cierres.
- Historial y análisis comercial por períodos: ventas, medios de pago, márgenes con costos históricos disponibles, inventario y desempeño.
- Exportación de informes CSV de inventario, ventas, jornadas y movimientos.
- Respaldo de MySQL desde la aplicación mediante `mysqldump`, con detección de la herramienta incluida.
- Asistente de instalación no destructivo: verifica la compatibilidad antes de utilizar una base existente.


## Alcance

**Proyecto académico**. SIGAM-POS no emite comprobantes fiscales electrónicos ni debe presentarse como solución certificada de facturación o sistema auditado para producción comercial. Algunas funciones de versiones futuras (como servicios técnicos, señas o integración fiscal) quedan fuera de este alcance.

El instalador de Windows se publica como **archivo adjunto en GitHub Releases**, no dentro del árbol de código fuente.
