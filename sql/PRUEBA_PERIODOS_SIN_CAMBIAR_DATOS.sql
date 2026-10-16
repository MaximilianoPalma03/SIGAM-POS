-- Prueba de ventanas de SIGAM-POS v0.6.0.
-- NO modifica sigam_pos: los registros son temporales dentro de la consulta.
-- Se ejecuta en cualquier MySQL 8.0+ y retorna conteos 1,2,3.
WITH ventas_simuladas AS (
  SELECT DATE_SUB(CURRENT_DATE(), INTERVAL 7 MONTH) fecha, 900 AS importe
  UNION ALL SELECT DATE_SUB(CURRENT_DATE(), INTERVAL 5 MONTH), 100
  UNION ALL SELECT DATE_SUB(CURRENT_DATE(), INTERVAL 2 MONTH), 200
  UNION ALL SELECT DATE_SUB(CURRENT_DATE(), INTERVAL 10 DAY), 300
)
SELECT
  SUM(CASE WHEN fecha >= DATE_ADD(DATE_SUB(CURRENT_DATE(), INTERVAL 1 MONTH), INTERVAL 1 DAY) THEN 1 ELSE 0 END) AS ventas_1_mes,
  SUM(CASE WHEN fecha >= DATE_ADD(DATE_SUB(CURRENT_DATE(), INTERVAL 3 MONTH), INTERVAL 1 DAY) THEN 1 ELSE 0 END) AS ventas_3_meses,
  SUM(CASE WHEN fecha >= DATE_ADD(DATE_SUB(CURRENT_DATE(), INTERVAL 6 MONTH), INTERVAL 1 DAY) THEN 1 ELSE 0 END) AS ventas_6_meses,
  SUM(CASE WHEN fecha >= DATE_ADD(DATE_SUB(CURRENT_DATE(), INTERVAL 6 MONTH), INTERVAL 1 DAY) THEN importe ELSE 0 END) AS facturacion_6_meses
FROM ventas_simuladas;
-- Esperado: 1, 2, 3, 600.
-- Las ventas de hace 7 meses no participan en el análisis de 6 meses.
