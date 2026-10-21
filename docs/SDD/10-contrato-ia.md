# 10 · Contrato del servicio de IA

## 10.1 Rol

Servicio **interno** FastAPI que carga un artefacto del ST-GNN y devuelve
predicciones de pH. No aplica reglas ECA-Agua, no escribe en la base y no es
accesible desde internet: en Compose solo lo alcanza el backend por la red
interna.

## 10.2 Endpoints

### GET `/health`

Público (para el healthcheck del contenedor). No revela pesos.

```json
{
  "status": "ok",
  "model_loaded": true,
  "model_version": "stgnn-synthetic-s42-i24-e150",
  "horizons": [1, 3, 6],
  "input_len": 24,
  "data_source": "synthetic",
  "detail": null
}
```

Sin artefacto: `status = degraded`, `model_loaded = false` y `detail` con el
motivo. Sigue respondiendo 200 para que el orquestador distinga «proceso vivo,
modelo ausente» de «proceso caído».

### GET `/model`

Requiere `X-Internal-Token` si el token está configurado. Devuelve metadatos sin
pesos: versión, fecha, estaciones, variables, `input_len`, horizontes y métricas.

### POST `/predict`

Requiere `X-Internal-Token`.

```json
{
  "horizon_hours": 6,
  "base_time": "2026-10-20T10:00:00Z",
  "input": [[[7.2, 15.4, 18.5, 320.0], "…24 filas…"]]
}
```

- `input` tiene forma `[4 estaciones][T][4 variables]`, en el orden
  `ph, turbidity_ntu, temp_c, tds_ppm`.
- `T` debe ser exactamente `input_len` del artefacto (24 hoy).
- `base_time` debe incluir zona horaria.
- `horizon_hours` debe estar entre los horizontes entrenados.

Respuesta:

```json
{
  "model_version": "stgnn-synthetic-s42-i24-e150",
  "data_source": "synthetic",
  "horizon_hours": 6,
  "generated_at": "2026-10-20T10:00:01Z",
  "target_at": "2026-10-20T16:00:00Z",
  "predictions": [
    {"station": "N1", "ph": 7.31},
    {"station": "N2", "ph": 7.28},
    {"station": "N3", "ph": 7.35},
    {"station": "N4", "ph": 7.30}
  ]
}
```

## 10.3 Validaciones

| Validación | Respuesta |
|---|---|
| Modelo no cargado | 503 |
| Horizonte no entrenado | 422 |
| Forma distinta de `[4][24][4]` | 422 |
| `base_time` sin zona | 422 |
| `NaN` o infinito | 422 (el manejador serializa el error sin romperse) |
| Token ausente o incorrecto | 401 |
| Inferencia con valores no finitos | 500 (no se devuelve un número inventado) |

## 10.4 Transformación de la entrada

1. Se estandariza la ventana con el `StandardScaler` guardado en el artefacto
   (media/desviación calculadas **solo con entrenamiento**).
2. Se construye el tensor `(1, 4, 24, 4)`.
3. Se ejecuta el modelo en `eval()` y `torch.no_grad()`, serializado con un lock
   para que dos peticiones no se pisen el modo.
4. La salida `(1, 4, horizonte)` está en unidades estandarizadas; se toma el
   último paso y se invierte con `inverse_targets` para volver a pH.
5. Se devuelve una predicción por estación.

**Por qué el escalador viaja en el artefacto:** sin él, los pesos serían
inútiles; con una media distinta, la inferencia sería silenciosamente
incorrecta. Del mismo modo, la adyacencia del grafo viaja en el artefacto: el
GCN y la atención dependen de ella.

## 10.5 Artefacto

- Formato versionado (`FORMAT_VERSION = 1`); una versión distinta falla de forma
  explícita.
- Contenido: configuración, `state_dict`, escalador, `edge_index`,
  `edge_weight`, métricas y `data_source`.
- Generación:

```powershell
ia-model\.venv\Scripts\python.exe train_stgnn.py --horizons 1 3 6 --save artifacts\stgnn.pt
```

- Se carga una sola vez al arrancar; **no se entrena al arrancar**.
- Si no existe o está corrupto, el servicio queda degradado; el backend responde
  503 en `/predict` y el panel lo explica.

## 10.6 Datos de campo

`lagunapaca_gnn/field_data.py` convierte un CSV de campo en la serie horaria que
consume el modelo. El contrato es deliberadamente estricto: un archivo mal
alineado produciría un R² que no significa nada.

| Columna | Regla |
|---|---|
| `timestamp` | ISO 8601 **con zona horaria** (`Z` o `-05:00`); se convierte a UTC |
| `node_id` | `N1`…`N4` (los mismos del ESP32 y del backend) |
| `ph`, `turbidity_ntu`, `temp_c`, `tds_ppm` | numéricos finitos, con punto decimal |

- Varias muestras dentro de la misma hora se **promedian**; el modelo y los
  horizontes están definidos en horas.
- Se usa el rango en que **todas** las estaciones tienen datos. Fuera de él no
  se extrapola.
- `max_gap_hours=0` por defecto: cualquier hora faltante detiene la carga. Si se
  permite interpolar, el reporte indica cuántos instantes se rellenaron y el
  hueco máximo; no se oculta la decisión.
- No se evalúa ECA-Agua aquí. El veredicto sigue siendo del backend.
- El artefacto se guarda con `data_source = field` y versión
  `stgnn-field-s<semilla>-i<ventana>-e<épocas>-d<huella>`; la huella SHA-256 del
  contenido evita que dos campañas distintas se pisen.

```powershell
ia-model\.venv\Scripts\python.exe train_stgnn.py --csv datos\campo.csv --max-gap-hours 2 --horizons 1 3 6 --save artifacts\stgnn-field.pt
```

No se versiona ningún CSV de campo real. Los tests usan *fixtures*; un R²
obtenido con ellos valida el canal de carga, no la laguna.

## 10.7 Integración con Spring Boot

- `AiClient` usa `java.net.http.HttpClient`: 2 s de conexión, 15 s de lectura.
- Cabecera `X-Internal-Token` (variable `IA_INTERNAL_TOKEN`).
- Mapeo de errores:
  - sin contacto / timeout / 5xx → `AiUnavailableException` (503);
  - 4xx / JSON inválido / contrato incumplido → `AiContractException` (502).
- El backend valida antes de persistir: versión no vacía, `data_source` conocido,
  `target_at > base_time`, horizonte igual al solicitado, cuatro estaciones
  conocidas y pH finitos.

## 10.8 Versionado del modelo

`model_version` se deriva de la configuración: `stgnn-synthetic-s{seed}-i{input_len}-e{epochs}`
para sintético y `stgnn-field-s{seed}-i{input_len}-e{epochs}-d{huella}` para campo.
No incluye la fecha para que dos ejecuciones equivalentes produzcan el mismo
identificador; la fecha queda en `created_at`. Cada fila de `prediction` guarda
la versión, así que un cambio de modelo no reescribe la historia.

## 10.9 Ejecución local

```powershell
cd ia-model
.venv\Scripts\python.exe -m uvicorn serve:app --host 127.0.0.1 --port 8000
```

Sin artefacto, `/health` responde `degraded`; no se inventan predicciones.

## 10.10 Límite científico

El artefacto actual se entrenó con la serie sintética de `dataset.py`. El
contrato y la integración son reales; la precisión **no** es evidencia sobre la
laguna. El cargador de CSV de campo ya existe y está probado, pero **no hay
datos de campo recolectados**. Cuando existan, se reentrena con `--csv`, se
guarda un artefacto con `data_source = field` y el panel cambiará la etiqueta
sin tocar el backend.
