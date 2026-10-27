# Datos de campo — Laguna Paca

Esta carpeta contiene la **plantilla** y un **ejemplo** del formato CSV que
consume `lagunapaca_gnn.field_data.load_field_csv`. **No son mediciones reales.**

| Archivo | Para qué sirve |
|---|---|
| `plantilla_campo.csv` | Solo la cabecera. Copiarla y rellenarla con las lecturas del nodo. |
| `ejemplo_campo.csv` | 200 h × 4 estaciones con valores constantes. **No es una medición real.** Sirve para probar el flujo de extremo a extremo (`train_stgnn.py --csv ...`); al ser constante, la persistencia le gana al modelo, lo cual es esperable. |

## Reglas del CSV (no negociables)

- Cabecera exacta: `timestamp,node_id,ph,turbidity_ntu,temp_c,tds_ppm`.
- `timestamp` en ISO 8601 **con zona horaria** (`2026-10-20T08:00:00-05:00` o `...Z`).
  Sin offset el cargador se niega a alinear la serie.
- `node_id` debe ser `N1`…`N4` (el mismo que envía el ESP32).
- Decimal con **punto** (`7.21`, no `7,21`).
- Una fila por muestra; se promedian las que caen en la misma hora.
- Todas las estaciones deben solaparse en el tiempo; fuera del rango común no
  se rellena nada.
- Por defecto no se interpola ningún hueco (`--max-gap-hours 0`). Si se acepta
  interpolación, queda registrado en el reporte.

## Antes de reportar resultados

1. Reemplazar `PROVISIONAL_STATIONS` en `lagunapaca_gnn/stations.py` por las
   coordenadas reales medidas en campo.
2. Regenerar el contorno y las coordenadas del panel/app:
   `ia-model\.venv\Scripts\python.exe backend\tools\fetch_laguna.py`.
3. Entrenar con el CSV real:
   `ia-model\.venv\Scripts\python.exe train_stgnn.py --csv datos\campo.csv --max-gap-hours 2 --save artifacts\stgnn-field.pt`
4. Actualizar `AiProperties` (backend) para apuntar al artefacto de campo y
   evaluar R²/RMSE/MAE contra la persistencia, igual que con datos sintéticos.

Ver `CAMPO.md` (en la raíz de `ia-model/`) para el plan de muestreo y la
calibración de sensores.
