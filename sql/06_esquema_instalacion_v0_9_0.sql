-- Esquema de instalación SIGAM-POS. SOLO para una BD nueva, creada tras validar ausencia.
CREATE TABLE `arqueocaja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `caja_id` bigint NOT NULL,
  `empleado_id` bigint NOT NULL,
  `fecha_hora` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `efectivo_esperado` decimal(12,2) NOT NULL,
  `efectivo_contado` decimal(12,2) NOT NULL,
  `diferencia` decimal(12,2) NOT NULL,
  `fondo_proximo_turno` decimal(12,2) NOT NULL,
  `observaciones` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `caja_id` (`caja_id`),
  KEY `empleado_id` (`empleado_id`),
  CONSTRAINT `arqueocaja_ibfk_1` FOREIGN KEY (`caja_id`) REFERENCES `caja` (`id`),
  CONSTRAINT `arqueocaja_ibfk_2` FOREIGN KEY (`empleado_id`) REFERENCES `empleado` (`id`),
  CONSTRAINT `arqueocaja_chk_1` CHECK ((`efectivo_contado` >= 0)),
  CONSTRAINT `arqueocaja_chk_2` CHECK ((`fondo_proximo_turno` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `arqueocajadigital` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `caja_digital_id` bigint NOT NULL,
  `empleado_id` bigint NOT NULL,
  `saldo_esperado` decimal(12,2) NOT NULL,
  `saldo_declarado` decimal(12,2) NOT NULL,
  `diferencia` decimal(12,2) NOT NULL,
  `fondo_proximo_turno` decimal(12,2) NOT NULL,
  `observaciones` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fecha_hora` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `caja_digital_id` (`caja_digital_id`),
  KEY `empleado_id` (`empleado_id`),
  CONSTRAINT `arqueocajadigital_ibfk_1` FOREIGN KEY (`caja_digital_id`) REFERENCES `cajadigital` (`id`),
  CONSTRAINT `arqueocajadigital_ibfk_2` FOREIGN KEY (`empleado_id`) REFERENCES `empleado` (`id`),
  CONSTRAINT `arqueocajadigital_chk_1` CHECK ((`saldo_declarado` >= 0)),
  CONSTRAINT `arqueocajadigital_chk_2` CHECK ((`fondo_proximo_turno` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `caja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `turno_id` bigint NOT NULL,
  `fecha_apertura` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fondo_inicial` decimal(12,2) NOT NULL,
  `estado` enum('ABIERTA','CERRADA') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ABIERTA',
  `jornada_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_caja_jornada` (`jornada_id`),
  KEY `turno_id` (`turno_id`),
  CONSTRAINT `caja_ibfk_1` FOREIGN KEY (`turno_id`) REFERENCES `turno` (`id`),
  CONSTRAINT `fk_caja_jornada` FOREIGN KEY (`jornada_id`) REFERENCES `jornadalaboral` (`id`),
  CONSTRAINT `caja_chk_1` CHECK ((`fondo_inicial` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `cajadigital` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `caja_id` bigint NOT NULL,
  `nombre` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Mercado Pago',
  `saldo_inicial` decimal(12,2) NOT NULL DEFAULT '0.00',
  `estado` enum('ABIERTA','CERRADA') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ABIERTA',
  PRIMARY KEY (`id`),
  UNIQUE KEY `caja_id` (`caja_id`),
  CONSTRAINT `fk_digital_caja` FOREIGN KEY (`caja_id`) REFERENCES `caja` (`id`),
  CONSTRAINT `cajadigital_chk_1` CHECK ((`saldo_inicial` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `detalleventa` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `venta_id` bigint NOT NULL,
  `producto_codigo` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `cantidad` int NOT NULL,
  `precio_unitario` decimal(12,2) NOT NULL,
  `costo_unitario` decimal(12,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `venta_id` (`venta_id`),
  KEY `producto_codigo` (`producto_codigo`),
  CONSTRAINT `detalleventa_ibfk_1` FOREIGN KEY (`venta_id`) REFERENCES `venta` (`id`),
  CONSTRAINT `detalleventa_ibfk_2` FOREIGN KEY (`producto_codigo`) REFERENCES `producto` (`codigo`),
  CONSTRAINT `detalleventa_chk_1` CHECK ((`cantidad` > 0)),
  CONSTRAINT `detalleventa_chk_2` CHECK ((`precio_unitario` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `empleado` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nombre` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `dni` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `estado` enum('ACTIVO','INACTIVO') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVO',
  PRIMARY KEY (`id`),
  UNIQUE KEY `dni` (`dni`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `empleadoturno` (
  `empleado_id` bigint NOT NULL,
  `turno_id` bigint NOT NULL,
  PRIMARY KEY (`empleado_id`,`turno_id`),
  KEY `turno_id` (`turno_id`),
  CONSTRAINT `empleadoturno_ibfk_1` FOREIGN KEY (`empleado_id`) REFERENCES `empleado` (`id`),
  CONSTRAINT `empleadoturno_ibfk_2` FOREIGN KEY (`turno_id`) REFERENCES `turno` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `historialprecio` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `producto_codigo` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `precio` decimal(12,2) NOT NULL,
  `fecha_cambio` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `producto_codigo` (`producto_codigo`),
  CONSTRAINT `historialprecio_ibfk_1` FOREIGN KEY (`producto_codigo`) REFERENCES `producto` (`codigo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `jornadalaboral` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `empleado_id` bigint NOT NULL,
  `turno_id` bigint NOT NULL,
  `fecha_programada` date NOT NULL,
  `hora_inicio_programada` time NOT NULL,
  `hora_fin_programada` time NOT NULL,
  `inicio_real` datetime NOT NULL,
  `fin_real` datetime DEFAULT NULL,
  `estado` enum('ABIERTA','CERRADA') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ABIERTA',
  PRIMARY KEY (`id`),
  KEY `empleado_id` (`empleado_id`),
  KEY `turno_id` (`turno_id`),
  CONSTRAINT `jornadalaboral_ibfk_1` FOREIGN KEY (`empleado_id`) REFERENCES `empleado` (`id`),
  CONSTRAINT `jornadalaboral_ibfk_2` FOREIGN KEY (`turno_id`) REFERENCES `turno` (`id`),
  CONSTRAINT `jornadalaboral_chk_1` CHECK (((`fin_real` is null) or (`fin_real` >= `inicio_real`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `movimientocaja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `caja_id` bigint NOT NULL,
  `pago_id` bigint DEFAULT NULL,
  `empleado_id` bigint NOT NULL,
  `tipo` enum('INGRESO_VENTA','ENTRADA','SALIDA') COLLATE utf8mb4_unicode_ci NOT NULL,
  `monto` decimal(12,2) NOT NULL,
  `fecha_hora` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `motivo` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `pago_id` (`pago_id`),
  KEY `caja_id` (`caja_id`),
  KEY `empleado_id` (`empleado_id`),
  CONSTRAINT `movimientocaja_ibfk_1` FOREIGN KEY (`caja_id`) REFERENCES `caja` (`id`),
  CONSTRAINT `movimientocaja_ibfk_2` FOREIGN KEY (`pago_id`) REFERENCES `pago` (`id`),
  CONSTRAINT `movimientocaja_ibfk_3` FOREIGN KEY (`empleado_id`) REFERENCES `empleado` (`id`),
  CONSTRAINT `movimientocaja_chk_1` CHECK ((`monto` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `movimientocajadigital` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `caja_digital_id` bigint NOT NULL,
  `pago_id` bigint DEFAULT NULL,
  `empleado_id` bigint NOT NULL,
  `tipo` enum('COBRO_TRANSFERENCIA','COBRO_QR','ENTRADA','SALIDA') COLLATE utf8mb4_unicode_ci NOT NULL,
  `medio_digital` enum('TRANSFERENCIA','QR','TARJETA') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `monto` decimal(12,2) NOT NULL,
  `motivo` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `fecha_hora` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `pago_id` (`pago_id`),
  KEY `caja_digital_id` (`caja_digital_id`),
  KEY `empleado_id` (`empleado_id`),
  CONSTRAINT `movimientocajadigital_ibfk_1` FOREIGN KEY (`caja_digital_id`) REFERENCES `cajadigital` (`id`),
  CONSTRAINT `movimientocajadigital_ibfk_2` FOREIGN KEY (`pago_id`) REFERENCES `pago` (`id`),
  CONSTRAINT `movimientocajadigital_ibfk_3` FOREIGN KEY (`empleado_id`) REFERENCES `empleado` (`id`),
  CONSTRAINT `movimientocajadigital_chk_1` CHECK ((`monto` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notificacionstockdescartada` (
  `empleado_id` bigint NOT NULL,
  `producto_codigo` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `stock_registrado` int NOT NULL,
  `tipo` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`empleado_id`,`producto_codigo`),
  KEY `producto_codigo` (`producto_codigo`),
  CONSTRAINT `notificacionstockdescartada_ibfk_1` FOREIGN KEY (`empleado_id`) REFERENCES `empleado` (`id`),
  CONSTRAINT `notificacionstockdescartada_ibfk_2` FOREIGN KEY (`producto_codigo`) REFERENCES `producto` (`codigo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pago` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `venta_id` bigint NOT NULL,
  `medio_pago` enum('EFECTIVO','TRANSFERENCIA','TARJETA','QR') COLLATE utf8mb4_unicode_ci NOT NULL,
  `monto` decimal(12,2) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `venta_id` (`venta_id`),
  CONSTRAINT `pago_ibfk_1` FOREIGN KEY (`venta_id`) REFERENCES `venta` (`id`),
  CONSTRAINT `pago_chk_1` CHECK ((`monto` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `producto` (
  `codigo` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nombre` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `precio_venta` decimal(12,2) NOT NULL,
  `costo` decimal(12,2) NOT NULL,
  `stock` int NOT NULL,
  `stock_minimo` int DEFAULT NULL,
  `stock_maximo` int DEFAULT NULL,
  `estado` enum('ACTIVO','INACTIVO') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVO',
  PRIMARY KEY (`codigo`),
  CONSTRAINT `producto_chk_1` CHECK ((`precio_venta` > 0)),
  CONSTRAINT `producto_chk_2` CHECK ((`costo` > 0)),
  CONSTRAINT `producto_chk_3` CHECK ((`stock` >= 0)),
  CONSTRAINT `producto_chk_4` CHECK (((`stock_minimo` is null) or (`stock_minimo` > 0))),
  CONSTRAINT `producto_chk_5` CHECK (((`stock_maximo` is null) or (`stock_maximo` > 0))),
  CONSTRAINT `producto_chk_6` CHECK (((`stock_minimo` is null) or (`stock_maximo` is null) or (`stock_maximo` >= `stock_minimo`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `turno` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `empleado_id` bigint DEFAULT NULL,
  `fecha` date DEFAULT NULL,
  `hora_inicio` time NOT NULL,
  `hora_fin` time NOT NULL,
  `nombre` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `lunes` tinyint(1) NOT NULL DEFAULT '0',
  `martes` tinyint(1) NOT NULL DEFAULT '0',
  `miercoles` tinyint(1) NOT NULL DEFAULT '0',
  `jueves` tinyint(1) NOT NULL DEFAULT '0',
  `viernes` tinyint(1) NOT NULL DEFAULT '0',
  `sabado` tinyint(1) NOT NULL DEFAULT '0',
  `domingo` tinyint(1) NOT NULL DEFAULT '0',
  `estado` enum('ACTIVO','INACTIVO') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVO',
  PRIMARY KEY (`id`),
  KEY `empleado_id` (`empleado_id`),
  CONSTRAINT `turno_ibfk_1` FOREIGN KEY (`empleado_id`) REFERENCES `empleado` (`id`),
  CONSTRAINT `chk_turno_dias` CHECK ((((((((`lunes` + `martes`) + `miercoles`) + `jueves`) + `viernes`) + `sabado`) + `domingo`) >= 1)),
  CONSTRAINT `chk_turno_duracion` CHECK (((((time_to_sec(`hora_fin`) - time_to_sec(`hora_inicio`)) + 86400) % 86400) between 60 and 43200))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `usuario` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `empleado_id` bigint NOT NULL,
  `nombre_usuario` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contrasena_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `rol` enum('VENDEDOR','ADMINISTRADOR') COLLATE utf8mb4_unicode_ci NOT NULL,
  `debe_cambiar_clave` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `empleado_id` (`empleado_id`),
  UNIQUE KEY `nombre_usuario` (`nombre_usuario`),
  CONSTRAINT `usuario_ibfk_1` FOREIGN KEY (`empleado_id`) REFERENCES `empleado` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `venta` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `caja_id` bigint NOT NULL,
  `empleado_id` bigint NOT NULL,
  `fecha_hora` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `total` decimal(12,2) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `caja_id` (`caja_id`),
  KEY `empleado_id` (`empleado_id`),
  KEY `idx_venta_fecha_hora` (`fecha_hora`),
  CONSTRAINT `venta_ibfk_1` FOREIGN KEY (`caja_id`) REFERENCES `caja` (`id`),
  CONSTRAINT `venta_ibfk_2` FOREIGN KEY (`empleado_id`) REFERENCES `empleado` (`id`),
  CONSTRAINT `venta_chk_1` CHECK ((`total` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sigam_version_esquema (
  version VARCHAR(20) PRIMARY KEY,
  instalada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO sigam_version_esquema (version) VALUES ('0.9.0');
