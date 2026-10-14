-- SIGAM-POS v0.5.0: evolución conservando los datos existentes.
-- IMPORTANTE: ejecutar UNA SOLA VEZ en MySQL Workbench, tras exportar respaldo.
-- No elimina ventas, pagos, cajas ni jornadas anteriores.
USE sigam_pos;

-- El alta o blanqueo de acceso puede exigir un cambio al siguiente inicio de sesión.
-- DEFAULT 0 mantiene habilitadas las cuentas anteriores hasta su próximo blanqueo.
ALTER TABLE Usuario
    ADD COLUMN debe_cambiar_clave BOOLEAN NOT NULL DEFAULT FALSE;

-- Cuenta digital independiente para Mercado Pago, asociada a UNA caja/jornada.
-- Ningún dato proviene de API externa: saldo inicial y saldo contado son declarados.
CREATE TABLE IF NOT EXISTS CajaDigital (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    caja_id BIGINT NOT NULL UNIQUE,
    nombre VARCHAR(50) NOT NULL DEFAULT 'Mercado Pago',
    saldo_inicial DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    estado ENUM('ABIERTA','CERRADA') NOT NULL DEFAULT 'ABIERTA',
    fecha_apertura DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_digital_caja FOREIGN KEY (caja_id) REFERENCES Caja(id),
    CONSTRAINT chk_digital_inicial CHECK (saldo_inicial >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS MovimientoCajaDigital (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    caja_digital_id BIGINT NOT NULL,
    pago_id BIGINT NULL UNIQUE,
    empleado_id BIGINT NOT NULL,
    tipo ENUM('COBRO_TRANSFERENCIA','COBRO_QR','ENTRADA','SALIDA') NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dmov_caja FOREIGN KEY (caja_digital_id) REFERENCES CajaDigital(id),
    CONSTRAINT fk_dmov_pago FOREIGN KEY (pago_id) REFERENCES Pago(id),
    CONSTRAINT fk_dmov_empleado FOREIGN KEY (empleado_id) REFERENCES Empleado(id),
    CONSTRAINT chk_dmov_monto CHECK (monto > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ArqueoCajaDigital (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    caja_digital_id BIGINT NOT NULL UNIQUE,
    empleado_id BIGINT NOT NULL,
    saldo_esperado DECIMAL(12,2) NOT NULL,
    saldo_declarado DECIMAL(12,2) NOT NULL,
    diferencia DECIMAL(12,2) NOT NULL,
    fondo_proximo_turno DECIMAL(12,2) NOT NULL,
    observaciones VARCHAR(500) NULL,
    fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_darqueo_caja FOREIGN KEY (caja_digital_id) REFERENCES CajaDigital(id),
    CONSTRAINT fk_darqueo_empleado FOREIGN KEY (empleado_id) REFERENCES Empleado(id),
    CONSTRAINT chk_darqueo_saldo CHECK (saldo_declarado >= 0),
    CONSTRAINT chk_darqueo_fondo CHECK (fondo_proximo_turno >= 0)
) ENGINE=InnoDB;

-- Los pagos de ventas históricas siguen estando en Pago y se reflejan
-- en informes. No se vuelcan automáticamente a CajaDigital: no sabemos
-- qué billetera recibió cada transferencia ni qué saldo tenía al abrir.
