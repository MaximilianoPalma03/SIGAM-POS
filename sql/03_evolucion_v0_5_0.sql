-- SIGAM-POS v0.5.0. Respaldar sigam_pos y ejecutar una sola vez.
-- No ejecutar 01 ni 02 de nuevo, no elimina los datos historicos.
USE sigam_pos;
ALTER TABLE Usuario ADD COLUMN debe_cambiar_clave BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE CajaDigital (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 caja_id BIGINT NOT NULL UNIQUE,
 nombre VARCHAR(50) NOT NULL DEFAULT 'Mercado Pago',
 saldo_inicial DECIMAL(12,2) NOT NULL DEFAULT 0,
 estado ENUM('ABIERTA','CERRADA') NOT NULL DEFAULT 'ABIERTA',
 CONSTRAINT fk_digital_caja FOREIGN KEY(caja_id) REFERENCES Caja(id),
 CHECK(saldo_inicial >= 0)
) ENGINE=InnoDB;
CREATE TABLE MovimientoCajaDigital (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 caja_digital_id BIGINT NOT NULL,
 pago_id BIGINT NULL UNIQUE,
 empleado_id BIGINT NOT NULL,
 tipo ENUM('COBRO_TRANSFERENCIA','COBRO_QR','ENTRADA','SALIDA') NOT NULL,
 monto DECIMAL(12,2) NOT NULL,
 motivo VARCHAR(255) NOT NULL,
 fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(caja_digital_id) REFERENCES CajaDigital(id),
 FOREIGN KEY(pago_id) REFERENCES Pago(id),
 FOREIGN KEY(empleado_id) REFERENCES Empleado(id),
 CHECK(monto > 0)
) ENGINE=InnoDB;
CREATE TABLE ArqueoCajaDigital (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 caja_digital_id BIGINT NOT NULL UNIQUE,
 empleado_id BIGINT NOT NULL,
 saldo_esperado DECIMAL(12,2) NOT NULL,
 saldo_declarado DECIMAL(12,2) NOT NULL,
 diferencia DECIMAL(12,2) NOT NULL,
 fondo_proximo_turno DECIMAL(12,2) NOT NULL,
 observaciones VARCHAR(500),
 fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(caja_digital_id) REFERENCES CajaDigital(id),
 FOREIGN KEY(empleado_id) REFERENCES Empleado(id),
 CHECK(saldo_declarado >= 0),CHECK(fondo_proximo_turno >= 0)
) ENGINE=InnoDB;
CREATE TABLE NotificacionStockDescartada (
 empleado_id BIGINT NOT NULL,
 producto_codigo VARCHAR(50) NOT NULL,
 stock_registrado INT NOT NULL,
 tipo VARCHAR(20) NOT NULL,
 PRIMARY KEY(empleado_id,producto_codigo),
 FOREIGN KEY(empleado_id) REFERENCES Empleado(id),
 FOREIGN KEY(producto_codigo) REFERENCES Producto(codigo)
) ENGINE=InnoDB;
-- Importante: los pagos preexistentes siguen consultables en Pago.
-- No se inventa a que billetera fueron acreditados.
