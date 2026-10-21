# ADR-009 · Datos sintéticos y coordenadas provisionales, declarados

**Estado:** aceptado (con revisión obligatoria antes de la defensa) · **Fecha:** 2026

## Contexto

No hay datos de campo de la laguna ni coordenadas medidas con GPS. El proyecto
necesita entrenar y demostrar el sistema, pero presentar una simulación como
medición real sería una falta de honestidad académica.

## Decisión

1. Entrenar con una serie **sintética** generada por `dataset.py`, con memoria
   temporal, ciclo diario y correlación espacial.
2. Marcar el origen en todas las capas: artefacto (`data_source`), tabla
   `prediction`, API y panel («Datos sintéticos»).
3. Usar coordenadas **provisionales** derivadas de OpenStreetMap, marcadas como
   tales en `stations.py`.
4. Reportar las métricas como validación de la **arquitectura**, no de la laguna.
5. Documentar la limitación en `26-limitaciones.md` y en el registro de
   evidencias.

## Alternativas

- **No entrenar hasta tener datos**: retrasa toda la integración y no permite
  demostrar el criterio.
- **Inventar datos «realistas» y no decirlo**: inaceptable.
- **Usar datos públicos de otra laguna**: el modelo aprendería otra dinámica y
  sería aún más engañoso.
- **Coordenadas al azar**: el grafo perdería realismo geográfico sin ganar
  honestidad (seguirían siendo inventadas).

## Consecuencias

- El sistema se puede probar de extremo a extremo hoy.
- El R² = 0.9289 no se puede citar como precisión real.
- Cuando existan datos de campo: reentrenar, guardar artefacto `field`, medir
  coordenadas, reejecutar `fetch_laguna.py` y actualizar las tablas.
- El panel ya distingue ambos orígenes sin cambios de código.
- La etiqueta «sintético» es un requisito (RF-21), no un detalle de interfaz.
