-- SIGAM-POS TP3: migración desde sql/01_esquema.sql ORIGINAL (turno con fecha y empleado fijo).
-- Ejecutar UNA SOLA VEZ sobre sigam_pos, después de un backup completo.
-- MySQL 8.0.16+. Los DDL hacen COMMIT implícito; esta migración no es atómica.
-- No ejecutar el nuevo 01_esquema.sql sobre una BD vieja esperando que la actualice.
USE sigam_pos;

-- 1) Extender los turnos antiguos: cada fecha antigua define su único día semanal.
ALTER TABLE Turno
 ADD COLUMN nombre VARCHAR(50) NULL,
 ADD COLUMN lunes BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN martes BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN miercoles BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN jueves BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN viernes BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN sabado BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN domingo BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN estado ENUM('ACTIVO','INACTIVO') NOT NULL DEFAULT 'ACTIVO';
UPDATE Turno SET
 nombre=CONCAT('Turno legado #',id),
 lunes=(WEEKDAY(fecha)=0), martes=(WEEKDAY(fecha)=1),
 miercoles=(WEEKDAY(fecha)=2), jueves=(WEEKDAY(fecha)=3),
 viernes=(WEEKDAY(fecha)=4), sabado=(WEEKDAY(fecha)=5), domingo=(WEEKDAY(fecha)=6);
ALTER TABLE Turno MODIFY COLUMN nombre VARCHAR(50) NOT NULL;
-- Se dejan columnas legadas, ahora opcionales, para NO perder datos históricos de fechas.
-- Los nuevos turnos semanales usarán NULL en empleado_id y fecha.
ALTER TABLE Turno MODIFY COLUMN empleado_id BIGINT NULL, MODIFY COLUMN fecha DATE NULL;

-- Quitar el viejo CHECK que prohibía fin < inicio (nombre generado por MySQL).
SET @restriccion_antigua := (
 SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Turno'
   AND CONSTRAINT_TYPE='CHECK' AND CONSTRAINT_NAME NOT IN ('chk_turno_duracion','chk_turno_dias')
 ORDER BY CONSTRAINT_NAME LIMIT 1
);
SET @sentencia := IF(@restriccion_antigua IS NULL, 'SELECT 1',
 CONCAT('ALTER TABLE Turno DROP CHECK `',REPLACE(@restriccion_antigua,'`','``'),'`'));
PREPARE migracion_stmt FROM @sentencia;
EXECUTE migracion_stmt;
DEALLOCATE PREPARE migracion_stmt;
ALTER TABLE Turno
 ADD CONSTRAINT chk_turno_duracion CHECK (
  MOD(TIME_TO_SEC(hora_fin)-TIME_TO_SEC(hora_inicio)+86400,86400) BETWEEN 60 AND 43200
 ),
 ADD CONSTRAINT chk_turno_dias CHECK (
  lunes+martes+miercoles+jueves+viernes+sabado+domingo >= 1
 );

-- 2) Asociaciones del empleado anterior (no perder asignaciones).
CREATE TABLE EmpleadoTurno (
 empleado_id BIGINT NOT NULL, turno_id BIGINT NOT NULL,
 PRIMARY KEY(empleado_id,turno_id),
 FOREIGN KEY(empleado_id) REFERENCES Empleado(id),
 FOREIGN KEY(turno_id) REFERENCES Turno(id)
);
INSERT INTO EmpleadoTurno(empleado_id,turno_id)
 SELECT empleado_id,id FROM Turno WHERE empleado_id IS NOT NULL;

-- 3) Crear jornadas reales para todas las cajas existentes.
-- Se usa una columna auxiliar temporal para asociar cada caja con su jornada.
CREATE TABLE JornadaLaboral (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 empleado_id BIGINT NOT NULL, turno_id BIGINT NOT NULL,
 fecha_programada DATE NOT NULL,
 hora_inicio_programada TIME NOT NULL, hora_fin_programada TIME NOT NULL,
 inicio_real DATETIME NOT NULL, fin_real DATETIME NULL,
 estado ENUM('ABIERTA','CERRADA') NOT NULL DEFAULT 'ABIERTA',
 caja_legacy_id BIGINT NULL UNIQUE,
 FOREIGN KEY(empleado_id) REFERENCES Empleado(id),
 FOREIGN KEY(turno_id) REFERENCES Turno(id),
 CHECK(fin_real IS NULL OR fin_real >= inicio_real)
);
INSERT INTO JornadaLaboral
 (empleado_id,turno_id,fecha_programada,hora_inicio_programada,hora_fin_programada,
 inicio_real,fin_real,estado,caja_legacy_id)
 SELECT T.empleado_id,T.id,T.fecha,T.hora_inicio,T.hora_fin,
 C.fecha_apertura,
 CASE WHEN C.estado='CERRADA'
      THEN GREATEST(C.fecha_apertura,COALESCE(A.fecha_hora,C.fecha_apertura))
      ELSE NULL END,
 C.estado,C.id
 FROM Caja C JOIN Turno T ON T.id=C.turno_id
 LEFT JOIN ArqueoCaja A ON A.caja_id=C.id;
ALTER TABLE Caja ADD COLUMN jornada_id BIGINT NULL;
UPDATE Caja C JOIN JornadaLaboral J ON J.caja_legacy_id=C.id SET C.jornada_id=J.id;
ALTER TABLE Caja MODIFY COLUMN jornada_id BIGINT NOT NULL,
 ADD UNIQUE KEY uq_caja_jornada (jornada_id),
 ADD CONSTRAINT fk_caja_jornada FOREIGN KEY(jornada_id) REFERENCES JornadaLaboral(id);
ALTER TABLE JornadaLaboral DROP COLUMN caja_legacy_id;
ALTER TABLE ArqueoCaja ADD COLUMN observaciones VARCHAR(500) NULL;

-- 4) Comprobaciones: ambos conteos deben coincidir.
SELECT (SELECT COUNT(*) FROM Caja) AS cajas,
       (SELECT COUNT(*) FROM JornadaLaboral) AS jornadas_migradas;
SELECT T.id,T.nombre,T.fecha,T.hora_inicio,T.hora_fin FROM Turno T ORDER BY T.id;
