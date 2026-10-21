# ADR-005 · Predicciones en tabla propia y evaluación diferida

**Estado:** aceptado · **Fecha:** 2026

## Contexto

El sistema predice pH a 1, 3 y 6 h. Una predicción no es una medición: si se
guardara como una columna de `water_quality_sample`, el veredicto ECA-Agua podría
confundirse con un pronóstico y no se podría evaluar el error.

## Decisión

- Tabla `prediction` independiente, con `model_run_id`, versión, `data_source` y
  estado `PENDING`/`EVALUATED`.
- El valor previsto es **inmutable**.
- Cuando llega una medición real dentro de ±30 min del `target_at`, se guardan
  `observed_value`, `error` y `evaluated_at`.
- La evaluación corre en `REQUIRES_NEW` y nunca rompe la ingesta.
- El panel muestra las predicciones en una sección aparte; no cambian el badge de
  la estación.

## Alternativas

- **Columna `predicted_ph` en la muestra**: mezcla observado y previsto, impide
  evaluar varias predicciones y contamina las consultas ambientales.
- **No persistir y evaluar en memoria**: se pierde la evidencia y no se puede
  auditar el error.
- **Evaluar con una ventana mayor**: ±30 min es suficiente para la cadencia de
  15 min y evita emparejar una predicción con una muestra de otra hora.

## Consecuencias

- Se puede medir el error real del modelo con el tiempo.
- Una predicción sin muestra queda `PENDING`, que es información honesta.
- La evaluación es un efecto secundario best-effort: si falla, la muestra se
  guarda igual.
- La API no expone la atención de alertas; la predicción sí se expone porque es
  el objeto de la tesis.
