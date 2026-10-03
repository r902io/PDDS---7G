-- alter_simulacion
ALTER TABLE simulacion
    DROP CHECK chk_simulacion_escenario;

ALTER TABLE simulacion
    ADD CONSTRAINT chk_simulacion_escenario
    CHECK (escenario IN (
        'OPERACION_DIARIA',
        'SIMULACION_5D',
        'COLAPSO_LOGISTICO',
        'OPERACION_TIEMPO_REAL'
    ));

ALTER TABLE simulacion
    ADD COLUMN fecha_fin_programada DATETIME(6) NULL AFTER fecha_inicio,
    ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'FINALIZADA'
        AFTER fecha_fin_programada,
    ADD COLUMN reloj_simulado DATETIME(6) NULL AFTER estado,
    ADD COLUMN velocidad_simulacion DECIMAL(10,2)
        NOT NULL DEFAULT 1.00 AFTER reloj_simulado,
    ADD COLUMN perfil VARCHAR(60)
        NOT NULL DEFAULT 'BASE' AFTER velocidad_simulacion;

ALTER TABLE simulacion
    ADD CONSTRAINT chk_simulacion_estado
    CHECK (
        estado IN (
            'DETENIDA',
            'EJECUTANDO',
            'PAUSADA',
            'FINALIZADA',
            'ERROR'
        )
    );

CREATE INDEX idx_simulacion_estado
    ON simulacion (estado);