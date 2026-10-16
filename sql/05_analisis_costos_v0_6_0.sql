-- SIGAM-POS v0.6.0: registra costo de venta en el instante de cobro.
-- Ejecutar UNA SOLA VEZ, tras respaldar la base completa sigam_pos.
-- NO ejecutar 01/02/03/04 nuevamente.
USE sigam_pos;

-- NULL en ventas antiguas significa COSTO HISTÓRICO DESCONOCIDO.
-- No rellenar con Producto.costo actual: eso falsearía el análisis histórico.
ALTER TABLE DetalleVenta ADD COLUMN costo_unitario DECIMAL(12,2) NULL AFTER precio_unitario;

-- Índice para filtros por fecha y agrupaciones en Análisis.
CREATE INDEX idx_venta_fecha_hora ON Venta(fecha_hora);

-- Comprobación. La cantidad de ventas no debe cambiar.
SELECT COUNT(*) AS ventas_existentes FROM Venta;
SELECT COUNT(*) AS detalles_historicos_sin_costo FROM DetalleVenta WHERE costo_unitario IS NULL;
SHOW COLUMNS FROM DetalleVenta LIKE 'costo_unitario';
