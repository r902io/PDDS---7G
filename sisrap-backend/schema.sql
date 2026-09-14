
DROP INDEX IF EXISTS idx_solucion_simulacion;
DROP INDEX IF EXISTS idx_incidencia_activa;
DROP INDEX IF EXISTS idx_vehiculo_estado;

DROP TABLE IF EXISTS metrica_resultado CASCADE;

DROP TABLE IF EXISTS ruta_pedido CASCADE;
DROP TABLE IF EXISTS ruta CASCADE;
DROP TABLE IF EXISTS solucion CASCADE;

DROP TABLE IF EXISTS simulacion CASCADE;

DROP TABLE IF EXISTS averia CASCADE;
DROP TABLE IF EXISTS bloqueo_nodo CASCADE;
DROP TABLE IF EXISTS bloqueo CASCADE;
DROP TABLE IF EXISTS incidencia CASCADE;

DROP TABLE IF EXISTS pedido CASCADE;

ALTER TABLE IF EXISTS vehiculo DROP CONSTRAINT IF EXISTS fk_vehiculo_conductor;
DROP TABLE IF EXISTS conductor CASCADE;
DROP TABLE IF EXISTS turno CASCADE;
DROP TABLE IF EXISTS vehiculo CASCADE;

DROP TABLE IF EXISTS almacen CASCADE;

DROP TABLE IF EXISTS via CASCADE;
DROP TABLE IF EXISTS nodo CASCADE;
DROP TABLE IF EXISTS ciudad CASCADE;

DROP TABLE IF EXISTS parametros_semaforo CASCADE;

DROP TYPE IF EXISTS tipo_algoritmo;
DROP TYPE IF EXISTS tipo_escenario;
DROP TYPE IF EXISTS tipo_incidencia;
DROP TYPE IF EXISTS tipo_averia;
DROP TYPE IF EXISTS estado_vehiculo;
DROP TYPE IF EXISTS tipo_vehiculo;
DROP TYPE IF EXISTS tipo_almacen;
DROP TYPE IF EXISTS estado_pedido;
DROP TYPE IF EXISTS tipo_prioridad;

CREATE TYPE tipo_prioridad AS ENUM (
    'REGULAR_36H',
    'PRIORIZADO_18H',
    'PRIORIZADO_12H',
    'PRIORIZADO_8H',
    'PRIORIZADO_4H'
);

CREATE TYPE estado_pedido AS ENUM (
    'PENDIENTE',
    'EN_RUTA',
    'ENTREGADO',
    'REASIGNADO',
    'RETRASADO'
);

CREATE TYPE tipo_almacen AS ENUM (
    'CENTRAL',
    'INTERMEDIO'
);

CREATE TYPE tipo_vehiculo AS ENUM (
    'AUTO',
    'MOTO',
    'BICICLETA'
);

CREATE TYPE estado_vehiculo AS ENUM (
    'DISPONIBLE',
    'EN_RUTA',
    'EN_AVERIA',
    'RETORNANDO_ALMACEN'
);

CREATE TYPE tipo_averia AS ENUM (
    'TIPO1_MENOR',
    'TIPO2_INTERMEDIA',
    'TIPO3_MAYOR'
);

CREATE TYPE tipo_incidencia AS ENUM (
    'BLOQUEO',
    'AVERIA'
);

CREATE TYPE tipo_escenario AS ENUM (
    'OPERACION_DIARIA',
    'SIMULACION_5D',
    'COLAPSO_LOGISTICO'
);

CREATE TYPE tipo_algoritmo AS ENUM (
    'GENETICO',
    'RECOCIDO_SIMULADO'
);

CREATE TABLE ciudad (
    id_ciudad      SERIAL PRIMARY KEY,
    nombre         VARCHAR(50) NOT NULL DEFAULT 'SISRAP City',
    ancho_km       INTEGER NOT NULL DEFAULT 70,
    alto_km        INTEGER NOT NULL DEFAULT 50
);

INSERT INTO ciudad (nombre, ancho_km, alto_km) VALUES ('SISRAP City', 70, 50);

CREATE TABLE nodo (
    id_nodo        SERIAL PRIMARY KEY,
    id_ciudad      INTEGER NOT NULL REFERENCES ciudad(id_ciudad),
    x              INTEGER NOT NULL CHECK (x >= 0),
    y              INTEGER NOT NULL CHECK (y >= 0),
    CONSTRAINT uq_nodo_coordenada UNIQUE (id_ciudad, x, y)
);

CREATE INDEX idx_nodo_coordenadas ON nodo (x, y);

CREATE TABLE via (
    id_via              SERIAL PRIMARY KEY,
    id_ciudad           INTEGER NOT NULL REFERENCES ciudad(id_ciudad),
    origen_x            INTEGER NOT NULL,
    origen_y            INTEGER NOT NULL,
    destino_x           INTEGER NOT NULL,
    destino_y           INTEGER NOT NULL,
    distancia_km        NUMERIC(5,2) NOT NULL DEFAULT 1.00,
    transitable         BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_via_adyacente CHECK (
        (ABS(origen_x - destino_x) = 1 AND origen_y = destino_y) OR
        (ABS(origen_y - destino_y) = 1 AND origen_x = destino_x)
    )
);

CREATE INDEX idx_via_origen ON via (origen_x, origen_y);
CREATE INDEX idx_via_destino ON via (destino_x, destino_y);

CREATE TABLE almacen (
    id_almacen          VARCHAR(20) PRIMARY KEY,
    nombre              VARCHAR(50) NOT NULL,
    tipo                tipo_almacen NOT NULL,
    ubicacion_x         INTEGER NOT NULL,
    ubicacion_y         INTEGER NOT NULL,
    capacidad_maxima    INTEGER,
    stock_actual        INTEGER,
    hora_recarga        TIME,
    CONSTRAINT chk_almacen_capacidad CHECK (
        (tipo = 'CENTRAL' AND capacidad_maxima IS NULL) OR
        (tipo = 'INTERMEDIO' AND capacidad_maxima IS NOT NULL)
    )
);

INSERT INTO almacen (id_almacen, nombre, tipo, ubicacion_x, ubicacion_y, capacidad_maxima, stock_actual, hora_recarga)
VALUES
    ('ALM-CENTRAL',  'Central',    'CENTRAL',    25, 15, NULL, NULL, NULL),
    ('ALM-NOROESTE', 'Nor-Oeste',  'INTERMEDIO', 12, 38, 1000, 1000, '23:59:59'),
    ('ALM-ESTE',     'Este',       'INTERMEDIO', 55, 27, 1000, 1000, '23:59:59');

CREATE TABLE vehiculo (
    id_vehiculo         VARCHAR(20) PRIMARY KEY,
    tipo                tipo_vehiculo NOT NULL,
    capacidad_paquetes  INTEGER NOT NULL,
    velocidad_kmh       NUMERIC(5,2) NOT NULL,
    costo_por_km        NUMERIC(6,2) NOT NULL,
    estado              estado_vehiculo NOT NULL DEFAULT 'DISPONIBLE',
    posicion_x          INTEGER NOT NULL DEFAULT 25,
    posicion_y          INTEGER NOT NULL DEFAULT 15,
    id_conductor_actual VARCHAR(20)
);

CREATE TABLE turno (
    id_turno            SERIAL PRIMARY KEY,
    hora_inicio         TIME NOT NULL,
    hora_fin            TIME NOT NULL,
    hora_alimentacion    TIME
);

INSERT INTO turno (hora_inicio, hora_fin) VALUES
    ('07:00:00', '15:00:00'),
    ('15:00:00', '23:00:00'),
    ('23:00:00', '07:00:00');

CREATE TABLE conductor (
    id_conductor        VARCHAR(20) PRIMARY KEY,
    nombre              VARCHAR(100) NOT NULL,
    id_turno_asignado   INTEGER NOT NULL REFERENCES turno(id_turno)
);

ALTER TABLE vehiculo
    ADD CONSTRAINT fk_vehiculo_conductor
    FOREIGN KEY (id_conductor_actual) REFERENCES conductor(id_conductor);

CREATE TABLE pedido (
    id_pedido           BIGSERIAL PRIMARY KEY,
    id_cliente          VARCHAR(20),
    cantidad_qq         INTEGER NOT NULL CHECK (cantidad_qq > 0),
    prioridad           tipo_prioridad NOT NULL,
    horas_limite        INTEGER NOT NULL,
    fecha_llegada       TIMESTAMP NOT NULL,
    fecha_entrega_real  TIMESTAMP,
    estado              estado_pedido NOT NULL DEFAULT 'PENDIENTE',
    ubicacion_x         INTEGER NOT NULL,
    ubicacion_y         INTEGER NOT NULL
);

CREATE INDEX idx_pedido_estado ON pedido (estado);
CREATE INDEX idx_pedido_id_cliente ON pedido (id_cliente);
CREATE INDEX idx_pedido_fecha_llegada ON pedido (fecha_llegada);
CREATE INDEX idx_pedido_ubicacion ON pedido (ubicacion_x, ubicacion_y);

CREATE TABLE incidencia (
    id_incidencia       BIGSERIAL PRIMARY KEY,
    tipo                tipo_incidencia NOT NULL,
    fecha_ocurrencia    TIMESTAMP NOT NULL,
    activa              BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE bloqueo (
    id_incidencia       BIGINT PRIMARY KEY REFERENCES incidencia(id_incidencia) ON DELETE CASCADE,
    fecha_inicio        TIMESTAMP NOT NULL,
    fecha_fin           TIMESTAMP NOT NULL
);

CREATE TABLE bloqueo_nodo (
    id_bloqueo_nodo     BIGSERIAL PRIMARY KEY,
    id_incidencia       BIGINT NOT NULL REFERENCES bloqueo(id_incidencia) ON DELETE CASCADE,
    orden               INTEGER NOT NULL,
    x                   INTEGER NOT NULL,
    y                   INTEGER NOT NULL,
    CONSTRAINT uq_bloqueo_orden UNIQUE (id_incidencia, orden)
);

CREATE INDEX idx_bloqueo_nodo_coordenadas ON bloqueo_nodo (x, y);

CREATE TABLE averia (
    id_incidencia       BIGINT PRIMARY KEY REFERENCES incidencia(id_incidencia) ON DELETE CASCADE,
    id_vehiculo         VARCHAR(20) NOT NULL REFERENCES vehiculo(id_vehiculo),
    tipo_averia         tipo_averia NOT NULL,
    ubicacion_x         INTEGER NOT NULL,
    ubicacion_y         INTEGER NOT NULL,
    hora_retorno_estimada TIMESTAMP
);

CREATE TABLE simulacion (
    id_simulacion       BIGSERIAL PRIMARY KEY,
    escenario           tipo_escenario NOT NULL,
    algoritmo           tipo_algoritmo NOT NULL,
    semilla_aleatoria   BIGINT,
    fecha_inicio        TIMESTAMP NOT NULL DEFAULT now(),
    fecha_fin           TIMESTAMP,
    tiempo_ejecucion_ms BIGINT
);

CREATE TABLE solucion (
    id_solucion         BIGSERIAL PRIMARY KEY,
    id_simulacion       BIGINT NOT NULL REFERENCES simulacion(id_simulacion),
    valor_funcion_objetivo NUMERIC(14,4) NOT NULL,
    costo_transporte    NUMERIC(14,4) NOT NULL,
    valor_r             NUMERIC(14,4) NOT NULL,    -- R(S) retraso
    valor_u             NUMERIC(14,4) NOT NULL,    -- U(S) penalización prioritarios
    valor_v             NUMERIC(14,4) NOT NULL,    -- V(S) penalización factibilidad
    lambda1             NUMERIC(10,4) NOT NULL,
    lambda2             NUMERIC(10,4) NOT NULL,
    lambda3             NUMERIC(10,4) NOT NULL,
    es_factible         BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_generacion    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE ruta (
    id_ruta             BIGSERIAL PRIMARY KEY,
    id_solucion         BIGINT NOT NULL REFERENCES solucion(id_solucion) ON DELETE CASCADE,
    id_vehiculo         VARCHAR(20) NOT NULL REFERENCES vehiculo(id_vehiculo),
    id_almacen_origen   VARCHAR(20) NOT NULL REFERENCES almacen(id_almacen),
    distancia_total_km  NUMERIC(8,2) NOT NULL DEFAULT 0,
    costo_total         NUMERIC(10,2) NOT NULL DEFAULT 0,
    hora_inicio         TIMESTAMP
);

CREATE TABLE ruta_pedido (
    id_ruta_pedido      BIGSERIAL PRIMARY KEY,
    id_ruta             BIGINT NOT NULL REFERENCES ruta(id_ruta) ON DELETE CASCADE,
    id_pedido           BIGINT NOT NULL REFERENCES pedido(id_pedido),
    orden_visita        INTEGER NOT NULL,
    hora_entrega_estimada TIMESTAMP,
    CONSTRAINT uq_ruta_orden UNIQUE (id_ruta, orden_visita)
);

CREATE INDEX idx_ruta_pedido_pedido ON ruta_pedido (id_pedido);

CREATE TABLE metrica_resultado (
    id_metrica                      BIGSERIAL PRIMARY KEY,
    id_simulacion                   BIGINT NOT NULL REFERENCES simulacion(id_simulacion) ON DELETE CASCADE,
    tasa_cumplimiento_plazos        NUMERIC(5,2),
    tasa_cumplimiento_prioritarios  NUMERIC(5,2),
    retraso_total_horas             NUMERIC(10,2),
    costo_total_transporte          NUMERIC(14,2),
    distancia_total_km              NUMERIC(12,2),
    nivel_utilizacion_flota         NUMERIC(5,2),
    tasa_replanificacion_exitosa    NUMERIC(5,2),
    tiempo_ejecucion_ms             BIGINT,
    tiempo_replanificacion_ms       BIGINT,
    fecha_calculo                   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE parametros_semaforo (
    id                      SMALLINT PRIMARY KEY DEFAULT 1,
    umbral_verde_horas      NUMERIC(5,2) NOT NULL DEFAULT 18.0,
    umbral_ambar_horas      NUMERIC(5,2) NOT NULL DEFAULT 4.0,
    CONSTRAINT chk_una_sola_fila CHECK (id = 1),
    CONSTRAINT chk_umbrales_validos CHECK (umbral_ambar_horas < umbral_verde_horas)
);

INSERT INTO parametros_semaforo (id, umbral_verde_horas, umbral_ambar_horas)
VALUES (1, 18.0, 4.0);

CREATE INDEX idx_vehiculo_estado ON vehiculo (estado);
CREATE INDEX idx_incidencia_activa ON incidencia (activa);
CREATE INDEX idx_solucion_simulacion ON solucion (id_simulacion);
