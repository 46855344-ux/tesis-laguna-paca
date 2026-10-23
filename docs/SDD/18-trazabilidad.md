# 18 · Trazabilidad

Cada requisito se enlaza con el código que lo implementa y la prueba que lo
fija. Si un requisito no tiene prueba, se dice; no se inventa cobertura.

## 18.1 Requisitos funcionales

| RF | Implementación | Prueba |
|---|---|---|
| RF-01 | `SecurityConfig`, `JwtAuthenticationFilter` | `ApiIntegrationTest` |
| RF-02 | `telemetry/mqtt/TelemetryMqttHandler`, `MqttProperties` | `TelemetryMqttHandlerTest`, `MqttPropertiesTest` |
| RF-03 | `EcaWaterQualityRule` | `EcaWaterQualityRuleTest` |
| RF-04 | `TelemetryService` (`Instant.now(clock)`) | `TelemetryServiceTest` |
| RF-05 | `TelemetryService.search`, `WaterQualitySampleSpecifications` | `PanelHistorialIntegrationTest`, `WaterQualitySampleRepositoryTest` |
| RF-06 | `AlertReason`, `EcaWaterQualityRule` | `EcaWaterQualityRuleTest` |
| RF-07 | `PredictionService.generar` | `PredictionServiceTest`, `PredictionControllerTest` |
| RF-08 | `Prediction`, `V5__predictions_and_idempotency.sql` | `FlywayMigrationsTest` |
| RF-09 | `PredictionEvaluator` | `PredictionEvaluatorTest`, `PredictionIntegrationTest` |
| RF-10 | `AiStatusController`, `AiClient.health` | `PredictionControllerTest`, `AiClientTest` |
| RF-11 | `PanelDashboardService`, `DashboardPage.tsx` | `PanelDashboardIntegrationTest`, `pages.test.tsx` |
| RF-12 | `PanelHistorialService`, `HistoryPage.tsx` | `PanelHistorialIntegrationTest`, `pages.test.tsx` |
| RF-13 | `PanelAlertasService`, `AlertsPage.tsx`, `V4` | `PanelAlertasIntegrationTest`, `pages.test.tsx` |
| RF-14 | `MapaLagunaService`, `geo/laguna-paca.json` | `PanelMapaIntegrationTest` |
| RF-15 | `TelemetryService.ingest`, `findBySampleId`, índice único | `TelemetryServiceTest`, `PredictionIntegrationTest` |
| RF-16 | `GlobalExceptionHandler` (409) | `ApiIntegrationTest`, `TelemetryControllerTest` |
| RF-17 | `PredictionController` (POST explícito) | `PredictionControllerTest` |
| RF-18 | `PredictionService` (horizontes permitidos) | `PredictionServiceTest`, `PredictionControllerTest` |
| RF-19 | `PredictionWindowBuilder`, `IncompleteWindowException` | `PredictionWindowBuilderTest`, `PredictionServiceTest` |
| RF-20 | `PanelDashboardService`, `DashboardPage.tsx` | `PanelPrediccionesIntegrationTest` |
| RF-21 | `data_source` en artefacto, tabla, DTO y panel | `AiClientTest`, `PanelPrediccionesIntegrationTest` |
| RF-22 | Estados vacío/error en `DashboardPage.tsx` | `PanelPrediccionesIntegrationTest` |
| RF-23 | `MovingAverageFilter`, `TelemetryNode` | 38 tests nativos |
| RF-24 | `SensorCalibration`, `TelemetryNode` | `test_node_descartaUnaMuestraImplausible` |
| RF-25 | `SampleIdentity`, `TelemetryPayload`, `main.cpp` | `test_sampleId_*`, `test_payload_incluyeSampleId*` |
| RF-26 | `DeepSleepPolicy` | `test_deepSleep_*` |
| RF-27 | `TelemetryTransport` (HTTP/MQTT) | Build de ambos entornos |
| RF-28 | `Prediction.evaluate` / DTO | `PredictionEvaluatorTest` |
| RF-29 | `PredictionService`, `api.py` (503) | `PredictionServiceTest`, `test_api.py` |

## 18.2 Requisitos no funcionales

| RNF | Evidencia | ¿Verificado? |
|---|---|---|
| RNF-01 | `pom.xml` + 145 tests | Sí |
| RNF-02 | `requirements.txt` + 172 tests | Sí |
| RNF-03 | Ingesta sin dependencia de IA | Sí |
| RNF-04 | Timeouts 2 s/15 s | Sí |
| RNF-05 | `.gitignore` + revisión | Parcial (sin escáner) |
| RNF-06 | SVG propio, sin CDN | Sí |
| RNF-07 | Contrastes y ARIA | Parcial (sin auditoría automática) |
| RNF-08 | `America/Lima` + test | Sí |
| RNF-09 | Dos cadenas de filtros | Sí |
| RNF-10 | Compose + Dockerfiles + CI + job PostgreSQL | Parcial: CI ejecutada y V1–V5 en `postgres:16-alpine`; imágenes no construidas |
| RNF-11 | `sample_id` + índice | Sí |
| RNF-12 | H2, `HttpServer`, dobles | Sí |
| RNF-13 | `DashboardPage.tsx` | Manual |
| RNF-14 | Artefacto, no entrenar al arrancar | Sí |
| RNF-15 | Campos de evaluación | Sí |
| RNF-16 | `data_source` | Sí |
| RNF-17 | JaCoCo 91.7 % de instrucciones | Sí |
| RNF-18 | Flash 72.3 % / 59.8 % | Sí |
| RNF-19 | Perfil `demo` | Sí (tests de contexto) |
| RNF-20 | Texto en español | Revisión manual |

## 18.3 Objetivos de la tesis

| Objetivo | Evidencia | Estado |
|---|---|---|
| Ingesta IoT con alertas ECA-Agua | 145 tests de backend, simulador | Cumplido |
| ST-GNN con R² > 0.90 a 6 h | `R² = 0.9289` vs persistencia 0.7403 | Cumplido con datos sintéticos |
| Servicio de inferencia integrado | FastAPI + `PredictionService` | Cumplido |
| Evaluación de predicciones | `PredictionEvaluator` | Cumplido |
| Panel operativo | 66 tests de panel + 17 de frontend | Cumplido |
| Idempotencia de ingesta | `sample_id` + firmware | Cumplido |
| Despliegue reproducible | Compose + CI | Parcial: CI ejecutada; `docker compose build/up` pendiente |
| Datos de campo | Cargador `--csv` probado; faltan mediciones reales | **Pendiente** |

## 18.4 Huecos declarados

1. **PostgreSQL de producción**: V1–V5 verificadas en PostgreSQL 16.9 embebido;
   falta el contenedor y el arranque desde Spring Boot.
2. **Docker Compose**: `config` validado; imágenes no construidas ni stack
   levantado.
3. **SonarQube**: configuración lista, análisis no ejecutado.
4. **TLS**: no implementado.
5. **Hardware**: firmware compilado y probado en host, no en placa.
6. **Datos de campo**: el cargador `field_data.py` y `--csv` están probados, pero
   el modelo se entrenó con datos sintéticos porque no hay campaña de muestreo.
7. **Coordenadas**: `PROVISIONAL_STATIONS` derivadas de OSM, no medidas.
8. **App Android**: MVP implementado (login, tablero y alertas); FCM, mapa y
   persistencia del token siguen pendientes.
9. **Notificaciones**: solo panel, app y API; sin push ni correo.
10. **Métricas de latencia**: no medidas contra PostgreSQL.

Estos huecos están repetidos en `26-limitaciones.md` con su impacto, para que no
se pierdan en un documento de trazabilidad.
