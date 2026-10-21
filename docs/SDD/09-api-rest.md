# 09 · API REST

## 9.1 Convenciones

- Base: `http://localhost:8080`.
- Contrato fuente de verdad: `backend/openapi.yaml`.
- Documentación interactiva: `http://localhost:8080/swagger-ui.html`.
- Autenticación: `Authorization: Bearer <JWT>` en todos los endpoints salvo
  `/api/v1/auth/token`.
- Errores: cuerpo `ApiError` con `timestamp`, `status`, `error`, `message`,
  `path` y `field_errors`.
- Fechas: ISO-8601 en UTC (`2026-10-20T16:00:00Z`).
- Paginación: `page` (0-based), `size` (1–100, por defecto 20), `sort`.

## 9.2 Endpoints

### POST `/api/v1/auth/token`

```json
{"username":"admin","password":"admin123"}
```

Respuesta 200:

```json
{"access_token":"eyJ...","token_type":"Bearer","expires_in":7200}
```

| Código | Cuándo |
|---|---|
| 200 | Credenciales válidas |
| 401 | Credenciales inválidas |

---

### POST `/api/v1/telemetry`

```json
{
  "sample_id": "N1-3FA4C21B-17",
  "node_id": "N1",
  "ph": 7.2,
  "turbidity_ntu": 15.4,
  "temp_c": 18.5,
  "tds_ppm": 320
}
```

`sample_id` es opcional y aditivo (máx. 64). Sin él, el comportamiento es el
histórico.

| Código | Cuándo |
|---|---|
| 201 | Muestra creada |
| 200 | `sample_id` ya existía; se devuelve la original |
| 400 | Validación (pH fuera de 0–14, campos faltantes…) |
| 401 | Sin JWT o inválido |
| 409 | Choque de `sample_id` por concurrencia |

Respuesta:

```json
{
  "id": 42,
  "node_id": "N1",
  "ph": 9.7,
  "turbidity_ntu": 15.4,
  "temp_c": 18.5,
  "tds_ppm": 320,
  "alert": true,
  "alert_reasons": [
    {"code": "PH_HIGH", "message": "pH 9.70 supera el límite superior ECA-Agua (9.0)"}
  ],
  "recorded_at": "2026-10-20T16:00:00Z"
}
```

---

### GET `/api/v1/telemetry`

Parámetros: `node_id`, `from`, `to`, `alert`, `reason`, `code`
(`PH_LOW`/`PH_HIGH`), `page`, `size`, `sort` (por defecto `recordedAt,desc`).

| Código | Cuándo |
|---|---|
| 200 | Página de muestras |
| 400 | Rango inválido (`from > to`) o parámetro mal formado |
| 401 | Sin JWT o inválido |

---

### POST `/api/v1/predictions`

```json
{"horizon_hours": 6}
```

Genera una predicción para las cuatro estaciones y persiste cuatro filas
`PENDING` con el mismo `model_run_id`.

| Código | Cuándo |
|---|---|
| 201 | Cuatro predicciones persistidas |
| 400 | Horizonte fuera de 1–24 o no entrenado (2, 4, 5…) |
| 401 | Sin JWT |
| 409 | Falta historia para la ventana |
| 502 | La IA devolvió algo que no cumple el contrato |
| 503 | La IA no responde o no tiene modelo |

Respuesta (extracto):

```json
[
  {
    "id": 1,
    "model_run_id": "0f7c...",
    "node_id": "N1",
    "variable": "ph",
    "horizon_hours": 6,
    "generated_at": "2026-10-20T10:00:00Z",
    "target_at": "2026-10-20T16:00:00Z",
    "predicted_value": 7.31,
    "model_version": "stgnn-synthetic-s42-i24-e150",
    "data_source": "synthetic",
    "status": "PENDING",
    "observed_value": null,
    "error": null,
    "evaluated_at": null
  }
]
```

---

### GET `/api/v1/predictions`

Filtros `node_id`, `horizon_hours`; paginación estándar. Orden por defecto
`generatedAt,desc`.

### GET `/api/v1/predictions/latest`

Hasta 12 predicciones recientes, sin paginar (para el tablero).

### GET `/api/v1/ai/health`

Siempre 200 si el backend responde; el cuerpo dice si la IA está disponible:

```json
{
  "available": false,
  "model_version": null,
  "horizons": [],
  "input_len": null,
  "data_source": null,
  "detail": "No se pudo contactar al servicio de IA (ConnectException)",
  "checked_at": "2026-10-20T10:00:00Z"
}
```

## 9.3 Ejemplo completo

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .access_token)

curl -s -X POST http://localhost:8080/api/v1/telemetry \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sample_id":"N1-demo-1","node_id":"N1","ph":9.7,"turbidity_ntu":15.4,"temp_c":18.5,"tds_ppm":320}' | jq

curl -s -X POST http://localhost:8080/api/v1/predictions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"horizon_hours":6}' | jq
```

## 9.4 Lo que la API no expone

- La atención de alertas (`acknowledged_*`): hoy solo se gestiona en el panel,
  porque afecta a la app Android y es una decisión aparte.
- Los motivos de alerta como recurso independiente: viajan dentro de la muestra.
- El estado interno de la evaluación de predicciones.
- Datos de otros usuarios: no hay multi-tenancy.

## 9.5 Compatibilidad

- Añadir un campo opcional al payload (como `sample_id`) no rompe a los nodos
  existentes.
- Cambiar el código de una alerta sí rompería a los clientes: los códigos son
  un enum estable.
- El contrato `openapi.yaml` se actualiza en el mismo commit que el controlador.
  Swagger UI se genera de las anotaciones, pero la fuente de verdad es el YAML.
