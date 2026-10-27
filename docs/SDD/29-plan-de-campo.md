# 29 · Plan de datos de campo

La ST-GNN solo demuestra la arquitectura con datos sintéticos. Este documento
fija lo que falta para pasar a mediciones reales y apunta a los artefactos
versionados en el repositorio.

## 29.1 Estado actual

- `lagunapaca_gnn/field_data.py` ya carga un CSV largo
  (`timestamp,node_id,ph,turbidity_ntu,temp_c,tds_ppm`) y lo convierte en la
  serie horaria `[estaciones, horas, variables]` que consume el modelo. Cubierto
  por 19 tests.
- `train_stgnn.py --csv ... --max-gap-hours N --save ...` entrena y guarda el
  artefacto con `data_source = field` y una huella SHA-256 del contenido.
- Las coordenadas de `PROVISIONAL_STATIONS` en `stations.py` **no son medidas**:
  son propuestas derivadas de OpenStreetMap. Deben reemplazarse antes de
  reportar resultados.
- No existe ninguna campaña real ni ningún CSV de campo versionado.

## 29.2 Artefactos entregados en este sprint

| Archivo | Contenido |
|---|---|
| `ia-model/datos/plantilla_campo.csv` | Solo la cabecera del CSV. |
| `ia-model/datos/ejemplo_campo.csv` | 30 h × 4 estaciones con valores constantes. **No es una medición real.** Sirve para probar el flujo. |
| `ia-model/datos/README.md` | Reglas del CSV y advertencia sobre datos sintéticos. |
| `ia-model/CAMPO.md` | Plan de muestreo, calibración de sensores y flujo de entrenamiento. |

## 29.3 Lo que sigue pendiente

- La campaña real (≥ 4 semanas, 4 estaciones).
- Reemplazar `PROVISIONAL_STATIONS` por coordenadas medidas.
- Regenerar el contorno con `backend/tools/fetch_laguna.py`.
- Entrenar `artifacts/stgnn-field.pt` y evaluar R² a 6 h contra la persistencia.

## 29.4 Decisiones que no se deshacen

- El veredicto ECA-Agua sigue siendo del backend; el modelo no lo evalúa.
- La zona horaria del CSV es obligatoria (sin offset, el cargador se niega).
- Por defecto no se interpola ningún hueco; si se permite, queda registrado.
- El artefacto de campo lleva `data_source = field` y una huella del contenido,
  para que dos campañas distintas no se pisen.
