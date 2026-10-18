# Plan de aceptación del instalador SIGAM-POS v1.0.0 RC1

El objetivo es verificar el **instalador real** y su desinstalación, sin consumir
la primera instalación de la PC de destino. Este plan no implica que las pruebas
nativas de Windows ya estén aprobadas.

## 1. Compilación en la PC de desarrollo

- Generar `dist/SIGAM-POS-Setup.exe` con `Crear-SIGAM-POS-Windows.ps1`.
- Confirmar que el constructor ejecutó 17 pruebas Java sin errores.
- Verificar que el archivo final es no vacío, presenta un SHA256 y que el
  instalador de Windows contiene `SIGAM-POS.exe`, Java y MySQL.
- **No copiar el `SIGAM-POS.exe` aislado**: su runtime y MySQL están dentro del
  instalador.

## 2. PC existente — base original 3306

1. Guardar un backup SQL de la base real y comprobar su ubicación.
2. Cerrar la aplicación; instalar `SIGAM-POS-Setup.exe`.
3. Iniciar desde el acceso directo, sin PowerShell, Maven ni Workbench.
4. Si no existe configuración persistida, elegir **Usar base existente**,
   servidor 127.0.0.1, puerto 3306 y las credenciales válidas.
5. Confirmar que los registros (productos, ventas, pagos, cajas y jornadas) son
   idénticos a los anteriores; ejecutar consultas `COUNT(*)` si se desea.
6. Cerrar y volver a abrir, comprobar que no se vuelve a pedir la conexión.
7. Cerrar la aplicación y DESINSTALAR SIGAM-POS desde Configuración de Windows.
8. Verificar que `%LOCALAPPDATA%\SIGAM-POS` continúa existiendo si corresponde.
9. Reinstalar con el mismo Setup.exe. Debe conservar la configuración y los datos.
10. La base original `sigam_pos` en 3306 NO se debe recrear ni alterar.

## 3. Instalación limpia — primero en una máquina virtual / Windows Sandbox

Una VM limpia con instantánea es preferible. Windows Sandbox requiere ediciones
compatibles de Windows y no existe en todas las instalaciones Home.

1. Crear snapshot inicial. Comprobar que no existen datos SIGAM-POS.
2. Instalar el Setup REAL sin Java, Maven, MySQL ni Workbench preinstalados.
3. Abrir `SIGAM-POS.exe` desde el menú Inicio. Debe detectar MySQL incluido.
4. Seleccionar **Crear instalación local nueva**. Esperar inicialización del
   servidor aislado `127.0.0.1:33307` y creación de 18 tablas (17 operativas más
   control de esquema). No debe modificar un MySQL existente en 3306.
5. Iniciar con administrador inicial y actualizar inmediatamente la clave.
6. Crear un producto de prueba, una jornada y una venta, comprobar el registro.
7. Salir, volver a abrir y comprobar que siguen los datos.
8. Crear un respaldo desde Configuraciones sin buscar `mysqldump` manualmente.
9. Desinstalar desde Windows y comprobar que persisten los datos en
   `%LOCALAPPDATA%\SIGAM-POS`.
10. Reinstalar el mismo Setup. Debe usar la instalación local anterior SIN volver
    a inicializar tablas, y permitir ingresar con la contraseña nueva.
11. Guardar capturas y resultados. Restaurar snapshot para repetir si falla.

## 4. Prueba de protección de datos

- Si existen datos pero la configuración no está disponible, SIGAM-POS debe
  bloquear la instalación nueva y explicar el problema; **nunca borrar datos**.
- Si una base existente no cumple la estructura mínima, debe informar
  incompatibilidad y no ejecutar cambios en ella.
- El desinstalador no debe incluir acciones de borrado de bases ni backups.

## 5. PC definitiva de destino: solo después de pasar todo lo anterior

Esta prueba se realiza UNA SOLA VEZ sobre esa PC inicialmente sin SIGAM-POS:

1. Descargar el `SIGAM-POS-Setup.exe` de una GitHub Release creada tras las
   pruebas de la versión candidata, no del código fuente ni del ZIP parche.
2. Instalar, abrir, seleccionar instalación nueva y crear la contraseña inicial.
3. Registrar pruebas de productos, caja, ventas, exportación y backup.
4. Cerrar, desinstalar, reinstalar y confirmar persistencia de datos.
5. Documentar resultados para AP3 y la presentación académica.

### Criterio de aprobación

No se declara v1.0.0 estable sin validar efectivamente Setup.exe, el inicio
sin Maven, MySQL local de Windows, desinstalación y reinstalación conservando
el almacenamiento. Una compilación Java exitosa es necesaria pero insuficiente.
