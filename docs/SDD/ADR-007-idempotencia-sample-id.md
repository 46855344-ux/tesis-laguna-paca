# ADR-007 · Idempotencia con `sample_id` opcional

**Estado:** aceptado · **Fecha:** 2026

## Contexto

El nodo puede reintentar un POST si se pierde la respuesta, y MQTT QoS 1 puede
reentregar un mensaje. Sin idempotencia, el reintento crea una muestra duplicada
y distorsiona el histórico y el modelo.

## Decisión

- Campo opcional `sample_id` (≤64) en el payload.
- Columna `sample_id` y **índice único parcial** `WHERE sample_id IS NOT NULL`.
- Si ya existe: HTTP **200** con la muestra original, no 201.
- Si dos peticiones simultáneas chocan: **409** (el índice es la garantía de
  último recurso).
- Opcional para no romper a los clientes existentes.
- El firmware lo envía: `<nodeId>-<bootId>-<secuencia>`, con `bootId` aleatorio
  por arranque en frío y secuencia que solo avanza al confirmar la entrega.

## Alternativas

- **Obligatorio**: rompe a los nodos y al simulador actuales.
- **Sin índice, solo consulta**: dos peticiones simultáneas podrían pasar la
  comprobación y duplicar.
- **UUID generado por el servidor**: no sirve para el reintento, porque el nodo
  no conserva la respuesta.
- **Solo un contador**: tras un corte de luz se reutilizan ids y se descartan
  mediciones reales.

## Consecuencias

- El reintento es seguro y observable (200).
- La columna es nullable y el índice es parcial: el histórico sin id no colisiona.
- El firmware debe cuidar la secuencia; está probado en 6 tests.
- La API documenta ambos códigos en `openapi.yaml`.
