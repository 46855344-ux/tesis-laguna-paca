-- V3: motivos de alerta estructurados (código estable + mensaje legible).
ALTER TABLE alert_reason RENAME COLUMN reason TO message;

ALTER TABLE alert_reason ADD COLUMN code VARCHAR(20);

-- Backfill best-effort para datos ya existentes (V2). Revisar los no clasificados.
UPDATE alert_reason
SET code = CASE
               WHEN message LIKE '%superior%' THEN 'PH_HIGH'
               WHEN message LIKE '%inferior%' THEN 'PH_LOW'
               ELSE 'PH_HIGH'
    END
WHERE code IS NULL;

ALTER TABLE alert_reason ALTER COLUMN code SET NOT NULL;

-- Un mismo código no puede repetirse dentro de una muestra.
ALTER TABLE alert_reason DROP CONSTRAINT uq_alert_reason;
ALTER TABLE alert_reason ADD CONSTRAINT uq_alert_reason UNIQUE (sample_id, code);
