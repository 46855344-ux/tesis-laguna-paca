# ADR-002 · PostgreSQL 16 + Flyway; sin TimescaleDB ni Neo4j

**Estado:** aceptado · **Fecha:** 2026

## Contexto

Se necesitan mediciones con series temporales, alertas, atención y predicciones.
El volumen estimado es de ~140 000 muestras/año con cuatro nodos. El grafo tiene
cuatro estaciones.

## Decisión

- PostgreSQL 16 como única base.
- Flyway con migraciones versionadas y aditivas (V1…V5).
- H2 solo en tests y en el perfil `demo`.
- **No** TimescaleDB, **no** Neo4j.

## Alternativas y por qué no

- **TimescaleDB**: útil con millones de filas y agregaciones temporales. Con este
  volumen, un índice `(node_id, recorded_at DESC)` resuelve las consultas. Añadir
  la extensión complica la imagen y el respaldo sin mejorar nada medible.
- **Neo4j**: el grafo se construye determinísticamente desde una matriz de
  distancias y vive en memoria. Un motor de grafos añadiría un servicio,
  credenciales y latencia. Se justificaría con decenas de cuerpos de agua y
  relaciones heterogéneas.

## Consecuencias

- Menos servicios que operar; la demo es más simple.
- Si el proyecto crece, la migración a TimescaleDB es una hypertable sobre la
  misma tabla; el modelo del grafo ya está aislado en `graph.py`.
- H2 no valida el SQL específico de PostgreSQL: deuda DT-01.
