-- SIGAM-POS v0.5.1: identifica el medio de cada movimiento digital.
-- Ejecutar UNA SOLA VEZ sobre la base v0.5.0, después de un respaldo.
-- No elimina, traslada ni modifica importes históricos de ninguna caja.
USE sigam_pos;

ALTER TABLE MovimientoCajaDigital
    ADD COLUMN medio_digital ENUM('TRANSFERENCIA','QR','TARJETA') NULL AFTER tipo;

-- Los cobros de ventas existentes sí tienen medio inequívoco según su tipo.
UPDATE MovimientoCajaDigital
SET medio_digital = CASE tipo
    WHEN 'COBRO_TRANSFERENCIA' THEN 'TRANSFERENCIA'
    WHEN 'COBRO_QR' THEN 'QR'
    ELSE NULL
END
WHERE tipo IN ('COBRO_TRANSFERENCIA','COBRO_QR') AND medio_digital IS NULL;

-- Los movimientos manuales anteriores quedan SIN CLASIFICAR porque no conocemos
-- su canal real. No inventamos un medio de pago y no movemos saldos de caja.
SELECT tipo,COALESCE(medio_digital,'SIN CLASIFICAR (v0.5)') AS medio,
       COUNT(*) AS movimientos,COALESCE(SUM(monto),0) AS total
FROM MovimientoCajaDigital
GROUP BY tipo,medio_digital
ORDER BY tipo,medio;
