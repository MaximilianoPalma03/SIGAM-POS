CREATE DATABASE IF NOT EXISTS sigam_pos CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE sigam_pos;

CREATE TABLE IF NOT EXISTS Empleado (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, nombre VARCHAR(100) NOT NULL,
 dni VARCHAR(20) NOT NULL UNIQUE, estado ENUM('ACTIVO','INACTIVO') NOT NULL DEFAULT 'ACTIVO'
);
CREATE TABLE IF NOT EXISTS Usuario (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, empleado_id BIGINT NOT NULL UNIQUE,
 nombre_usuario VARCHAR(50) NOT NULL UNIQUE, contrasena_hash VARCHAR(255) NOT NULL,
 rol ENUM('VENDEDOR','ADMINISTRADOR') NOT NULL,
 FOREIGN KEY (empleado_id) REFERENCES Empleado(id)
);
-- Turno = patrón semanal reutilizable; no contiene fecha ni empleado fijo.
-- Si la hora de fin es menor que la de inicio, termina al día siguiente.
CREATE TABLE IF NOT EXISTS Turno (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 nombre VARCHAR(50) NOT NULL,
 hora_inicio TIME NOT NULL, hora_fin TIME NOT NULL,
 lunes BOOLEAN NOT NULL DEFAULT FALSE, martes BOOLEAN NOT NULL DEFAULT FALSE,
 miercoles BOOLEAN NOT NULL DEFAULT FALSE, jueves BOOLEAN NOT NULL DEFAULT FALSE,
 viernes BOOLEAN NOT NULL DEFAULT FALSE, sabado BOOLEAN NOT NULL DEFAULT FALSE,
 domingo BOOLEAN NOT NULL DEFAULT FALSE,
 estado ENUM('ACTIVO','INACTIVO') NOT NULL DEFAULT 'ACTIVO',
 CONSTRAINT chk_turno_duracion CHECK (
   MOD(TIME_TO_SEC(hora_fin)-TIME_TO_SEC(hora_inicio)+86400,86400) BETWEEN 60 AND 43200
 ),
 CONSTRAINT chk_turno_dias CHECK (lunes+martes+miercoles+jueves+viernes+sabado+domingo >= 1)
);
CREATE TABLE IF NOT EXISTS EmpleadoTurno (
 empleado_id BIGINT NOT NULL, turno_id BIGINT NOT NULL,
 PRIMARY KEY(empleado_id,turno_id),
 FOREIGN KEY(empleado_id) REFERENCES Empleado(id),
 FOREIGN KEY(turno_id) REFERENCES Turno(id)
);
-- Una jornada representa una ejecución real del horario, con instantánea del horario programado.
CREATE TABLE IF NOT EXISTS JornadaLaboral (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 empleado_id BIGINT NOT NULL, turno_id BIGINT NOT NULL,
 fecha_programada DATE NOT NULL,
 hora_inicio_programada TIME NOT NULL, hora_fin_programada TIME NOT NULL,
 inicio_real DATETIME NOT NULL, fin_real DATETIME NULL,
 estado ENUM('ABIERTA','CERRADA') NOT NULL DEFAULT 'ABIERTA',
 FOREIGN KEY(empleado_id) REFERENCES Empleado(id),
 FOREIGN KEY(turno_id) REFERENCES Turno(id),
 CHECK(fin_real IS NULL OR fin_real >= inicio_real)
);
CREATE TABLE IF NOT EXISTS Caja (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 turno_id BIGINT NOT NULL, jornada_id BIGINT NOT NULL UNIQUE,
 fecha_apertura DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 fondo_inicial DECIMAL(12,2) NOT NULL,
 estado ENUM('ABIERTA','CERRADA') NOT NULL DEFAULT 'ABIERTA',
 FOREIGN KEY(turno_id) REFERENCES Turno(id),
 FOREIGN KEY(jornada_id) REFERENCES JornadaLaboral(id),
 CHECK(fondo_inicial >= 0)
);
CREATE TABLE IF NOT EXISTS Producto (
 codigo VARCHAR(50) PRIMARY KEY, nombre VARCHAR(100) NOT NULL,
 precio_venta DECIMAL(12,2) NOT NULL, costo DECIMAL(12,2) NOT NULL,
 stock INT NOT NULL, stock_minimo INT NULL, stock_maximo INT NULL,
 estado ENUM('ACTIVO','INACTIVO') NOT NULL DEFAULT 'ACTIVO',
 CHECK(precio_venta > 0), CHECK(costo > 0), CHECK(stock >= 0),
 CHECK(stock_minimo IS NULL OR stock_minimo > 0),
 CHECK(stock_maximo IS NULL OR stock_maximo > 0),
 CHECK(stock_minimo IS NULL OR stock_maximo IS NULL OR stock_maximo >= stock_minimo)
);
CREATE TABLE IF NOT EXISTS HistorialPrecio (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, producto_codigo VARCHAR(50) NOT NULL,
 precio DECIMAL(12,2) NOT NULL, fecha_cambio DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (producto_codigo) REFERENCES Producto(codigo)
);
CREATE TABLE IF NOT EXISTS Venta (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, caja_id BIGINT NOT NULL, empleado_id BIGINT NOT NULL,
 fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, total DECIMAL(12,2) NOT NULL,
 FOREIGN KEY(caja_id) REFERENCES Caja(id), FOREIGN KEY(empleado_id) REFERENCES Empleado(id), CHECK(total > 0)
);
CREATE TABLE IF NOT EXISTS DetalleVenta (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, venta_id BIGINT NOT NULL,
 producto_codigo VARCHAR(50) NOT NULL, cantidad INT NOT NULL, precio_unitario DECIMAL(12,2) NOT NULL,
 FOREIGN KEY(venta_id) REFERENCES Venta(id), FOREIGN KEY(producto_codigo) REFERENCES Producto(codigo),
 CHECK(cantidad > 0), CHECK(precio_unitario > 0)
);
CREATE TABLE IF NOT EXISTS Pago (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, venta_id BIGINT NOT NULL,
 medio_pago ENUM('EFECTIVO','TRANSFERENCIA','TARJETA','QR') NOT NULL,
 monto DECIMAL(12,2) NOT NULL, FOREIGN KEY(venta_id) REFERENCES Venta(id), CHECK(monto > 0)
);
CREATE TABLE IF NOT EXISTS MovimientoCaja (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, caja_id BIGINT NOT NULL,
 pago_id BIGINT NULL UNIQUE, empleado_id BIGINT NOT NULL,
 tipo ENUM('INGRESO_VENTA','ENTRADA','SALIDA') NOT NULL,
 monto DECIMAL(12,2) NOT NULL, fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 motivo VARCHAR(255), FOREIGN KEY(caja_id) REFERENCES Caja(id),
 FOREIGN KEY(pago_id) REFERENCES Pago(id), FOREIGN KEY(empleado_id) REFERENCES Empleado(id), CHECK(monto > 0)
);
CREATE TABLE IF NOT EXISTS ArqueoCaja (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, caja_id BIGINT NOT NULL UNIQUE, empleado_id BIGINT NOT NULL,
 fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 efectivo_esperado DECIMAL(12,2) NOT NULL, efectivo_contado DECIMAL(12,2) NOT NULL,
 diferencia DECIMAL(12,2) NOT NULL, fondo_proximo_turno DECIMAL(12,2) NOT NULL,
 observaciones VARCHAR(500) NULL,
 FOREIGN KEY(caja_id) REFERENCES Caja(id), FOREIGN KEY(empleado_id) REFERENCES Empleado(id),
 CHECK(efectivo_contado >= 0), CHECK(fondo_proximo_turno >= 0)
);
