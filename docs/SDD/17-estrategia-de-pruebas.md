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
- **De componentes**: React con Vitest + Testing Library y `jsdom`.
- **De contrato**: el servicio FastAPI con `TestClient`.
- **Nativas**: el núcleo del firmware en el host, con dobles de sensores.
- **Unitarias JVM**: resumen del tablero y normalización de URL en Android.

## 17.2 Totales verificados (22/10/2026)

| Suite | Comando | Tests | Estado |
|---|---|---|---|
| Backend | `mvn test` | **145** | Verde |
| Frontend | `npm test` | **17** | Verde |
| Firmware | `pio test -e native` | **38** | Verde |
| IA | `python -m pytest` | **172** | Verde |
| Android | `gradlew.bat testDebugUnitTest` | **6** | Verde |
| **Total** | | **378** | |

Cobertura del backend (JaCoCo): **91.7 %** de instrucciones (5491/5989) y
**68.8 %** de ramas (265/385). Se reporta como indicador, no como meta: la
cobertura sola no dice si una prueba comprueba lo correcto.

Además, `PostgresMigrationIT` (1 test) valida las migraciones contra PostgreSQL
real y **no** corre en `mvn test`: lo ejecuta el job `backend-postgres` del CI
con `postgres:16-alpine`. Con él, el total sería **379**.

## 17.3 Backend: 145 tests

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
| `PanelIntegrationTest` | 13 | Raíz, shell React, assets, login, CSRF, logout, JWT no abre panel, 401 JSON |
| `PanelDashboardIntegrationTest` | 7 | Última muestra, alerta, JSON, 24 h |
| `PanelHistorialIntegrationTest` | 12 | Filtros, Lima, paginación, avisos |
| `PanelAlertasIntegrationTest` | 12 | Atención, CSRF, auditoría, 404, reabrir |
| `PanelMapaIntegrationTest` | 6 | Contorno, proyección, colores, sin teselas |
| `PanelMapaGoogleIntegrationTest` | 3 | Google opcional, clave fuera del JSON |
| `PanelMapaTest` | 2 | Serialización sin clave |
| `PanelPrediccionesIntegrationTest` | 11 | Predicciones, origen, evaluación, IA caída, CSRF, horizonte inválido |

### Qué **no** cubren

- **La suite usa H2**: no ejecuta el índice parcial ni `TIMESTAMPTZ`. Fuera de
  la suite se ejecutó una verificación de V1–V5 contra PostgreSQL 16.9 embebido
  (`28-registro-de-evidencias.md`, 28.3), pero no forma parte de `mvn test`.
- **No hay motor Docker**: `compose config` se validó; `build/up` no se ejecuta
  en los tests.
- **No hay SonarQube**: el análisis está configurado, no ejecutado.
- **No hay E2E con navegador real**: el refresco de 20 s, el cajón móvil y la
  carga opcional de Google Maps se validan a mano. Las pruebas de componentes
  usan `jsdom` y dobles de `fetch`; no son datos de campo.
- **No hay hardware**: los sensores del firmware son dobles.
- **La app Android no tiene E2E ni pruebas instrumentadas**: se validó a mano en el
  emulador `Pixel_9` contra el perfil `demo`; los 6 tests son unitarios JVM.

## 17.4 Frontend: 17 tests

| Archivo | Tests | Qué fija |
|---|---|---|
| `estado.test.ts` | 2 | Etiquetas, clases y los tres estados ECA-Agua |
| `client.test.ts` | 5 | CSRF en cada petición, 401, reintento por 403, login y detección por URL final |
| `components.test.tsx` | 6 | Badge, mapa con enlaces, paginación y gráfico SVG |
| `pages.test.tsx` | 4 | Historial con filtros, atención de alertas, tablero y métricas con datos simulados |

- Usan **Vitest + Testing Library + jsdom**, no un navegador real.
- Los dobles de `fetch` devuelven JSON del contrato del panel; no se usan
  mediciones de la laguna ni se presentan como validación de campo.
- No cubren Google Maps real, el refresco de 20 s ni el cajón móvil: eso se
  valida a mano porque depende del navegador y de la red.

## 17.5 Firmware: 38 tests

- `test_core.cpp` cubre filtros, calibración, payload exacto, `sample_id`,
  plausibilidad, transporte, Deep Sleep y client id MQTT.
- Se ejecutan en la PC con `g++` (MinGW en Windows), sin ESP32.
- `-DUNITY_INCLUDE_DOUBLE` está en `platformio.ini` porque `unity.c` se compila
  como unidad aparte; sin el flag, `UnityAssertDoublesWithin` queda sin enlazar.
- Ambas variantes compilan (`esp32dev`, `esp32dev-mqtt`), lo que verifica el
  glue de Arduino que los tests nativos no tocan.

## 17.6 IA: 172 tests

- `test_stations.py`, `test_graph.py`, `test_windows.py`, `test_metrics.py`,
  `test_model.py`, `test_dataset.py`, `test_normalization.py`,
  `test_training.py`: la base de 134.
- `test_registry.py` (8): guardar/cargar artefacto, versión sintética y de campo,
  corrupción, `FORMAT_VERSION`.
- `test_api.py` (11): `/health` sin modelo, `/predict` 503, token, forma,
  `base_time` con zona, NaN, horizonte no entrenado.
- `test_field_data.py` (19): CSV válido, promedio por hora, UTC obligatorio,
  nodo/columna/valor inválido, duplicado exacto, rango común, estación sin datos,
  huecos interpolados solo con permiso explícito y archivo vacío.
- Los tests del modelo comprueban **propiedades** (formas, causalidad,
  topología, gradientes, atención que suma 1), no valores mágicos.
- Los CSV de `test_field_data.py` son *fixtures*: no son mediciones de la laguna.
  El cargador está probado; la validación científica sigue pendiente de campo.

## 17.7 Android: 6 tests

| Archivo | Tests | Qué fija |
|---|---|---|
| `DashboardMapperTest` | 3 | Ventana de 24 h, las cuatro estaciones, último dato y cumplimiento sin inventar 100 % |
| `MainViewModelTest` | 3 | Esquema de URL, conservación de `https` y rechazo de dirección vacía |

- Se ejecutan en la JVM (`testDebugUnitTest`), sin emulador.
- **No** hay tests instrumentados de Compose ni E2E. El login, el tablero, las
  alertas y el cierre de sesión se verificaron manualmente en el emulador.
- No se usan datos de campo: la demo usa el simulador y H2.

## 17.7 Cómo ejecutar

```powershell
# Backend
cd backend
$env:JAVA_HOME="D:\Intellij\IntelliJ IDEA 2025.2.1\jbr"
& "C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.9\977a63e90f436cd6ade95b4c0e10c20c\bin\mvn.cmd" test

# Frontend
cd frontend
npm test
npm run build

# Firmware
$env:PATH="C:\Users\USER\AppData\Local\Programs\mingw64\bin;$env:PATH"
& "C:\Users\USER\AppData\Local\Programs\Python\Python312\Scripts\pio.exe" test -e native

# IA
cd ia-model
.venv\Scripts\python.exe -m pytest
.venv\Scripts\python.exe train_stgnn.py --csv datos\campo.csv --max-gap-hours 2
```

## 17.8 Datos de prueba

- Las muestras se insertan por repositorio para fijar `recordedAt` sin depender
  del reloj.
- Hay un `Clock` inyectable (`config/ClockConfig`) para pruebas deterministas.
- La IA se sustituye con `@MockBean` en los tests de integración; el cliente real
  se prueba contra `com.sun.net.httpserver.HttpServer`.
- El frontend usa `vi.fn()` para `fetch` y renderiza con `MemoryRouter`; el test
  de login fija la URL final `/panel`, no solo el código HTTP.
- El firmware usa `FakeAnalogChannel`, `FakeTemperatureSensor` y `FakeTransport`.

## 17.9 Tiempo de ejecución

- Backend: ~90 s (los tests de contexto completo dominan).
- Frontend: ~6 s (Vitest + jsdom).
- IA: ~3.5 min en la última ejecución (los tests del modelo entrenan redes pequeñas en CPU); puede variar con la caché y la carga de la máquina.
- Firmware: ~10 s.

## 17.10 Lección aprendida

El primer test que cargó el contexto completo descubrió dos bugs que 32 tests de
slice no podían ver: `TelemetryService` tenía dos constructores sin `@Autowired`
(la aplicación no arrancaba) y un GET protegido sin token devolvía 403 en vez del
401 que exige `openapi.yaml`. Por eso el test de integración se mantiene y se
amplía antes de agregar funcionalidad.
