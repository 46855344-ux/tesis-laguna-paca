-- ---------------------------------------------------------------------------
-- V6: alinea los tipos de las mediciones con las entidades JPA.
--
-- La V1 declaró ph, turbidity_ntu, temp_c y tds_ppm como NUMERIC(x, 2), pero
-- WaterQualitySample los mapea como double. Hibernate valida el esquema al
-- arrancar (ddl-auto: validate) y rechaza NUMERIC frente a double precision,
-- así que el backend no podía arrancar contra PostgreSQL. En H2 no se veía
-- porque el perfil demo genera el esquema desde las entidades.
--
-- Aditiva a propósito: no se reescriben V1–V5 (sus checksums ya están en
-- flyway_schema_history). El cambio es sin pérdida para los valores guardados
-- (todos tienen dos decimales) y hace que PostgreSQL se comporte como la API.
-- ---------------------------------------------------------------------------
ALTER TABLE water_quality_sample
    ALTER COLUMN ph TYPE double precision,
    ALTER COLUMN turbidity_ntu TYPE double precision,
    ALTER COLUMN temp_c TYPE double precision,
    ALTER COLUMN tds_ppm TYPE double precision;
