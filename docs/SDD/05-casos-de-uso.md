# 05 · Casos de uso

Los casos de uso se numeran **CU-xx**. Cada uno indica actor, precondición,
flujo principal, alternativas y las pruebas que lo cubren.

---

## CU-01 · Registrar una muestra por HTTP

**Actor:** nodo ESP32 (o simulador).
**Precondición:** el nodo tiene un JWT válido.

1. El nodo hace `POST /api/v1/telemetry` con `sample_id`, `node_id`, pH,
   turbidez, temperatura y TDS.
2. El backend valida el payload (`@Valid`).
3. Si el `sample_id` ya existe, devuelve **200** con la muestra original y
   termina.
4. Evalúa los límites ECA-Agua de pH.
5. Persiste la muestra y sus motivos con `recorded_at` del servidor.
6. Dispara, sin bloquear, la evaluación de predicciones pendientes de esa
   estación dentro de ±30 min.
7. Responde **201** con `alert` y `alert_reasons`.

**Alternativas:** payload inválido → 400; sin token → 401; choque de
`sample_id` por concurrencia → 409; la evaluación de predicciones falla → se
registra un `WARN` y la muestra queda guardada igual.

**Pruebas:** `ApiIntegrationTest`, `TelemetryControllerTest`,
`TelemetryServiceTest`, `PredictionIntegrationTest`.

---

## CU-02 · Registrar una muestra por MQTT

**Actor:** nodo ESP32.
**Precondición:** `MQTT_ENABLED=true` y el broker acepta las credenciales.

1. El nodo publica en `laguna-paca/telemetry` con QoS 1.
2. El broker entrega al backend; el `handler` parsea y valida el payload.
3. Se ejecuta el mismo caso de uso que CU-01 (sin JWT).
4. El nodo considera entregada la muestra cuando recibe el PUBACK.

**Alternativas:** JSON inválido o payload que no valida → se descarta con
`WARN`, sin detener el consumidor; duplicado por reentrega QoS 1 → 200
idempotente.

**Pruebas:** `TelemetryMqttHandlerTest` (sin broker).

---

## CU-03 · Consultar el histórico

**Actor:** operador.
**Precondición:** sesión iniciada en el panel (o JWT para la API).

1. El operador entra a `/panel/historial`.
2. Elige estación, desde, hasta, «solo alertas» o texto del motivo.
3. El panel parsea las fechas en hora de Lima y llama a `TelemetryService.search`.
4. Muestra 20 filas por página, de la más reciente a la más antigua.
5. Al paginar, conserva los filtros en la URL.

**Alternativas:** fecha inválida → aviso y se sigue mostrando el historial;
rango invertido → no se aplica y se avisa; página fuera de rango → se ajusta a
la última.

**Pruebas:** `PanelHistorialIntegrationTest`.

---

## CU-04 · Atender una alerta

**Actor:** operador.
**Precondición:** sesión y una muestra con `alert = true`.

1. El operador abre `/panel/alertas` (por defecto, pendientes).
2. Pulsa «Atender», escribe una nota y confirma (POST con CSRF).
3. El backend registra `acknowledged_at`, `acknowledged_by` (de la sesión) y
   `acknowledge_note`.
4. El tablero muestra «Atendida por …», sin cambiar el veredicto.

**Alternativas:** muestra sin alerta o id inexistente → 404; doble envío → no
pisa la auditoría; reabrir → borra la auditoría y vuelve a pendientes.

**Pruebas:** `PanelAlertasIntegrationTest`.

---

## CU-05 · Generar una predicción

**Actor:** operador (panel) o cliente con JWT (API).
**Precondición:** al menos 24 muestras por estación.

1. El operador elige horizonte (1, 3 o 6 h) y pulsa «Generar predicción»
   (POST con CSRF), o llama a `POST /api/v1/predictions`.
2. El backend consulta `/health` del servicio de IA.
3. Arma la ventana `[4 estaciones][24][4 variables]` con las últimas muestras.
4. Envía la ventana a `/predict`.
5. Valida la respuesta (versión, origen, objetivo posterior, cuatro estaciones,
   pH finitos).
6. Persiste cuatro filas `PENDING` con el mismo `model_run_id`.
7. El panel muestra los valores con la etiqueta «Datos sintéticos».

**Alternativas:** IA caída → 503 y aviso en el panel; respuesta inválida → 502;
historia insuficiente → 409; horizonte no entrenado → 400. En ningún caso se
inventa un valor ni se guarda una predicción parcial.

**Pruebas:** `PredictionServiceTest`, `PredictionControllerTest`,
`PredictionIntegrationTest`, `PanelPrediccionesIntegrationTest`.

---

## CU-06 · Evaluar una predicción

**Actor:** sistema (efecto de CU-01).
**Precondición:** existe una predicción `PENDING`.

1. Llega una medición real de la estación.
2. El evaluador busca predicciones pendientes de esa estación con `target_at`
   dentro de ±30 min.
3. Guarda `observed_value`, `error = predicho − observado`, `evaluated_at` y
   cambia el estado a `EVALUATED`.
4. El valor previsto no se modifica.

**Alternativas:** no hay predicción en la ventana → no hace nada; falla la
evaluación → se absorbe y la muestra queda registrada.

**Pruebas:** `PredictionEvaluatorTest`, `PredictionIntegrationTest`.

---

## CU-07 · Consultar el estado de la IA

**Actor:** operador / monitor.
**Precondición:** JWT.

1. `GET /api/v1/ai/health`.
2. El backend consulta `/health` de FastAPI con timeout.
3. Responde 200 con `available`, versión, horizontes, `input_len` y origen; o
   `available=false` con el detalle.

**Pruebas:** `AiClientTest`, `PredictionControllerTest`.

---

## CU-08 · Operar el nodo en campo

**Actor:** nodo ESP32.
**Precondición:** calibración cargada y WiFi configurado.

1. Despierta por temporizador.
2. Si es arranque en frío, genera un `bootId` nuevo con el RNG.
3. Lee cada canal 3 veces y promedia (filtro causal).
4. Convierte a unidades físicas y valida rangos.
5. Conecta WiFi, obtiene/reutiliza el JWT, envía el payload con `sample_id`.
6. Si el envío se confirma, incrementa la secuencia; si falla, la conserva.
7. Desconecta WiFi y entra en Deep Sleep.

**Alternativas:** muestra implausible → no se envía; sin WiFi → no se envía y
se registra en serie; token vencido → se reautentica.

**Pruebas:** 38 tests nativos + build de ambas variantes.
