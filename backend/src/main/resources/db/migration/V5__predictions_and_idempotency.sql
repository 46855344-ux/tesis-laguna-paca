-- ---------------------------------------------------------------------------
-- V5: idempotencia de la ingesta y predicciones del ST-GNN.
--
-- Aditiva a propósito: no modifica ni borra columnas existentes. La tabla
-- water_quality_sample conserva su historial y las predicciones viven en una
-- tabla aparte (RN-06: nunca se mezclan mediciones observadas con valores
-- previstos).
-- ---------------------------------------------------------------------------

-- RN-09: el nodo puede reintentar un POST o recibir un duplicado por QoS 1.
-- El identificador es opcional para no romper a los clientes actuales.
ALTER TABLE water_quality_sample ADD COLUMN sample_id VARCHAR(64);

-- Índice parcial: PostgreSQL permite varios NULL, así que las muestras
-- históricas (sin sample_id) no colisionan entre sí.
CREATE UNIQUE INDEX ux_water_quality_sample_sample_id
    ON water_quality_sample (sample_id)
    WHERE sample_id IS NOT NULL;

CREATE TABLE prediction (
    id              BIGSERIAL PRIMARY KEY,
    model_run_id    UUID             NOT NULL,
    node_id         VARCHAR(32)      NOT NULL,
    variable        VARCHAR(16)      NOT NULL DEFAULT 'ph',
    horizon_hours   INTEGER          NOT NULL,
    generated_at    TIMESTAMPTZ      NOT NULL,
    target_at       TIMESTAMPTZ      NOT NULL,
    predicted_value DOUBLE PRECISION NOT NULL,
    model_version   VARCHAR(64)      NOT NULL,
    data_source     VARCHAR(16)      NOT NULL,
    status          VARCHAR(16)      NOT NULL,
    observed_value  DOUBLE PRECISION,
    prediction_error DOUBLE PRECISION,
    evaluated_at    TIMESTAMPTZ,
    CONSTRAINT ck_prediction_horizon CHECK (horizon_hours > 0),
    CONSTRAINT ck_prediction_status  CHECK (status IN ('PENDING', 'EVALUATED')),
    CONSTRAINT ck_prediction_target  CHECK (target_at > generated_at),
    -- Una predicción evaluada tiene observación, error y fecha; una pendiente,
    -- ninguno de los tres. Evita estados a medias.
    CONSTRAINT ck_prediction_evaluation CHECK (
        (status = 'PENDING'
            AND observed_value IS NULL
            AND prediction_error IS NULL
            AND evaluated_at IS NULL)
        OR
        (status = 'EVALUATED'
            AND observed_value IS NOT NULL
            AND prediction_error IS NOT NULL
            AND evaluated_at IS NOT NULL))
);

-- Consulta de evaluación posterior: predicciones pendientes de una estación
-- cuya hora objetivo cae en un rango.
CREATE INDEX ix_prediction_status_target ON prediction (status, target_at);
CREATE INDEX ix_prediction_node_target ON prediction (node_id, target_at);
CREATE INDEX ix_prediction_generated_at ON prediction (generated_at DESC);
CREATE INDEX ix_prediction_model_run ON prediction (model_run_id);
