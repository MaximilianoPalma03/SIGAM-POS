# SIGAM-POS v0.5.0 — avance de implementación

## Objetivo
Separar caja física de caja digital **declarativa** de Mercado Pago (sin API),
y ofrecer historial de ventas con detalle de pagos mixtos.

## Estado de los archivos de esta rama
- `sql/03_evolucion_v0_5_0.sql`: **preparado, todavía NO ejecutar**.
- `src/main/java/servicio/CajaDigitalServicio.java`: servicio de operaciones declaradas;
  aún falta conectarlo a apertura, cobro y cierre existentes en la interfaz.
- `src/main/java/servicio/HistorialVentasServicio.java`: consultas por período,
  vendedor y medio; detalle de Pago y DetalleVenta sin multiplicar ventas mixtas.
- **Todavía no es una versión ejecutable v0.5.0 completa**. No confundirla con
  el parche v0.4.0 que se encuentra en la computadora del desarrollador.

## Precaución de integración
La rama remota `mejoras/v0.4.0` contenía únicamente limpieza de Git en el momento
de comenzar este desarrollo. Antes de integrar la interfaz v0.5.0, publicar
los archivos Java de v0.4.0 efectivamente probados en esa rama.

## Reglas de negocio
1. CajaDigital es opcional y tiene relación 1:1 con Caja.
2. Fondo de apertura, entradas y salidas de MP son declarados manualmente.
3. Cobros por TRANSFERENCIA y QR se contabilizan a MP **solo si se selecciona
   expresamente Mercado Pago**; TARJETA no ingresa automáticamente a esa caja.
4. Cada venta mixta es una sola Venta, con uno o varios Pago.
5. Los pagos digitales existentes de versiones anteriores siguen consultables
   en Pago; no se los asigna retroactivamente a una billetera desconocida.
6. Un cierre nuevo de caja digital y física deberá ser una única transacción.
7. Un administrador no recupera contraseñas anteriores; podrá emitir una clave
   temporal que obligue a elegir otra.
8. No se requiere conexión a Internet para operar con la caja digital local.
   No se consulta saldo real de Mercado Pago.

## Pendiente antes de habilitar v0.5.0
- Conectar apertura conjunta de caja física y digital y contabilización de
  Pago -> MovimientoCajaDigital sin duplicar operaciones.
- Conectar el cierre transaccional en CajaServicio y ventana de arqueo.
- Incorporar en VentanaPrincipal informes de ventas y jornadas.
- Implementado en el código el cambio obligatorio con reintento de errores, y la actualización protegida de credenciales. **Sin prueba de integración MySQL todavía**.
- UI: fichas compactas, importes, flechas, carrito consolidado y alertas de stock.
- Pruebas automatizadas e integración con MySQL de prueba.

## Migración
**No ejecutar todavía** `03_evolucion_v0_5_0.sql`. La estructura fue diseñada,
pero el flujo de apertura/cobro/cierre sigue sin enlazarse a ella. Cuando se
publique la integración completa: respaldar la BD, cerrar jornadas abiertas y
aplicar el script una sola vez. No ejecutar 01/02 sobre una base en uso.

## Base validada y compilación parcial
El trabajo ahora se deriva del commit `e0951dc` (versión 0.4.0 efectivamente probada). Los cambios de credenciales, carrito y edición de turnos compilaron en Java 21 en una reconstrucción local de esa misma fuente, y pasaron PruebaDominio, PruebaTurnos y PruebaImportes. La integración de CajaDigital/HistorialVentas aún debe compilarse, enlazarse y probarse antes de habilitar migraciones.
