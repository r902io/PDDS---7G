-- experimentacion
CREATE TABLE perfil_parametro (
    perfil VARCHAR(60) NOT NULL,
    clave VARCHAR(100) NOT NULL,
    valor VARCHAR(255) NOT NULL,
    PRIMARY KEY (perfil, clave)
);
-- Puntos de partida del informe, no parámetros óptimos ya calibrados.
INSERT INTO perfil_parametro(perfil,clave,valor) VALUES
('BASE','ga.poblacion','60'),
('BASE','ga.generaciones','150'),
('BASE','ga.cruzamiento','0.9'),
('BASE','ga.mutacion','0.01'),
('BASE','ga.elitismo','1'),
('BASE','ga.torneo','2'),
('BASE','ga.proporcionVoraz','0.5'),
('BASE','sa.temperaturaInicial','1000'),
('BASE','sa.temperaturaFinal','1'),
('BASE','sa.enfriamiento','0.95'),
('BASE','sa.iteraciones','40'),
('BASE','sa.recalentamiento','0.3'),
('BASE','sa.calibrar','true'),
('BASE','sa.aceptacion','0.6'),
('BASE','sa.muestras','100'),
('BASE','sa.maxNiveles','1000'),
('BASE','busqueda.movimientos','DOS_OPT,SWAP,RELOCATE,CROSS_ROUTE,VEHICLE_CHANGE'),
('BASE','objetivo.beta1','100'),
('BASE','objetivo.beta2','1000'),
('BASE','objetivo.beta3','10000'),
('BASE','operacion.servicioHoras','1'),
('BASE','operacion.servicioDentroPlazo','false'),
('BASE','operacion.distanciaNodoKm','1'),
('BASE','operacion.incluirRetorno','true'),
('BASE','experimento.repeticiones','30'),
('BASE','experimento.semillaBase','42'),
('BASE','experimento.tamanios','25,50,100'),
('BASE','prioridad.REGULAR_36H','36'),
('BASE','prioridad.PRIORIZADO_18H','18'),
('BASE','prioridad.PRIORIZADO_12H','12'),
('BASE','prioridad.PRIORIZADO_8H','8'),
('BASE','prioridad.PRIORIZADO_4H','4');

-- Catálogo de flota de arranque. El lector usa las filas reales de vehiculo,
-- no estas cantidades, de modo que futuras modificaciones de flota son efectivas.
CREATE TABLE tipo_vehiculo_inicial (
    tipo VARCHAR(20) PRIMARY KEY,
    prefijo VARCHAR(2) NOT NULL,
    cantidad INT NOT NULL,
    capacidad INT NOT NULL,
    velocidad DECIMAL(7,2) NOT NULL,
    costo DECIMAL(7,2) NOT NULL
);
INSERT INTO tipo_vehiculo_inicial VALUES
('AUTO','TA',10,24,40,8),('MOTO','TM',15,8,25,6),('BICICLETA','TB',12,4,12,3);
INSERT INTO vehiculo(id_vehiculo,tipo,capacidad_paquetes,velocidad_kmh,costo_por_km,posicion_x,posicion_y)
SELECT CONCAT(t.prefijo,LPAD(n.numero,2,'0')),t.tipo,t.capacidad,t.velocidad,t.costo,a.ubicacion_x,a.ubicacion_y
FROM tipo_vehiculo_inicial t
CROSS JOIN (SELECT 1 numero UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
 UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15) n
JOIN almacen a ON a.tipo='CENTRAL'
WHERE n.numero<=t.cantidad
 AND NOT EXISTS(SELECT 1 FROM vehiculo v WHERE v.id_vehiculo=CONCAT(t.prefijo,LPAD(n.numero,2,'0')));

CREATE TABLE carga_pedidos_archivo (
    huella CHAR(64) PRIMARY KEY,
    anio INT NOT NULL,
    mes INT NOT NULL,
    filas INT NOT NULL,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE experimento_numerico (
    id CHAR(36) PRIMARY KEY,
    perfil VARCHAR(60) NOT NULL,
    fase VARCHAR(20) NOT NULL,
    modelo VARCHAR(50) NOT NULL,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    informe_json JSON NOT NULL
);