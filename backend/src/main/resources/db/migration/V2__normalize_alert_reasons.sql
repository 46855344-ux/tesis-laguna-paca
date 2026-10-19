-- V2: normaliza los motivos de alerta en su propia tabla (1:N), en lugar de
-- una columna de texto con separador en water_quality_sample.
CREATE TABLE alert_reason
(
    sample_id BIGINT       NOT NULL,
    reason    VARCHAR(255) NOT NULL,
    CONSTRAINT fk_alert_reason_sample
        FOREIGN KEY (sample_id) REFERENCES water_quality_sample (id) ON DELETE CASCADE,
    CONSTRAINT uq_alert_reason UNIQUE (sample_id, reason)
);

CREATE INDEX idx_alert_reason_sample ON alert_reason (sample_id);

ALTER TABLE water_quality_sample DROP COLUMN alert_reasons;
