-- V4: atención de las alertas ECA-Agua desde el panel.
--
-- No toca la columna alert: el veredicto lo sigue decidiendo el backend al
-- ingerir y no se reescribe. Estas tres columnas registran la respuesta humana
-- ("ya me ocupé de esta muestra"), que es una dimensión distinta.
ALTER TABLE water_quality_sample
    ADD COLUMN acknowledged_at  TIMESTAMPTZ,
    ADD COLUMN acknowledged_by  VARCHAR(120),
    ADD COLUMN acknowledge_note VARCHAR(500);

-- El panel lista sobre todo alertas pendientes; este índice evita recorrer la
-- tabla completa cuando el histórico crezca.
CREATE INDEX idx_wqs_alert_acknowledged_at
    ON water_quality_sample (alert, acknowledged_at);
