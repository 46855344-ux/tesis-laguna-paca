# Datos de campo — Laguna de Paca

Plan para pasar de datos **sintéticos** a mediciones **reales** del grafo ST-GNN.
Mientras no exista una campaña real, el modelo solo demuestra la arquitectura;
este documento deja listo lo que falta.

## 1. Objetivo de la campaña

Obtener una serie horaria regular de **4 estaciones** (`N1`…`N4`) con las
cuatro variables que el modelo consume (`ph`, `turbidity_ntu`, `temp_c`,
`tds_ppm`), durante **al menos 4 semanas continuas**, para poder:

- armar ventanas de entrada de 24 h y evaluar a 1, 3 y 6 h;
- comparar R²/RMSE/MAE del ST-GNN contra la persistencia con datos reales;
- guardar el artefacto con `data_source = field` y su huella SHA-256.

## 2. Ubicación de las estaciones

Las coordenadas actuales (`PROVISIONAL_STATIONS` en `lagunapaca_gnn/stations.py`)
son **propuestas** derivadas del polígono del lago en OpenStreetMap (extremos
norte/este/sur/oeste desplazados un 5 % hacia el centroide, verificadas con
*point-in-polygon*). **No son puntos medidos.**

Antes de reportar resultados:

1. Elegir los cuatro puntos de muestreo reales (criterio: uno por extremo del
   lago, dentro del agua, con acceso para mantenimiento y panel solar).
2. Medir latitud/longitud con GPS (precisión ≤ 5 m) y anotarlas.
3. Reemplazar `PROVISIONAL_STATIONS` por `FieldStation` o renombrar la tupla a
   `STATIONS` con las coordenadas reales.
4. Regenerar el contorno y las coordenadas del panel y de la app:
   `ia-model\.venv\Scripts\python.exe backend\tools\fetch_laguna.py`.
5. Reentrenar el modelo con el CSV real (sección 5).

El grafo (`graph.py`) recibe las distancias como parámetro, así que el cambio
de coordenadas no toca la lógica del grafo: solo la matriz de entrada.

## 3. Frecuencia y duración

- Muestreo cada **15 min** (configurado en `SAMPLE_INTERVAL_SECONDS = 900` del
  firmware), que se remuestrea a **1 h** por promedio en `field_data.py`.
- Duración mínima: **4 semanas** (672 h) para tener ~648 ventanas de
  entrenamiento con `input_length = 24`.
- Si el nodo se cae, anotar el corte; el cargador permite interpolación hasta
  `--max-gap-hours N`, pero queda registrado en el reporte.

## 4. Calibración de sensores (laboratorio)

Los valores de `config.h` son nominales; hay que medirlos con el montaje
definitivo. Procedimiento:

### pH (PH-4502C)
- Soluciones buffer pH 4.01, 7.00 y 10.01 a 25 °C.
- Dos puntos: pH 4.01 y 7.00 (o 7.00 y 10.01 según el rango esperado, 6.5–9.0).
- Ajustar `PH_VOLTAGE_AT_4`, `PH_VOLTAGE_AT_7`, `PH_VOLTAGE_AT_10` en
  `config.h` con el voltaje medido en cada buffer.
- Verificar con el tercer buffer que el error sea < 0.1 pH.

### Temperatura (DS18B20)
- Baño de hielo (0 °C) y agua a temperatura ambiente medida con termómetro de
  referencia.
- Anotar el offset; el sensor suele estar dentro de ±0.5 °C.

### Turbidez (TSW-20M)
- Soluciones estándar de formazina (0, 10, 100, 1000 NTU) o diluciones de
  leche descremada calibradas con turbidímetro de referencia.
- El sensor se alimenta a 5 V y su salida alcanza ~4.5 V: **obligatorio**
  divisor de tensión (`TURBIDITY_DIVIDER_RATIO = 1.56`) para no dañar el ADC.
- Corregir el offset de tensión en oscuridad (0 NTU).

### TDS (Gravity TDS)
- Solución estándar de KCl 1413 µS/cm (o 1000 ppm) a 25 °C.
- El TDS depende de la temperatura: aplicar compensación
  (`TDS = TDS_25 * (1 + 0.02 * (T - 25))`).
- Verificar que el factor de conversión del módulo coincida con la sonda.

### ADC
- Los tres canales analógicos deben ir a **ADC1** (GPIO 32–39): ADC2 deja de
  funcionar con WiFi activo.
- `ADC_OVERSAMPLING = 8` reduce ruido; no bajarlo sin motivo.

## 5. Flujo de entrenamiento con datos reales

```powershell
# 1. Validar que el CSV carga sin errores (sin interpolar)
ia-model\.venv\Scripts\python.exe -c ^
  "from lagunapaca_gnn.field_data import load_field_csv; s=load_field_csv('ia-model/datos/campo.csv'); print(s.values.shape, s.rows_read, s.interpolated_slots)"

# 2. Entrenar y guardar el artefacto de campo
ia-model\.venv\Scripts\python.exe train_stgnn.py ^
  --csv ia-model\datos\campo.csv ^
  --max-gap-hours 2 ^
  --save ia-model\artifacts\stgnn-field.pt

# 3. El reporte imprime R²/RMSE/MAE del ST-GNN y de la persistencia.
#    El criterio de la tesis es R² > 0.90 a 6 h.
```

El artefacto queda con `data_source = field` y una versión del tipo
`stgnn-field-s{seed}-i{input}-e{epochs}-d{huella}`. Para que el backend lo use,
apuntar `LAGUNA_MODEL_PATH` (o `AiProperties`) a `artifacts/stgnn-field.pt`.

## 6. Lo que NO hace este documento

- No incluye mediciones reales: la primera campaña está pendiente.
- No cambia el código de entrenamiento ni el cargador: ambos ya existen y están
  probados (19 tests de `field_data`).
- No evalúa ECA-Agua: el veredicto sigue siendo del backend.

## 7. Checklist antes de reportar resultados

- [ ] Coordenadas reales en `stations.py` (no `PROVISIONAL_STATIONS`).
- [ ] Contorno y coordenadas regenerados con `fetch_laguna.py`.
- [ ] CSV con ≥ 4 semanas, sin huecos no declarados.
- [ ] Sensores calibrados y documentados (sección 4).
- [ ] Artefacto `stgnn-field.pt` entrenado y evaluado (R² > 0.90 a 6 h).
- [ ] Reporte del experimento adjunto al anexo de la tesis.
