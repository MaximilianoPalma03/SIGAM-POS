# SIGAM-POS — AP3 / TP3 — primera entrega

Prototipo de escritorio Java (Swing) y MySQL de SIGAM-POS, continuando AP1/AP2. Inspiración en la organización visual del POS de referencia, sin copiar recursos ni depender de su código. **No es el sistema final ni está certificado para uso comercial.**

## Requisitos
- JDK **17 o superior** (se compiló en este entorno con Java 21).
- MySQL 8.0.16 o superior para que se apliquen los `CHECK`.
- Maven 3.9+ y conectividad inicial para obtener MySQL Connector/J.

## Preparación de MySQL

1. Ejecutar `sql/01_esquema.sql` en MySQL (por ejemplo mediante MySQL Workbench).
2. Crear un usuario de aplicación, no usar `root`. Ejemplo de comandos administrativos para un entorno de desarrollo **local**:

```sql
CREATE USER 'sigam_app'@'localhost' IDENTIFIED BY 'CAMBIAR_ESTA_CLAVE';
GRANT SELECT, INSERT, UPDATE, DELETE ON sigam_pos.* TO 'sigam_app'@'localhost';
```

3. Definir las variables de entorno `SIGAM_DB_USER`, `SIGAM_DB_PASSWORD` y, opcionalmente, `SIGAM_DB_URL` (predeterminada: `jdbc:mysql://localhost:3306/sigam_pos?connectionTimeZone=LOCAL`). Nunca cargar claves en el repositorio.

PowerShell, en la misma terminal desde la que ejecutes Maven:

```powershell
$env:SIGAM_DB_USER = "sigam_app"
$env:SIGAM_DB_PASSWORD = "TU_CLAVE_LOCAL"
```

## Primera cuenta administrativa

Ejecutar **una sola vez** desde una terminal interactiva:

```sh
mvn -q compile exec:java -Dexec.mainClass=ar.com.sigampos.InicializarAdmin
```

Se pedirán nombre, DNI, usuario y contraseña. Para ejecutar la interfaz:

```sh
mvn -q compile exec:java -Dexec.mainClass=ar.com.sigampos.Main
```

La propiedad `-Dexec.mainClass` permite seleccionar qué clase ejecutar. Si el IDE ignora esa propiedad, ejecutar `InicializarAdmin` directamente desde el IDE como clase Java, con el driver JDBC en las dependencias.

## Orden sugerido de primera ejecución
1. Ingresar como administrador.
2. En **Personal / Turnos**, crear un turno para el ID del empleado administrador (o crear un vendedor, iniciar sesión como él y usar el turno asignado).
3. En **Caja**, introducir el ID de turno, el fondo y pulsar **Abrir caja**. Copiar el ID de caja generado.
4. En **Productos**, crear productos con stock y precios válidos.
5. En **Ventas**, agregar productos por código y cobrar asignando importes a los medios de pago. Los pagos deben sumar exactamente el total.
6. En **Caja**, registrar entradas/salidas y luego hacer arqueo y cierre.
7. En **Análisis**, consultar operaciones por fecha.

## Qué funciona en esta primera base
- Login asociado a empleado activo y roles para mostrar módulos.
- Creación de empleado + usuario, alta/modificación de turno y baja lógica de empleado.
- Productos: alta, modificación por código, búsqueda y baja lógica; historial de cambios de precio.
- Carrito con cantidades, cobro dividido exacto (efectivo, transferencia, tarjeta, QR), descuento de stock, pago, detalle, venta y movimiento de efectivo **en una única transacción**.
- Caja: apertura, fondo inicial, entradas/salidas de efectivo con responsable, total esperado, arqueo y cierre transaccional.
- Análisis inicial: cantidad, monto y ticket promedio por rango de fechas.
- Modelo POO con clase abstracta `MovimientoCaja` y dos subclases, encapsulamiento en entidades y polimorfismo probado en `PruebaDominio`.

## Reglas de negocio que no hay que cambiar con Copilot
- Un `Usuario` aporta credenciales y rol; un `Empleado` es el responsable histórico de ventas y operaciones.
- Una caja pertenece a un turno y las ventas de esa caja pertenecen al empleado de ese turno (regla estricta de esta primera versión).
- `Pago` registra **todos** los medios; `MovimientoCaja` afecta **solo efectivo físico**. Transferencia, tarjeta y QR no suman al efectivo esperado.
- Fórmula: esperado = fondo inicial + efectivo vendido + entradas manuales − salidas manuales.
- Baja lógica de productos y empleados: no borrar información histórica.
- Costo y precio > 0; stock >= 0; stock mínimo/máximo, si se informan, > 0; máximo >= mínimo.
- Registrar venta y sus efectos de forma atómica (confirmar o revertir todo).
- Señas, fiados, servicios y clientes quedan fuera del prototipo actual.

## Limitaciones conocidas y próximas tareas
- **No se probó la conexión real contra un servidor MySQL** en este entorno. Sí se compiló todo el código y se ejecutaron las pruebas del modelo en Java.
- La interfaz es una base Swing funcional pero todavía no replica la densidad visual, atajos de teclado, edición de carrito, lector de códigos o desglose de billetes de las capturas.
- La UI todavía requiere introducir IDs de caja/turno; hay que reemplazarlos por selección automática de turno y caja activos.
- El dinero en efectivo se cobra por importe exacto; falta manejo de efectivo entregado y vuelto.
- Los controles de autorización por roles están en la UI; para producción también deben aplicarse en la capa de servicios, con sesiones verificadas.
- La creación del primer administrador debe endurecerse contra ejecución concurrente, aunque no puede repetirse secuencialmente.
- La apertura de caja impide otra caja abierta mediante comprobación transaccional en el caso normal, pero falta una restricción para carreras entre dos terminales.
- El módulo de análisis es inicial: faltan filtros por empleado, medios de pago, rentabilidad histórica y exportación CSV.
- Hay que implementar ABM completo de empleados/usuarios, búsqueda y actualización más cómoda de turnos, deshabilitación de sesiones, pruebas de integración y verificación de una instalación final.
- La edición actual de productos se hace por código; al guardar un producto existente, su estado queda activo. Separar modificación y reactivación antes de uso real.
- No hay soporte de servicios técnicos, señas, descuentos, recargos ni facturación fiscal.

## Prueba de modelo sin MySQL ni Maven

```sh
javac -encoding UTF-8 -d compilado $(find src/main/java -name '*.java')
javac -encoding UTF-8 -cp compilado -d compilado src/test/java/ar/com/sigampos/PruebaDominio.java
java -ea -cp compilado ar.com.sigampos.PruebaDominio
```

Para Windows es más sencillo usar `mvn test` en la próxima etapa cuando integremos JUnit.

**Compatibilidad AP2:** El código de producto pasa de `INTEGER` a `VARCHAR(50)` para permitir códigos de barras alfanuméricos. Se agregan `MovimientoCaja.empleado_id` y `ArqueoCaja.empleado_id` para registrar al responsable; la primera versión del TP3 usa una base nueva `sigam_pos`, no modifica automáticamente la BD del AP2. Se deben documentar estos cambios en la próxima revisión de DER y clases.
