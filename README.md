# Sistema de software e IoT con Redes Neuronales de Grafos (GNN) para el monitoreo de la contaminación en la Laguna de Paca

Tesis de **Ronald Arroyo Breña (RONY)** — Universidad Continental, Junín, 2026.

El proyecto mide la calidad del agua de la Laguna de Paca con nodos ESP32, evalúa cada
muestra contra los límites de la **ECA-Agua** (pH entre 6.5 y 9.0), guarda el historial,
avisa en un **panel web** y predice el pH a futuro con un **ST-GNN** (red espacio-temporal
sobre el grafo de estaciones).

> **Estado:** backend, firmware e IA funcionando de extremo a extremo, con predicciones
> persistidas y evaluadas contra muestras posteriores. **348 pruebas** verdes (138 backend,
> 38 firmware, 172 IA). La app Android todavía no está implementada. Los datos de
> entrenamiento del modelo son **sintéticos**: el resultado demuestra la arquitectura,
> no el comportamiento real de la laguna. El despliegue con Docker está configurado,
> pero **no se ejecutó** en esta máquina (no hay Docker).

---

## Contenido

- [Arquitectura](#arquitectura)
- [Estado por módulo](#estado-por-módulo)
- [Inicio rápido (demo sin PostgreSQL)](#inicio-rápido-demo-sin-postgresql)
- [Panel web](#panel-web)
- [API REST](#api-rest)
- [Firmware ESP32](#firmware-esp32)
- [Modelo de IA (ST-GNN)](#modelo-de-ia-st-gnn)
- [Pruebas](#pruebas)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Decisiones que conviene no deshacer](#decisiones-que-conviene-no-deshacer)
- [Pendientes](#pendientes)
- [Documentación](#documentación)

---

## Arquitectura

```
┌────────────────────┐   HTTP POST / MQTT    ┌──────────────────────────────┐
│  Nodos ESP32       │ ────────────────────► │  Backend (Spring Boot)       │
│  N1 … N4           │   payload JSON +      │  · ingesta idempotente       │
│  pH, turbidez,     │   sample_id           │  · regla ECA-Agua (alerta)   │
│  temperatura, TDS  │                       │  · PostgreSQL + Flyway       │
└────────────────────┘                       │  · API REST con JWT          │
                                             │  · panel web (Thymeleaf)     │
                                             └───────┬──────────────┬───────┘
                                                     │              │ /predict
                                     series históricas│              │ (red interna)
                                                     ▼              ▼
                                             ┌──────────────────────────────┐
                                             │  IA (Python + FastAPI)       │
                                             │  ST-GNN: TCN + GCN +         │
                                             │  atención sobre el grafo de  │
                                             │  las 4 estaciones            │
                                             └──────────────────────────────┘
```

El **backend es la única fuente de verdad** de las reglas de negocio: el firmware solo
mide y publica, el panel solo muestra el veredicto ya persistido y el servicio de IA
solo pronostica. Si la IA está caída, la ingesta y las alertas siguen funcionando.

### Stack

| Capa | Tecnología |
|---|---|
| Firmware | ESP32 (C++/Arduino, PlatformIO), deep sleep, HTTP y MQTT QoS 1 |
| Backend | Java 21, Spring Boot 3.2, PostgreSQL 16, Flyway, JWT, Thymeleaf |
| IA | Python 3.12, PyTorch (CPU), PyTorch Geometric, NumPy, FastAPI |
| Móvil (pendiente) | Kotlin, Jetpack Compose, Firebase Cloud Messaging |

---

## Estado por módulo

| Módulo | Estado | Qué incluye |
|---|---|---|
| `backend/` | ✅ Completo (138 tests) | Ingesta HTTP y MQTT idempotente, alertas ECA-Agua, historial paginado, JWT, Flyway V1–V5, panel web, predicciones y su evaluación, Swagger UI |
| `iot-firmware/` | ✅ Completo (38 tests) | Núcleo portable C++11, sensores, calibración, deep sleep, JWT cacheado, transporte HTTP y MQTT, `sample_id` |
| `ia-model/` | ✅ Completo (172 tests) | Grafo, ventanas, ST-GNN, normalización, entrenamiento, carga de CSV de campo, artefacto versionado y servicio FastAPI |
| `android-app/` | ⬜ Vacío | Pendiente: tema Material 3, login, tablero, alertas y FCM |
| Docker/CI | ⚙️ Configurado, no ejecutado | `compose.yaml`, Dockerfiles, workflow de CI, JaCoCo y Sonar (sin Docker/Sonar en la máquina) |

---

## Inicio rápido (demo sin PostgreSQL)

La demo usa **H2 en memoria** (perfil `demo`): no hace falta instalar PostgreSQL ni Docker.
Los datos viven en memoria y **se borran al reiniciar**.

### Requisitos

- **JDK 21** (el JBR que trae IntelliJ sirve: `D:\Intellij\IntelliJ IDEA 2025.2.1\jbr`).
  ⚠️ No uses JDK 26: Spring Boot 3.2 no arranca con él.
- **Maven 3.9** (o el Maven integrado de IntelliJ).
- **Python 3.12** para el simulador de nodos y el servicio de IA (ambos opcionales).

### 1. Arrancar

Desde `backend/` (PowerShell):

```powershell
$env:JAVA_HOME="D:\Intellij\IntelliJ IDEA 2025.2.1\jbr"
$env:GOOGLE_MAPS_API_KEY="tu-clave"   # opcional: mapa de Google
mvn spring-boot:run -Dspring-boot.run.profiles=demo -Dspring-boot.run.useTestClasspath=true
```

`useTestClasspath=true` es necesario porque H2 está en ámbito `test`: así la demo arranca
sin meter el driver en el artefacto de producción.

Espera la línea `Started LagunaPacaApplication`. Luego abre:

| Recurso | URL |
|---|---|
| Panel de monitoreo | http://127.0.0.1:8080/panel |
| Formulario de acceso | http://127.0.0.1:8080/login |
| Documentación de la API (Swagger UI) | http://127.0.0.1:8080/swagger-ui.html |

Credenciales de demostración: **admin / admin123** (solo para la demo).

> Un JWT válido **no** abre el panel: son dos mecanismos distintos (el panel usa sesión
> con formulario y CSRF; la API usa JWT sin estado).

### 2. Sembrar datos

El simulador genera muestras y las envía al backend como lo haría un nodo:

```powershell
python tools/simulate_node.py                                # 10 muestras (escenario mixto)
python tools/simulate_node.py --scenario alert --count 6     # fuerza alertas
python tools/simulate_node.py --scenario mixed --count 12    # mezcla
python tools/simulate_node.py --node-id N2 --scenario alert  # una estación concreta
```

Escenarios: `normal`, `alert`, `limit` (pH exactamente 6.5 y 9.0, que **no** son alerta)
y `mixed`. El simulador solo genera datos: quien decide si hay alerta es el backend.

### 3. Predicciones (opcional)

El backend arranca sin la IA: la ingesta y las alertas no dependen de ella. Para generar
predicciones, en otra terminal:

```powershell
cd ia-model
.venv\Scripts\python.exe -m uvicorn serve:app --host 127.0.0.1 --port 8000
```

Comprueba `http://127.0.0.1:8000/health` (`model_loaded: true`) y pulsa «Generar
predicción» en el tablero. El artefacto versionado es `ia-model/artifacts/stgnn.pt`.
Sin IA, el panel muestra un aviso y no inventa valores.

### 4. Desde IntelliJ IDEA

1. `File → Open` → `E:\tesis-laguna-paca` (la raíz) y acepta cargar el proyecto Maven.
2. `Project Structure → Project` → SDK **21** (`D:\Intellij\...\jbr`).
3. `Run → Edit Configurations → + → Maven`:
   - Working directory: `backend`
   - Command line: `-B spring-boot:run -Dspring-boot.run.profiles=demo -Dspring-boot.run.useTestClasspath=true`
   - Environment: `GOOGLE_MAPS_API_KEY=...` (opcional)
4. Run. **No** uses el botón verde sobre `LagunaPacaApplication`: sin el classpath de test
   no encuentra H2 y falla con `Failed to configure a DataSource`.

---

## Panel web

Panel renderizado en el servidor con Thymeleaf, en español, con sistema de diseño propio
(tokens CSS, modo claro/oscuro, sin dependencias externas: funciona sin internet).

| Pantalla | Ruta | Qué muestra |
|---|---|---|
| Tablero | `/panel` | Indicadores de 24 h, mapa de la laguna, tarjetas N1–N4 y gráfico |
| Historial | `/panel/historial` | Muestras paginadas con filtros por estación, fecha, alerta y motivo |
| Alertas | `/panel/alertas` | Muestras con alerta, pendientes por defecto; atender y reabrir con auditoría |
| Predicciones | `/panel` (sección) | Generación a 1, 3 o 6 h, con origen sintético/campo y evaluación contra la medición real |
| Datos (JSON) | `/panel/datos` | El mismo tablero en JSON, para el refresco cada 20 s |

### Mapa

- **Sin clave ni internet:** el servidor dibuja el contorno **real** de la laguna
  (OpenStreetMap `way 26947746`, simplificado a 140 puntos) como SVG, con una marca por
  estación coloreada según el veredicto persistido (`DENTRO` verde, `ALERTA` rojo,
  `SIN_DATOS` gris). Cada marca enlaza al historial filtrado.
- **Con clave (`GOOGLE_MAPS_API_KEY`):** se carga Google Maps y las estaciones se ubican
  en su coordenada real. Si la API no carga, el SVG sigue ahí como respaldo.
- La clave **nunca** se versiona ni viaja en `/panel/datos`; se lee del entorno.
  Restríngela por referente (`http://127.0.0.1:8080/*`, `http://localhost:8080/*`) y
  habilita solo *Maps JavaScript API*.

### Diseño

- Tokens en `backend/src/main/resources/static/css/tokens.css` (fuente única de la paleta,
  compartida con el futuro tema Material 3 de Android).
- Modo oscuro persistido en `localStorage`, aplicado antes del CSS para evitar el destello.
- Accesibilidad: contraste WCAG AA, foco visible, áreas táctiles de 44 px, navegación con
  teclado, `aria-live` en avisos y estados vacíos honestos.
- Gráfico y mapa en SVG hechos a mano: la demo no depende de CDN ni de teselas.

---

## API REST

Contrato en `backend/openapi.yaml`. Autenticación con **JWT** (`Authorization: Bearer …`).

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/auth/token` | Obtiene el JWT con usuario y contraseña |
| `POST` | `/api/v1/telemetry` | Ingesta una muestra (201; 200 si `sample_id` ya existía) |
| `GET` | `/api/v1/telemetry` | Consulta paginada con filtros |
| `POST` | `/api/v1/predictions` | Genera una predicción de pH a 1, 3 o 6 h |
| `GET` | `/api/v1/predictions` | Histórico paginado de predicciones |
| `GET` | `/api/v1/predictions/latest` | Últimas predicciones para el tablero |
| `GET` | `/api/v1/ai/health` | Estado del servicio de IA (200 aunque esté caída) |

Filtros de `GET /api/v1/telemetry`: `node_id`, `from`, `to` (ISO-8601), `alert`, `reason`,
`code` (`PH_HIGH` / `PH_LOW`), `page`, `size`, `sort`.

### Ingesta

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

`sample_id` es opcional y aditivo. Si se repite, la respuesta es **200** con la muestra
original en vez de **201**: el reintento del nodo no duplica la medición. El firmware lo
envía con un `bootId` aleatorio por arranque en frío y una secuencia que solo avanza al
confirmar la entrega.

### Predicciones

```json
{"horizon_hours": 6}
```

Devuelve cuatro filas (una por estación) con `model_run_id`, `predicted_value`,
`model_version`, `data_source` (`synthetic`/`field`) y estado `PENDING`. Cuando llega una
medición real dentro de ±30 min del objetivo, la fila pasa a `EVALUATED` con el valor
observado y el error; el valor previsto **nunca** se reescribe.

Regla ECA-Agua: hay alerta si **pH < 6.5** o **pH > 9.0**. Los valores 6.5 y 9.0 están
dentro del rango y **no** generan alerta. Los motivos se guardan como
`{code, message}` (`PH_LOW`, `PH_HIGH`) para que la lógica no dependa del texto.

### Ingesta MQTT (opcional)

Se habilita con `MQTT_ENABLED=true`; por defecto está apagada para poder arrancar sin
broker. Variables: `MQTT_BROKER_URL`, `MQTT_CLIENT_ID`, `MQTT_USERNAME`,
`MQTT_PASSWORD`, `MQTT_TOPIC`, `MQTT_QOS`, `MQTT_TIMEOUT_MS`. No usa JWT: se autentica
con las credenciales del broker. El payload es el mismo del ESP32.

### Variables de entorno del backend

| Variable | Uso |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL (perfil por defecto) |
| `JWT_SECRET` | Firma de los tokens |
| `APP_USER`, `APP_PASSWORD` | Usuario del panel |
| `MQTT_*` | Ingesta MQTT (opcional) |
| `IA_BASE_URL`, `IA_INTERNAL_TOKEN` | Servicio FastAPI (interno) |
| `IA_CONNECT_TIMEOUT_MS`, `IA_READ_TIMEOUT_MS` | Timeouts de la IA (2 s / 15 s) |
| `GOOGLE_MAPS_API_KEY` | Mapa de Google en el panel (opcional) |

---

## Firmware ESP32

Nodos con **PH-4502C** (pH), **TSW-20M** (turbidez), **DS18B20** (temperatura) y
**Gravity TDS**. Cada despertar mide, publica y vuelve a **deep sleep**.

- `lib/lagunapaca_core/` — lógica portable C++11 (sin Arduino): filtros, calibraciones,
  payload, política de deep sleep y `TelemetryNode`. Es lo que se prueba en la PC.
- `src/` — glue de Arduino: ADC, DS18B20, WiFi, transporte HTTP (`HTTPClient`) y MQTT
  (`256dpi/MQTT` con **QoS 1**).
- `include/secrets.h` — credenciales WiFi y backend (**no se versiona**; copiar de
  `secrets.example.h`).
- El JWT se cachea en memoria RTC (`RTC_DATA_ATTR`) para no autenticar en cada despertar.
- Cada payload lleva `sample_id` (`N1-<bootId>-<secuencia>`), lo que hace idempotente el
  reintento. La secuencia vive en RTC y solo avanza cuando el transporte confirma la
  entrega; el `bootId` se renueva en cada arranque en frío.

```powershell
$env:PATH="C:\Users\USER\AppData\Local\Programs\mingw64\bin;$env:PATH"
pio test -e native        # 38 tests de la lógica, sin hardware
pio run -e esp32dev       # firmware con transporte HTTP (72.3 % de flash)
pio run -e esp32dev-mqtt  # firmware con transporte MQTT (59.8 % de flash)
pio run -e esp32dev -t upload
```

> Los valores de calibración de `include/config.h` son **nominales**: hay que medirlos en
> laboratorio. `HTTPClient` es HTTP plano; en producción requiere TLS (`WiFiClientSecure`)
> y el backend en HTTPS.

---

## Modelo de IA (ST-GNN)

Entrada: tensor `[4 estaciones, T horas, 4 variables]` sobre el grafo de la laguna
(adyacencia gaussiana podada con **MST ∪ KNN**, para que el grafo nunca quede
desconectado). Arquitectura: **TCN causales** (dilatación exponencial) + **GCN** +
**atención multi-cabeza enmascarada por la adyacencia**.

El modelo predice la **corrección** sobre el último pH observado, así que sin entrenar ya
iguala a la línea base de persistencia.

### Resultado del experimento (datos sintéticos, semilla 42)

| Horizonte | R² persistencia | R² ST-GNN | RMSE (pH) | MAE (pH) |
|---|---|---|---|---|
| 1 h | 0.9675 | 0.9817 | 0.0296 | 0.0235 |
| 3 h | 0.8986 | 0.9681 | 0.0391 | 0.0308 |
| 6 h | 0.7403 | **0.9289** | 0.0584 | 0.0445 |

Criterio de la tesis: **R² > 0.90**. Se cumple a 6 h y el modelo bate a la persistencia en
los tres horizontes; cuanto más largo el horizonte, más aporta el modelo.

```powershell
.venv\Scripts\python.exe -m pytest
.venv\Scripts\python.exe train_stgnn.py
.venv\Scripts\python.exe train_stgnn.py --horizons 1 3 6 --save artifacts\stgnn.pt
.venv\Scripts\python.exe train_stgnn.py --csv datos\campo.csv --max-gap-hours 2 --save artifacts\stgnn-field.pt
.venv\Scripts\python.exe -m uvicorn serve:app --host 127.0.0.1 --port 8000
```

El artefacto `artifacts/stgnn.pt` (~100 KB) guarda pesos, configuración, escalador,
adyacencia y métricas. El servicio FastAPI lo carga al arrancar y expone `/health`,
`/model` y `/predict`; sin artefacto, `/predict` responde 503. El detalle del contrato
está en `docs/SDD/10-contrato-ia.md`.

### Datos de campo

`lagunapaca_gnn/field_data.py` convierte un CSV en formato largo
(`timestamp,node_id,ph,turbidity_ntu,temp_c,tds_ppm`) en la serie horaria que consume
la ST-GNN:

- el `timestamp` debe incluir zona horaria (se convierte a UTC);
- varias muestras dentro de la misma hora se promedian;
- se usa el rango en que **todas** las estaciones tienen datos, sin extrapolar;
- por defecto (`--max-gap-hours 0`) no se interpola ningún hueco; si se permite, el
  reporte dice cuántos instantes se rellenaron;
- el artefacto se marca con `data_source = field` y versión
  `stgnn-field-s<semilla>-i<ventana>-e<épocas>-d<huella>`, para que dos campañas
  distintas no se pisen.

No se incluye ningún CSV de campo real: las pruebas usan *fixtures*. El R² de un CSV
describe ese conjunto, no la laguna en general.

> ⚠️ `dataset.py` **simula** la dinámica (memoria AR(1), ciclo diario, correlación
> espacial). El R² demuestra que la arquitectura aprende una dinámica espacio-temporal,
> **no** que prediga la laguna real. Faltan datos de campo.

---

## Pruebas

| Módulo | Comando | Tests |
|---|---|---|
| Backend | `mvn test` (con `JAVA_HOME` en JDK 21) | **138** |
| Firmware | `pio test -e native` | **38** |
| IA | `.venv\Scripts\python.exe -m pytest` | **172** |
| **Total** | | **348** |

- Cobertura del backend (JaCoCo): **91.5 %** de instrucciones y **70.0 %** de ramas.
- No hay Docker ni Testcontainers: la persistencia se prueba con **H2**.
- El backend incluye tests de integración con contexto completo (arranque, seguridad,
  panel, mapa y predicciones), no solo pruebas por capa.
- `train_stgnn.py` devuelve código de salida 1 si no se cumple el criterio, para encadenarlo
  al CI.
- **No se ejecutaron** `docker compose build/up`, SonarQube, la CI en un runner ni
  pruebas en hardware. La V5 sí se validó en PostgreSQL 16.9 embebido y
  `docker compose config`. Además, `PostgresMigrationIT` (1 test) ejecuta las
  migraciones contra PostgreSQL real y corre en el job `backend-postgres` del CI.
  Se declara en `docs/SDD/26-limitaciones.md`.

---

## Estructura del repositorio

```
.
├── backend/                 Spring Boot + API REST + panel web
│   ├── src/main/java/       dominio, ingesta, alertas, predicciones, seguridad, panel
│   ├── src/main/resources/  templates, css, js, geo/, migraciones Flyway V1–V5
│   ├── src/test/java/       138 tests (unitarios, slice e integración)
│   ├── tools/               simulador de nodo y fetch_laguna.py
│   ├── Dockerfile
│   └── openapi.yaml         contrato de la API
├── iot-firmware/            ESP32 (PlatformIO)
│   ├── lib/lagunapaca_core/ lógica portable + 38 tests nativos
│   └── src/                 glue de Arduino, hardware y red
├── ia-model/                ST-GNN (Python 3.12, entorno .venv propio)
│   ├── lagunapaca_gnn/      estaciones, grafo, ventanas, modelo, registro, API
│   ├── artifacts/           artefacto versionado + cómo regenerarlo
│   ├── tests/               172 tests
│   ├── serve.py             uvicorn
│   ├── Dockerfile
│   └── train_stgnn.py       experimento del criterio de la tesis
├── android-app/             (pendiente)
├── deploy/                  configuración de Mosquitto
├── docs/SDD/                documentación de diseño (versionada)
├── docs/                    tesis y libros de referencia (no versionado)
├── compose.yaml             PostgreSQL + backend + IA/MQTT opcionales
├── .github/workflows/ci.yml CI
└── AGENTS.md                decisiones técnicas y comandos detallados
```

---

## Decisiones que conviene no deshacer

- **La regla ECA-Agua vive solo en el backend.** El firmware publica y el panel muestra el
  veredicto persistido; reevaluar el pH en otro sitio crearía dos reglas que podrían divergir.
- **Dos cadenas de seguridad:** panel con sesión + CSRF (navegador) y API con JWT sin estado
  (ESP32). Un JWT no abre el panel.
- **El panel lee del repositorio, no de la API REST** (evita un JWT en `localStorage`).
- **Atender una alerta no cambia el veredicto.** Es otra dimensión, con auditoría aparte
  (`acknowledged_at`, `acknowledged_by`, `acknowledge_note`).
- **Las fechas del historial son de Lima**, no UTC.
- **El mapa no depende de teselas** por defecto: la demo funciona sin internet.
- **Las coordenadas de las estaciones son provisionales** (derivadas de OpenStreetMap).
  Se reemplazan en `ia-model/lagunapaca_gnn/stations.py` y se regenera el mapa con
  `backend/tools/fetch_laguna.py`; el grafo y el mapa no pueden discrepar.
- **Una predicción no es una medición.** Vive en `prediction`, declara su `data_source` y
  se evalúa después; nunca cambia el veredicto ECA-Agua ni se escribe en la tabla de muestras.
- **La IA no es una dependencia de la ingesta.** Si FastAPI está caído, se registra la
  muestra y se aplica la alerta igual; solo se degrada la predicción.
- **El reintento es idempotente.** `sample_id` + índice único parcial; un duplicado responde
  200, no crea otra fila.
- **El modelo se sirve desde un artefacto**, no se entrena al arrancar. Sin artefacto,
  `/predict` responde 503 y no se inventan valores.

El detalle de cada decisión (con el porqué y los contrastes medidos) está en `AGENTS.md` y
en los ADR de `docs/SDD/`.

---

## Pendientes

1. **Datos de campo**: reemplazar las estaciones provisionales y reentrenar el ST-GNN con
   mediciones reales. El cargador `field_data.py` y el entrenamiento con `--csv` ya existen
   y están probados; falta la campaña de muestreo. Es el pendiente científico más importante.
2. **Calibración de sensores** en laboratorio con soluciones buffer.
3. **Validar el despliegue**: `docker compose build/up` y el workflow de CI en un
   runner. La V5 ya se validó en PostgreSQL 16.9 embebido y `compose config`;
   falta el contenedor real y construir las imágenes.
4. **TLS**: firmware con `WiFiClientSecure`, backend en HTTPS y MQTT en 8883.
5. **SonarQube** ejecutado sobre un servidor real (configuración lista).
6. **App Android**: tema Material 3 con la misma paleta, login, tablero con mapa, alertas
   y notificaciones push (FCM).
7. **Notificaciones**: hoy la alerta solo se ve en el panel y en la API.
8. **Rotar la clave de Google** compartida durante el desarrollo y restringirla por referente.
9. Capítulos de la tesis y despliegue en producción.

No se usa Neo4j: con cuatro estaciones, la adyacencia se calcula en `ia-model` y vive en el
artefacto. Se justificaría con decenas de cuerpos de agua y relaciones heterogéneas.

---

## Documentación

- `docs/SDD/` — documentación de diseño del sistema (empieza por
  [`docs/SDD/README.md`](docs/SDD/README.md)): visión, requisitos, arquitectura,
  modelo de datos, contratos, seguridad, despliegue, pruebas, trazabilidad, riesgos,
  deuda técnica, manuales, limitaciones, evidencias y 9 ADR.
- `AGENTS.md` — decisiones técnicas, comandos y totales de pruebas (mantenerlo al día).
- `backend/openapi.yaml` — contrato de la API.
- `docs/` — tesis y libros de referencia (**no versionado**, ~257 MB; `docs/SDD/` es la
  excepción versionada).
