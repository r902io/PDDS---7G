-- schema_inicial
CREATE TABLE ciudad (
    id_ciudad      INT NOT NULL AUTO_INCREMENT,
    nombre         VARCHAR(50) NOT NULL DEFAULT 'SISRAP City',
    ancho_km       INT NOT NULL DEFAULT 70,
    alto_km        INT NOT NULL DEFAULT 50,
    PRIMARY KEY (id_ciudad)
);

CREATE TABLE nodo (
    id_nodo        INT NOT NULL AUTO_INCREMENT,
    id_ciudad      INT NOT NULL,
    x              INT NOT NULL,
    y              INT NOT NULL,
    PRIMARY KEY (id_nodo),
    CONSTRAINT fk_nodo_ciudad FOREIGN KEY (id_ciudad) REFERENCES ciudad (id_ciudad),
    CONSTRAINT uq_nodo_coordenada UNIQUE (id_ciudad, x, y),
    CONSTRAINT chk_nodo_x CHECK (x >= 0),
    CONSTRAINT chk_nodo_y CHECK (y >= 0)
);

CREATE INDEX idx_nodo_coordenadas ON nodo (x, y);

CREATE TABLE via (
    id_via              INT NOT NULL AUTO_INCREMENT,
    id_ciudad           INT NOT NULL,
    origen_x            INT NOT NULL,
    origen_y            INT NOT NULL,
    destino_x           INT NOT NULL,
    destino_y           INT NOT NULL,
    distancia_km        DECIMAL(5,2) NOT NULL DEFAULT 1.00,
    transitable         BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id_via),
    CONSTRAINT fk_via_ciudad FOREIGN KEY (id_ciudad) REFERENCES ciudad (id_ciudad),
    CONSTRAINT chk_via_adyacente CHECK (
        (ABS(origen_x - destino_x) = 1 AND origen_y = destino_y) OR
        (ABS(origen_y - destino_y) = 1 AND origen_x = destino_x)
    )
);

CREATE INDEX idx_via_origen ON via (origen_x, origen_y);
CREATE INDEX idx_via_destino ON via (destino_x, destino_y);

CREATE TABLE almacen (
    id_almacen          VARCHAR(20) NOT NULL,
    nombre              VARCHAR(50) NOT NULL,
    tipo                VARCHAR(20) NOT NULL,
    ubicacion_x         INT NOT NULL,
    ubicacion_y         INT NOT NULL,
    capacidad_maxima    INT,
    stock_actual        INT,
    hora_recarga        TIME,
    PRIMARY KEY (id_almacen),
    CONSTRAINT chk_almacen_tipo CHECK (tipo IN ('CENTRAL', 'INTERMEDIO')),
    CONSTRAINT chk_almacen_capacidad CHECK (
        (tipo = 'CENTRAL' AND capacidad_maxima IS NULL) OR
        (tipo = 'INTERMEDIO' AND capacidad_maxima IS NOT NULL)
    )
);

CREATE TABLE turno (
    id_turno            INT NOT NULL AUTO_INCREMENT,
    hora_inicio         TIME NOT NULL,
    hora_fin            TIME NOT NULL,
    hora_alimentacion   TIME,
    PRIMARY KEY (id_turno)
);

CREATE TABLE conductor (
    id_conductor        VARCHAR(20) NOT NULL,
    nombre              VARCHAR(100) NOT NULL,
    id_turno_asignado   INT NOT NULL,
    PRIMARY KEY (id_conductor),
    CONSTRAINT fk_conductor_turno FOREIGN KEY (id_turno_asignado) REFERENCES turno (id_turno)
);

CREATE TABLE vehiculo (
    id_vehiculo         VARCHAR(20) NOT NULL,
    tipo                VARCHAR(20) NOT NULL,
    capacidad_paquetes  INT NOT NULL,
    velocidad_kmh       DECIMAL(5,2) NOT NULL,
    costo_por_km        DECIMAL(6,2) NOT NULL,
    estado              VARCHAR(30) NOT NULL DEFAULT 'DISPONIBLE',
    posicion_x          INT NOT NULL DEFAULT 25,
    posicion_y          INT NOT NULL DEFAULT 15,
    id_conductor_actual VARCHAR(20),
    PRIMARY KEY (id_vehiculo),
    CONSTRAINT fk_vehiculo_conductor FOREIGN KEY (id_conductor_actual) REFERENCES conductor (id_conductor),
    CONSTRAINT chk_vehiculo_tipo CHECK (tipo IN ('AUTO', 'MOTO', 'BICICLETA')),
    CONSTRAINT chk_vehiculo_estado CHECK (estado IN ('DISPONIBLE', 'EN_RUTA', 'EN_AVERIA', 'RETORNANDO_ALMACEN'))
);

CREATE INDEX idx_vehiculo_estado ON vehiculo (estado);

CREATE TABLE pedido (
    id_pedido           BIGINT NOT NULL AUTO_INCREMENT,
    id_cliente          VARCHAR(20),
    cantidad_qq         INT NOT NULL,
    prioridad           VARCHAR(20) NOT NULL,
    horas_limite        INT NOT NULL,
    fecha_llegada       DATETIME(6) NOT NULL,
    fecha_entrega_real  DATETIME(6),
    estado              VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    ubicacion_x         INT NOT NULL,
    ubicacion_y         INT NOT NULL,
    PRIMARY KEY (id_pedido),
    CONSTRAINT chk_pedido_cantidad CHECK (cantidad_qq > 0),
    CONSTRAINT chk_pedido_prioridad CHECK (prioridad IN ('REGULAR_36H', 'PRIORIZADO_18H', 'PRIORIZADO_12H', 'PRIORIZADO_8H', 'PRIORIZADO_4H')),
    CONSTRAINT chk_pedido_estado CHECK (estado IN ('PENDIENTE', 'EN_RUTA', 'ENTREGADO', 'REASIGNADO', 'RETRASADO'))
);

CREATE INDEX idx_pedido_estado ON pedido (estado);
CREATE INDEX idx_pedido_id_cliente ON pedido (id_cliente);
CREATE INDEX idx_pedido_fecha_llegada ON pedido (fecha_llegada);
CREATE INDEX idx_pedido_ubicacion ON pedido (ubicacion_x, ubicacion_y);

CREATE TABLE incidencia (
    id_incidencia       BIGINT NOT NULL AUTO_INCREMENT,
    tipo                VARCHAR(20) NOT NULL,
    fecha_ocurrencia    DATETIME(6) NOT NULL,
    activa              BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id_incidencia),
    CONSTRAINT chk_incidencia_tipo CHECK (tipo IN ('BLOQUEO', 'AVERIA'))
);

CREATE INDEX idx_incidencia_activa ON incidencia (activa);

CREATE TABLE bloqueo (
    id_incidencia       BIGINT NOT NULL,
    fecha_inicio        DATETIME(6) NOT NULL,
    fecha_fin           DATETIME(6) NOT NULL,
    PRIMARY KEY (id_incidencia),
    CONSTRAINT fk_bloqueo_incidencia FOREIGN KEY (id_incidencia) REFERENCES incidencia (id_incidencia) ON DELETE CASCADE
);

CREATE TABLE bloqueo_nodo (
    id_bloqueo_nodo     BIGINT NOT NULL AUTO_INCREMENT,
    id_incidencia       BIGINT NOT NULL,
    orden               INT NOT NULL,
    x                   INT NOT NULL,
    y                   INT NOT NULL,
    PRIMARY KEY (id_bloqueo_nodo),
    CONSTRAINT fk_bloqueo_nodo_bloqueo FOREIGN KEY (id_incidencia) REFERENCES bloqueo (id_incidencia) ON DELETE CASCADE,
    CONSTRAINT uq_bloqueo_orden UNIQUE (id_incidencia, orden)
);

CREATE INDEX idx_bloqueo_nodo_coordenadas ON bloqueo_nodo (x, y);

CREATE TABLE averia (
    id_incidencia         BIGINT NOT NULL,
    id_vehiculo           VARCHAR(20) NOT NULL,
    tipo_averia           VARCHAR(20) NOT NULL,
    ubicacion_x           INT NOT NULL,
    ubicacion_y           INT NOT NULL,
    hora_retorno_estimada DATETIME(6),
    PRIMARY KEY (id_incidencia),
    CONSTRAINT fk_averia_incidencia FOREIGN KEY (id_incidencia) REFERENCES incidencia (id_incidencia) ON DELETE CASCADE,
    CONSTRAINT fk_averia_vehiculo FOREIGN KEY (id_vehiculo) REFERENCES vehiculo (id_vehiculo),
    CONSTRAINT chk_averia_tipo CHECK (tipo_averia IN ('TIPO1_MENOR', 'TIPO2_INTERMEDIA', 'TIPO3_MAYOR'))
);

CREATE TABLE simulacion (
    id_simulacion       BIGINT NOT NULL AUTO_INCREMENT,
    escenario           VARCHAR(30) NOT NULL,
    algoritmo           VARCHAR(30) NOT NULL,
    semilla_aleatoria   BIGINT,
    fecha_inicio        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    fecha_fin           DATETIME(6),
    tiempo_ejecucion_ms BIGINT,
    PRIMARY KEY (id_simulacion),
    CONSTRAINT chk_simulacion_escenario CHECK (escenario IN ('OPERACION_DIARIA', 'SIMULACION_5D', 'COLAPSO_LOGISTICO')),
    CONSTRAINT chk_simulacion_algoritmo CHECK (algoritmo IN ('GENETICO', 'RECOCIDO_SIMULADO'))
);

CREATE TABLE solucion (
    id_solucion            BIGINT NOT NULL AUTO_INCREMENT,
    id_simulacion          BIGINT NOT NULL,
    valor_funcion_objetivo DECIMAL(14,4) NOT NULL,
    costo_transporte       DECIMAL(14,4) NOT NULL,
    valor_r                DECIMAL(14,4) NOT NULL,   -- R(S) retraso
    valor_u                DECIMAL(14,4) NOT NULL,   -- U(S) penalización prioritarios
    valor_v                DECIMAL(14,4) NOT NULL,   -- V(S) penalización factibilidad
    lambda1                DECIMAL(10,4) NOT NULL,
    lambda2                DECIMAL(10,4) NOT NULL,
    lambda3                DECIMAL(10,4) NOT NULL,
    es_factible            BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_generacion       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_solucion),
    CONSTRAINT fk_solucion_simulacion FOREIGN KEY (id_simulacion) REFERENCES simulacion (id_simulacion)
);

CREATE INDEX idx_solucion_simulacion ON solucion (id_simulacion);

CREATE TABLE ruta (
    id_ruta             BIGINT NOT NULL AUTO_INCREMENT,
    id_solucion         BIGINT NOT NULL,
    id_vehiculo         VARCHAR(20) NOT NULL,
    id_almacen_origen   VARCHAR(20) NOT NULL,
    distancia_total_km  DECIMAL(8,2) NOT NULL DEFAULT 0,
    costo_total         DECIMAL(10,2) NOT NULL DEFAULT 0,
    hora_inicio         DATETIME(6),
    PRIMARY KEY (id_ruta),
    CONSTRAINT fk_ruta_solucion FOREIGN KEY (id_solucion) REFERENCES solucion (id_solucion) ON DELETE CASCADE,
    CONSTRAINT fk_ruta_vehiculo FOREIGN KEY (id_vehiculo) REFERENCES vehiculo (id_vehiculo),
    CONSTRAINT fk_ruta_almacen FOREIGN KEY (id_almacen_origen) REFERENCES almacen (id_almacen)
);

CREATE TABLE ruta_pedido (
    id_ruta_pedido        BIGINT NOT NULL AUTO_INCREMENT,
    id_ruta               BIGINT NOT NULL,
    id_pedido             BIGINT NOT NULL,
    orden_visita          INT NOT NULL,
    hora_entrega_estimada DATETIME(6),
    PRIMARY KEY (id_ruta_pedido),
    CONSTRAINT fk_ruta_pedido_ruta FOREIGN KEY (id_ruta) REFERENCES ruta (id_ruta) ON DELETE CASCADE,
    CONSTRAINT fk_ruta_pedido_pedido FOREIGN KEY (id_pedido) REFERENCES pedido (id_pedido),
    CONSTRAINT uq_ruta_orden UNIQUE (id_ruta, orden_visita)
);

CREATE INDEX idx_ruta_pedido_pedido ON ruta_pedido (id_pedido);

CREATE TABLE metrica_resultado (
    id_metrica                      BIGINT NOT NULL AUTO_INCREMENT,
    id_simulacion                   BIGINT NOT NULL,
    tasa_cumplimiento_plazos        DECIMAL(5,2),
    tasa_cumplimiento_prioritarios  DECIMAL(5,2),
    retraso_total_horas             DECIMAL(10,2),
    costo_total_transporte          DECIMAL(14,2),
    distancia_total_km              DECIMAL(12,2),
    nivel_utilizacion_flota         DECIMAL(5,2),
    tasa_replanificacion_exitosa    DECIMAL(5,2),
    tiempo_ejecucion_ms             BIGINT,
    tiempo_replanificacion_ms       BIGINT,
    fecha_calculo                   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_metrica),
    CONSTRAINT fk_metrica_simulacion FOREIGN KEY (id_simulacion) REFERENCES simulacion (id_simulacion) ON DELETE CASCADE
);

CREATE TABLE parametros_semaforo (
    id                      SMALLINT NOT NULL DEFAULT 1,
    umbral_verde_horas      DECIMAL(5,2) NOT NULL DEFAULT 18.0,
    umbral_ambar_horas      DECIMAL(5,2) NOT NULL DEFAULT 4.0,
    PRIMARY KEY (id),
    CONSTRAINT chk_una_sola_fila CHECK (id = 1),
    CONSTRAINT chk_umbrales_validos CHECK (umbral_ambar_horas < umbral_verde_horas)
);


-- Datos iniciales

INSERT INTO ciudad (nombre, ancho_km, alto_km) VALUES ('SISRAP City', 70, 50);

INSERT INTO almacen (id_almacen, nombre, tipo, ubicacion_x, ubicacion_y, capacidad_maxima, stock_actual, hora_recarga)
VALUES
    ('ALM-CENTRAL',  'Central',    'CENTRAL',    25, 15, NULL, NULL, NULL),
    ('ALM-NOROESTE', 'Nor-Oeste',  'INTERMEDIO', 12, 38, 1000, 1000, '23:59:59'),
    ('ALM-ESTE',     'Este',       'INTERMEDIO', 55, 27, 1000, 1000, '23:59:59');

INSERT INTO turno (hora_inicio, hora_fin) VALUES
    ('07:00:00', '15:00:00'),
    ('15:00:00', '23:00:00'),
    ('23:00:00', '07:00:00');

INSERT INTO parametros_semaforo (id, umbral_verde_horas, umbral_ambar_horas)
VALUES (1, 18.0, 4.0);

