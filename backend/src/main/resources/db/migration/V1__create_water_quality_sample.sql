-- V1: tabla de series temporales para la telemetría de los nodos sensores.
-- Refleja 1:1 el payload del ESP32:
--   {"node_id": "N1", "ph": 7.2, "turbidity_ntu": 15.4, "temp_c": 18.5, "tds_ppm": 320}
CREATE TABLE water_quality_sample
(
    id            BIGSERIAL PRIMARY KEY,
    node_id       VARCHAR(32)   NOT NULL,
    ph            NUMERIC(4, 2) NOT NULL,
    turbidity_ntu NUMERIC(8, 2) NOT NULL,
    temp_c        NUMERIC(5, 2) NOT NULL,
    tds_ppm       NUMERIC(8, 2) NOT NULL,
    alert         BOOLEAN       NOT NULL DEFAULT FALSE,
    alert_reasons VARCHAR(512),
    recorded_at   TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_wqs_node_recorded_at ON water_quality_sample (node_id, recorded_at DESC);
