# 03 · Requisitos funcionales

Convención: **RF-xx**. La columna «Prueba» apunta al test que fija el
comportamiento. Los identificadores coinciden con los usados en
`18-trazabilidad.md`.

## 3.1 Ingesta y reglas

| ID | Requisito | Prueba |
|---|---|---|
| RF-01 | La API debe autenticar al nodo con JWT antes de aceptar telemetría. | `ApiIntegrationTest` |
| RF-02 | Debe existir una segunda vía de ingesta por MQTT QoS 1, deshabilitada por defecto y sin JWT (credenciales del broker). | `TelemetryMqttHandlerTest` |
| RF-03 | Una muestra con `pH < 6.5` genera `PH_LOW`; con `pH > 9.0`, `PH_HIGH`. Los valores 6.5 y 9.0 **no** son alerta. | `EcaWaterQualityRuleTest` |
| RF-04 | El servidor asigna `recorded_at` al recibir la muestra; no confía en la hora del nodo (que no tiene NTP). | `TelemetryServiceTest` |
| RF-05 | El histórico debe filtrar por estación, rango de fechas, alerta, código y texto del motivo, con paginación. | `PanelHistorialIntegrationTest` |
| RF-06 | Cada alerta debe guardar sus motivos redactados (`alert_reason`) junto a la muestra. | `EcaWaterQualityRuleTest` |
| RF-15 | Si llega un `sample_id` ya registrado, la ingesta devuelve la muestra original con HTTP 200 y no crea otra fila. | `TelemetryServiceTest`, `PredictionIntegrationTest` |
| RF-16 | Un `sample_id` nuevo pero que choca por concurrencia debe responder 409, no 500. | `GlobalExceptionHandler` + `FlywayMigrationsTest` |

## 3.2 Predicciones

| ID | Requisito | Prueba |
|---|---|---|
| RF-07 | El sistema debe generar pH previsto para las cuatro estaciones a 1, 3 y 6 horas. | `PredictionServiceTest`, `PredictionControllerTest` |
| RF-08 | Las predicciones se guardan en una tabla propia, con `model_run_id`, versión, origen de datos y estado; nunca en `water_quality_sample`. | `FlywayMigrationsTest` |
| RF-09 | Cuando llega una medición real dentro de ±30 min del `target_at`, la predicción se evalúa y guarda observado y error, sin reescribir el valor previsto. | `PredictionEvaluatorTest`, `PredictionIntegrationTest` |
| RF-10 | Debe existir un endpoint de estado de la IA que responda 200 incluso si está caída. | `PredictionControllerTest` |
| RF-17 | La generación de una predicción debe ser explícita (POST), no un efecto de cada ingesta. | `PredictionControllerTest` |
| RF-18 | Solo se aceptan horizontes entrenados (1, 3, 6 h); otro valor responde 400. | `PredictionServiceTest` |
| RF-19 | Si falta historia para la ventana, la respuesta debe ser 409 con un mensaje que indique la estación y cuántas muestras hay. | `PredictionWindowBuilderTest` |

## 3.3 Panel web

| ID | Requisito | Prueba |
|---|---|---|
| RF-11 | El tablero debe mostrar indicadores de 24 h, una tarjeta por estación con su última muestra y el veredicto persistido. | `PanelDashboardIntegrationTest` |
| RF-12 | El historial debe listar 20 muestras por página y conservar los filtros al paginar. | `PanelHistorialIntegrationTest` |
| RF-13 | Las alertas deben poder atenderse y reabrirse, con usuario, fecha y nota; el veredicto no cambia. | `PanelAlertasIntegrationTest` |
| RF-14 | El mapa debe dibujar el contorno real de la laguna y enlazar cada estación a su historial. | `PanelMapaIntegrationTest` |
| RF-20 | El panel debe mostrar las predicciones aparte de las mediciones, con su origen (sintético/campo) y, si existe, el error. | `PanelPrediccionesIntegrationTest` |
| RF-22 | El panel debe explicar que no hay predicciones cuando la lista está vacía y que la IA no está disponible cuando falla. | `PanelPrediccionesIntegrationTest` |

## 3.4 Firmware

| ID | Requisito | Prueba |
|---|---|---|
| RF-23 | El nodo debe medir pH, turbidez, temperatura y TDS con filtro de media móvil causal. | `test_node_aplicaLaMediaMovilCausal` |
| RF-24 | El nodo debe descartar muestras implausibles antes de enviarlas. | `test_node_descartaUnaMuestraImplausible` |
| RF-25 | El nodo debe enviar `sample_id` con un `bootId` que cambie en cada arranque en frío y una secuencia que solo avance al confirmar la entrega. | `test_sampleId_*`, `test_payload_incluyeSampleId*` |
| RF-26 | El nodo debe dormir en Deep Sleep entre ciclos y descontar el tiempo activo. | `test_deepSleep_*` |
| RF-27 | El nodo debe funcionar por HTTP o MQTT sin cambiar el núcleo. | Build de `esp32dev` y `esp32dev-mqtt` |

## 3.5 Honestidad de los datos

| ID | Requisito | Prueba |
|---|---|---|
| RF-21 | Toda predicción debe declarar `data_source` (`synthetic` o `field`); la interfaz no puede presentar una simulación como medición de campo. | `AiClientTest`, `PanelPrediccionesIntegrationTest` |
| RF-28 | Una predicción pendiente no debe mostrar valores observados ni error inventados. | `PredictionEvaluatorTest` |
| RF-29 | El sistema no debe crear predicciones si el servicio de IA no responde o no tiene modelo. | `PredictionServiceTest`, `test_api.py` |

## 3.6 Reglas que no son requisitos

- No hay cuarto estado «cerca del límite»: sería una regla que el backend no
  conoce.
- El panel no reevalúa el pH: solo lee `sample.isAlert()` y los motivos ya
  redactados.
- La predicción no cambia el badge de la estación: el veredicto es de la
  medición, no del pronóstico.
