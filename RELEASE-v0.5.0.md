# SIGAM-POS v0.5.0 — Versión candidata a pruebas

## Antes de ejecutar
- Partimos de la v0.4.0 validada; main permanece sin modificar.
- Hacer una copia de seguridad de MySQL antes de actualizar.
- Ejecutar una sola vez sql/03_evolucion_v0_5_0.sql sobre sigam_pos. No repetir 01 ni 02.
- Cerrar la jornada anterior antes de abrir una nueva con Mercado Pago.
- Mantener SIGAM_DB_USER y SIGAM_DB_PASSWORD configurados.

## Funcionalidades
- Cambio obligatorio de clave tras blanqueo. Los errores mantienen el diálogo abierto.
- Carrito consolidado, controles de cantidad y validación de stock.
- Turnos editables con doble clic; fichas de empleados compactas.
- Montos presentados con formato monetario argentino.
- Caja física y Mercado Pago separados, con saldos iniciales y movimientos manuales.
- Cobros por transferencia y QR en la caja MP declarada, en la misma transacción de venta.
- Arqueo y cierre simultáneo de caja física y digital; recuento auxiliar de billetes.
- Historial de ventas y medios de pago; historial de jornadas y diferencias.
- Alertas de stock bajo, próximo al mínimo y agotado para productos activos.

## Reglas
- No se usa API de Mercado Pago. Saldos iniciales y finales son declaraciones del empleado.
- Las transferencias/QR requieren abrir MP en la jornada; tarjeta se informa, pero no acredita automáticamente en MP.
- Los pagos históricos anteriores siguen en Pago y los reportes sin atribuir billeteras no verificadas.
- Un pago mixto sigue siendo una única Venta con varios Pago.
- Las contraseñas antiguas no se pueden revelar, solo restablecer.

## Validación
- Compilación Java 21 --release 17 y pruebas locales de dominio, turnos, importes y alertas completadas.
- Pendiente: prueba de integración con servidor MySQL real y flujo de interfaz en Windows.

## Límites
- Prototipo académico. Sin API externa, facturación fiscal ni certificación contable.