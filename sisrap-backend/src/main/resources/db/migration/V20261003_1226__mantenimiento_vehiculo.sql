-- mantenimiento_vehiculo
ALTER TABLE vehiculo
    DROP CHECK chk_vehiculo_estado;

ALTER TABLE vehiculo
    ADD CONSTRAINT chk_vehiculo_estado
    CHECK (estado IN (
        'DISPONIBLE',
        'EN_RUTA',
        'EN_AVERIA',
        'EN_MANTENIMIENTO',
        'RETORNANDO_ALMACEN'
    ));

CREATE TABLE mantenimiento_vehiculo (
    id_mantenimiento BIGINT NOT NULL AUTO_INCREMENT,
    id_vehiculo VARCHAR(20) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    fecha_inicio DATETIME(6) NOT NULL,
    fecha_fin DATETIME(6) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id_mantenimiento),
    CONSTRAINT fk_mantenimiento_vehiculo
        FOREIGN KEY (id_vehiculo)
        REFERENCES vehiculo(id_vehiculo),
    CONSTRAINT chk_mantenimiento_tipo
        CHECK (tipo IN ('PREVENTIVO', 'CORRECTIVO')),
    CONSTRAINT chk_mantenimiento_fechas
        CHECK (fecha_fin > fecha_inicio)
);

CREATE INDEX idx_mantenimiento_vehiculo_intervalo
    ON mantenimiento_vehiculo(id_vehiculo, activo, fecha_inicio, fecha_fin);
