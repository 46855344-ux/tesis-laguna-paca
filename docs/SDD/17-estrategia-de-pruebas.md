# 17 · Estrategia de pruebas

## 17.1 Enfoque

Se siguió **TDD** en los módulos nuevos: primero la prueba que falla, luego la
implementación mínima, después el refactor. La regla que se mantuvo: **una
prueba no se cambia para que pase**; si el comportamiento esperado estaba mal, se
corrige el comportamiento, no la aserción.

La pirámide es deliberadamente ancha en la base:

- **Unitarias**: regla ECA-Agua, evaluador, ventana, cliente IA con un
  `HttpServer` del JDK, filtros y payload del firmware.
- **De slice**: controladores con `@WebMvcTest` y servicios instanciados a mano.
- **De integración**: contexto completo con H2 (`@SpringBootTest`), panel y API.
- **De contrato**: el servicio FastAPI con `TestClient`.
- **Nativas**: el núcleo del firmware en el host, con dobles de sensores.

## 17.2 Totales verificados (20/10/2026)

| Suite | Comando | Tests | Estado |
|---|---|---|---|
| Backend | `mvn test` | **138** | Verde |
| Firmware | `pio test -e native` | **38** | Verde |
| IA | `python -m pytest` | **151** | Verde |
| **Total** | | **327** | |

Cobertura del backend (JaCoCo): **91.5 %** de instrucciones (5321/5813) y
**70.0 %** de ramas (254/363). Se reporta como indicador, no como meta: la
cobertura sola no dice si una prueba comprueba lo correcto.

Además, `PostgresMigrationIT` (1 test) valida las migraciones contra PostgreSQL
real y **no** corre en `mvn test`: lo ejecuta el job `backend-postgres` del CI
con `postgres:16-alpine`. Con él, el total sería **328**.

## 17.3 Backend: 138 tests

| Clase | Tests | Qué fija |
|---|---|---|
| `ApiIntegrationTest` | 6 | Contexto completo, 401/403, flujo de ingesta y alerta, predicciones protegidas |
| `TelemetryControllerTest` | 8 | Validación, 201/200, mapeo de errores |
| `EcaWaterQualityRuleTest` | 4 | Bordes 6.5 / 9.0, motivos |
| `TelemetryServiceTest` | 6 | Persistencia, idempotencia, evaluación absorbida |
| `WaterQualitySampleRepositoryTest` | 5 | Especificaciones y paginación |
| `MqttPropertiesTest` | 3 | Valores por defecto |
| `TelemetryMqttHandlerTest` | 4 | Payload válido/inválido/duplicado |
| `PredictionServiceTest` | 5 | Horizontes, IA caída, ventana incompleta, contrato |
| `PredictionWindowBuilderTest` | 4 | Forma `[4][T][4]`, orden de variables, historia insuficiente |
| `PredictionEvaluatorTest` | 5 | ±30 min, error, no reescribe, estados |
| `PredictionControllerTest` | 7 | 201/400/409/502/503, estado de IA |
| `PredictionIntegrationTest` | 4 | Flujo completo con IA simulada, evaluación, idempotencia |
| `AiClientTest` | 10 | Timeouts, token, 4xx/5xx, JSON inválido, contrato |
| `JwtServiceTest` | 5 | Firma, expiración, issuer |
| `FlywayMigrationsTest` | 3 | Contigüidad, no destructivo, V5 aditiva |
| `PanelIntegrationTest` | 9 | Login, CSRF, JWT no abre panel, estáticos |
| `PanelDashboardIntegrationTest` | 6 | Última muestra, alerta, JSON, 24 h |
| `PanelHistorialIntegrationTest` | 12 | Filtros, Lima, paginación, avisos |
| `PanelAlertasIntegrationTest` | 12 | Atención, CSRF, auditoría, 404, reabrir |
| `PanelMapaIntegrationTest` | 7 | Contorno, proyección, colores, sin teselas |
| `PanelMapaGoogleIntegrationTest` | 3 | Google opcional, clave fuera del JSON |
| `PanelMapaTest` | 2 | Serialización sin clave |
| `PanelPrediccionesIntegrationTest` | 8 | Predicciones, origen, evaluación, IA caída, CSRF |

### Qué **no** cubren

- **La suite usa H2**: no ejecuta el índice parcial ni `TIMESTAMPTZ`. Fuera de
  la suite se ejecutó una verificación de V1–V5 contra PostgreSQL 16.9 embebido
  (`28-registro-de-evidencias.md`, 28.3), pero no forma parte de `mvn test`.
- **No hay motor Docker**: `compose config` se validó; `build/up` no se ejecuta
  en los tests.
- **No hay SonarQube**: el análisis está configurado, no ejecutado.
- **No hay navegador**: el refresco de 20 s y el cajón móvil se validan a mano.
- **No hay hardware**: los sensores del firmware son dobles.

## 17.4 Firmware: 38 tests

- `test_core.cpp` cubre filtros, calibración, payload exacto, `sample_id`,
  plausibilidad, transporte, Deep Sleep y client id MQTT.
- Se ejecutan en la PC con `g++` (MinGW en Windows), sin ESP32.
- `-DUNITY_INCLUDE_DOUBLE` está en `platformio.ini` porque `unity.c` se compila
  como unidad aparte; sin el flag, `UnityAssertDoublesWithin` queda sin enlazar.
- Ambas variantes compilan (`esp32dev`, `esp32dev-mqtt`), lo que verifica el
  glue de Arduino que los tests nativos no tocan.

## 17.5 IA: 151 tests

- `test_stations.py`, `test_graph.py`, `test_windows.py`, `test_metrics.py`,
  `test_model.py`, `test_dataset.py`, `test_normalization.py`,
  `test_training.py`: la base de 134.
- `test_registry.py` (6): guardar/cargar artefacto, versión, corrupción,
  `FORMAT_VERSION`.
- `test_api.py` (11): `/health` sin modelo, `/predict` 503, token, forma,
  `base_time` con zona, NaN, horizonte no entrenado.
- Los tests del modelo comprueban **propiedades** (formas, causalidad,
  topología, gradientes, atención que suma 1), no valores mágicos.

## 17.6 Cómo ejecutar

```powershell
# Backend
cd backend
$env:JAVA_HOME="D:\Intellij\IntelliJ IDEA 2025.2.1\jbr"
& "C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.9\977a63e90f436cd6ade95b4c0e10c20c\bin\mvn.cmd" test

# Firmware
$env:PATH="C:\Users\USER\AppData\Local\Programs\mingw64\bin;$env:PATH"
& "C:\Users\USER\AppData\Local\Programs\Python\Python312\Scripts\pio.exe" test -e native

# IA
cd ia-model
.venv\Scripts\python.exe -m pytest
```

## 17.7 Datos de prueba

- Las muestras se insertan por repositorio para fijar `recordedAt` sin depender
  del reloj.
- Hay un `Clock` inyectable (`config/ClockConfig`) para pruebas deterministas.
- La IA se sustituye con `@MockBean` en los tests de integración; el cliente real
  se prueba contra `com.sun.net.httpserver.HttpServer`.
- El firmware usa `FakeAnalogChannel`, `FakeTemperatureSensor` y `FakeTransport`.

## 17.8 Tiempo de ejecución

- Backend: ~90 s (los tests de contexto completo dominan).
- IA: ~9 min (los tests del modelo entrenan redes pequeñas en CPU).
- Firmware: ~10 s.

## 17.9 Lección aprendida

El primer test que cargó el contexto completo descubrió dos bugs que 32 tests de
slice no podían ver: `TelemetryService` tenía dos constructores sin `@Autowired`
(la aplicación no arrancaba) y un GET protegido sin token devolvía 403 en vez del
401 que exige `openapi.yaml`. Por eso el test de integración se mantiene y se
amplía antes de agregar funcionalidad.
