# AGENTS.md

Repositorio de tesis: **"Sistema de software e IoT con Redes Neuronales de Grafos (GNN) para el monitoreo de la contaminación en la Laguna de Paca, Junín 2026"** (autor: Ronald Arroyo Breña / RONY).

## Estado actual (importante)

- `backend/` está **completo**: Spring Boot + ingesta de telemetría (HTTP y MQTT) **idempotente** (`sample_id`), alertas ECA-Agua, consulta histórica, **predicciones del ST-GNN persistidas y evaluadas**, Swagger UI, **API JSON del panel** y simulador del nodo. **145 tests.**
- `frontend/` es una **SPA React 19 + TypeScript + Vite** (React Router): acceso, tablero, mapa, historial, alertas y predicciones. **16 tests** con Vitest + Testing Library. El build se escribe en `backend/src/main/resources/static/panel`.
- `iot-firmware/` tiene el **núcleo portable** del nodo ESP32, transporte HTTP y MQTT QoS 1, Deep Sleep y `sample_id`; **38 tests nativos** y ambas variantes compilan.
- `ia-model/` está **completo de extremo a extremo**: estaciones, grafo, ventanas, modelo ST-GNN (TCN + GCN + Attention), dataset sintético, normalización, entrenamiento, **carga de CSV de campo**, **artefacto versionado** (`artifacts/stgnn.pt`) y **servicio FastAPI** (`serve.py`). **172 tests con pytest.** El criterio de la tesis se cumple con datos sintéticos (`R² = 0.9289` a 6 h, contra `0.7403` de persistencia).
- `docs/SDD/` es la documentación de diseño (versionada; índice en `docs/SDD/README.md`). El resto de `docs/` es material de referencia y **no se versiona**.
- Docker/Compose, CI y SonarQube están **configurados pero no ejecutados** en esta máquina (no hay motor Docker ni servidor Sonar). `docker compose config` sí se validó y las migraciones V1–V5 se ejecutaron en PostgreSQL 16.9 embebido. `android-app/` sigue **vacío**.

## Mapa de componentes (stack planificado)

La fuente de verdad es `docs/Plantilla Plan Tesis Continental Sistemas_RONY.docx`.

- `iot-firmware/` — firmware ESP32 (C++/Arduino): PH-4502C, TSW-20M, DS18B20 y Gravity TDS. Publica el payload JSON al **backend propio** (HTTP POST y/o MQTT) y entra en Deep Sleep. Estructura: `lib/lagunapaca_core/` (lógica portable y testeable) y `src/main.cpp` (glue de Arduino).
- `backend/` — Java 21 / Spring Boot 3.2; API REST con JWT, PostgreSQL 16 + Flyway, Neo4j AuraDB y **API JSON del panel**. Sirve el build de React desde el mismo JAR.
- `frontend/` — React 19 + TypeScript + Vite; SPA del panel (acceso, tablero, mapa, historial y alertas). El build se copia a `backend/src/main/resources/static/panel`.
- `ia-model/` — Python 3.12 / PyTorch Geometric; modelo ST-GNN sobre el grafo de estaciones de la laguna (adyacencia gaussiana + MST/KNN); se evalúa con R², RMSE y MAE.
- `android-app/` — Kotlin / Jetpack Compose (MVVM + unidirectional data flow); alertas vía Firebase Cloud Messaging.

Metodología: Scrum (sprints de 2 semanas) para el software; CRISP-DM para el modelo.

## Backend (`backend/`) — comandos

Toolchain: **Java 21** y **Maven 3.9.9**, ninguno de los dos está en el PATH del sistema.

- **JDK 21 requerido.** El intrínseco `D:\JDK` es Java 26 y **rompe** Spring Boot 3.2. Usar el JBR de IntelliJ:
  `$env:JAVA_HOME="D:\Intellij\IntelliJ IDEA 2025.2.1\jbr"`
- Maven (ya descargado en la caché de wrapper):
  `C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.9\977a63e90f436cd6ade95b4c0e10c20c\bin\mvn.cmd`

Comandos (desde `backend/`, con `JAVA_HOME` ya definido):

| Objetivo | Comando |
|---|---|
| Todos los tests | `mvn test` |
| Un solo test | `mvn -Dtest=EcaWaterQualityRuleTest test` |
| Empaquetar (sin tests) | `mvn -DskipTests package` |

Notas de testing:
- **No hay Docker** en la máquina → sin Testcontainers. La persistencia se prueba con **H2** (`@DataJpaTest` sobreescribe `spring.flyway.enabled=false`) porque la migración Flyway es específica de PostgreSQL.
- El arranque real (`mvn spring-boot:run`) **requiere PostgreSQL 16** y datos en `application.yml` (variables `DB_*`, `JWT_SECRET`, `APP_USER`, `APP_PASSWORD`).
- `@WebMvcTest` usa `@AutoConfigureMockMvc(addFilters = false)`: por eso `JwtAuthenticationFilter` **no** es `@Component` y se registra como `@Bean` en `SecurityConfig`.

Ingesta MQTT (segunda vía, además del `POST /api/v1/telemetry`):
- Se habilita con `MQTT_ENABLED=true` (por defecto `false`), así que la app arranca sin broker.
- Variables: `MQTT_BROKER_URL`, `MQTT_CLIENT_ID`, `MQTT_USERNAME`, `MQTT_PASSWORD`, `MQTT_TOPIC`, `MQTT_QOS`, `MQTT_TIMEOUT_MS`.
- No usa JWT: se autentica con las credenciales del broker. El payload es el mismo del ESP32.
- Los tests de MQTT no necesitan broker: se prueba el *handler* aislado (`TelemetryMqttHandlerTest`).

## Demostración: Swagger UI y simulador del nodo

Documentación interactiva de la API (**Swagger UI**) en `http://localhost:8080/swagger-ui.html`.
El contrato sigue siendo `backend/openapi.yaml` (fuente de verdad del SDD); Swagger UI lo genera
desde las anotaciones y `OpenApiConfig`. Botón **Authorize** → usuario `admin` / `admin123`.

**No hace falta PostgreSQL para una demostración**: el perfil `demo` usa H2 en memoria.

| Objetivo | Comando (desde `backend/`) |
|---|---|
| Arrancar en modo demo (H2 en memoria) | `mvn spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.useTestClasspath=true"` |
| Simulador del nodo (10 muestras) | `python tools/simulate_node.py` |
| Simulador: forzar alertas | `python tools/simulate_node.py --scenario alert --count 6` |
| Simulador: bordes 6.5 y 9.0 | `python tools/simulate_node.py --scenario limit` |
| Tests del simulador | `python -m unittest` (desde `backend/tools/`) |

- `useTestClasspath=true` es necesario porque H2 está en ámbito `test`: así se arranca el perfil
  `demo` sin meter el driver en el artefacto de producción.
- El perfil `demo` **no es el entorno real**: el esquema lo genera Hibernate desde las entidades,
  Flyway no se ejecuta y los datos viven en memoria. PostgreSQL sigue siendo la fuente de verdad.
- Escenarios del simulador: `normal`, `alert`, `limit` (pH exactamente 6.5 y 9.0, que **no** son
  alerta) y `mixed`. Solo generan datos: quien decide si hay alerta es el backend.

**Lección aprendida (importante):** el primer test que cargó el contexto completo
(`ApiIntegrationTest`) descubrió dos bugs que 32 tests de slice no podían ver:
1. `TelemetryService` tenía dos constructores sin `@Autowired`: **la aplicación no arrancaba**.
2. `GET` protegido sin token devolvía **403** en vez del **401** que exige `openapi.yaml`.

Los tests por slice (`@WebMvcTest` con `addFilters = false`, servicios instanciados a mano) no cubren
ni el arranque ni la seguridad. Antes de agregar funcionalidad, mantener este test de integración.


## Panel web (`frontend/` + `backend/`) - SPA React

El panel es una **SPA React 19 + TypeScript + Vite** con React Router. Spring Boot la sirve
desde el mismo JAR: el build de Vite se escribe en
`backend/src/main/resources/static/panel` (`base: '/panel/'`). Ya **no hay Thymeleaf** ni
plantillas renderizadas en el servidor.

| Objetivo | Comando / URL |
|---|---|
| Abrir el panel (perfil `demo`) | `http://localhost:8080/panel` |
| Formulario de acceso | `http://localhost:8080/login` -> `admin` / `admin123` |
| Desarrollo con recarga | `cd frontend; npm run dev` (abrir `http://localhost:5173/panel`, proxy a 8080) |
| Tests del frontend | `cd frontend; npm test` (Vitest + Testing Library) |
| Build al JAR | `cd frontend; npm run build` |
| Tokens de diseño | `frontend/src/styles/tokens.css` |
| Componentes y páginas | `frontend/src/components/`, `frontend/src/pages/` |
| Cliente de API/CSRF | `frontend/src/api/client.ts` |

El build se versiona para que `mvn spring-boot:run` y la demo funcionen sin Node. Si se
modifica React hay que ejecutar `npm run build` antes de arrancar/empaquetar el backend.

### Dos superficies, dos mecanismos de autenticación

`SecurityConfig` tiene **dos cadenas de filtros**:

1. `@Order(1)` - panel (`/`, `/panel/**`, `/login`, `/logout`): **sesión con formulario**
   (`JSESSIONID`) y **CSRF activo**. Es la única forma razonable de proteger un navegador:
   con el JWT en `localStorage` el token quedaría expuesto a XSS, y sin CSRF el formulario
   de acceso sería falsificable.
2. `@Order(2)` - API (`/api/**`): **stateless con JWT**, sin cookies y sin CSRF, porque su
   cliente es el ESP32 y no un navegador.

Consecuencia deliberada: **un JWT válido no abre el panel**. Hay tests que lo fijan
(`PanelIntegrationTest.elPanelNoAceptaJwt`). El orden importa: si la cadena de la API fuera
primero, su `anyRequest().authenticated()` respondería el panel con un 401 en JSON.

Detalles que no se deben romper:

- `/panel/api/**` responde **401 JSON** si la sesión caducó; las rutas de página redirigen a
  `/login`. Hacen falta **dos** `defaultAuthenticationEntryPointFor`: con una sola entrada,
  Spring Security la aplica a todas las peticiones e ignora su matcher.
- `/logout` pertenece a la cadena del panel. Si se deja fuera, la cadena stateless lo trata
  como una petición sin JWT y el cierre de sesión responde 401.
- `/login`, `/panel/csrf`, `/panel/assets/**` y `/panel/favicon.svg` son públicos. El resto
  del panel exige rol `ADMIN`.
- `GET /panel/csrf` entrega `{token, headerName, parameterName}` a React. El login de la SPA
  hace POST a `/login` con `_csrf` y considera éxito cuando la URL final es `/panel`.
- `SpaController` devuelve `static/panel/index.html` como `Resource` para `/login`, `/panel`,
  `/panel/`, `/panel/historial` y `/panel/alertas`; la raíz `/` redirige a `/panel`. No se
  usa `forward:` porque MockMvc debe recibir el HTML y no hace falta una segunda pasada.
- La API del panel (`PanelApiController`) lee de `PanelDashboardService`,
  `PanelHistorialService`, `PanelAlertasService` y `PredictionService`; **no** llama a
  `/api/v1` ni duplica la regla ECA-Agua.

### Endpoints JSON del panel

| Método | Ruta | Uso |
|---|---|---|
| `GET` | `/panel/api/sesion` | Usuario, rol, estaciones y clave opcional de Google |
| `GET` | `/panel/api/datos` | Tablero completo (alias antiguo: `/panel/datos`) |
| `GET` | `/panel/api/historial` | Filtros, orden y paginación |
| `GET` | `/panel/api/alertas` | Alertas, contadores y filtros |
| `POST` | `/panel/api/alertas/{id}/atender` | Sesión + CSRF; guarda usuario, hora y nota |
| `POST` | `/panel/api/alertas/{id}/reabrir` | Sesión + CSRF; borra la auditoría |
| `POST` | `/panel/api/predicciones` | Sesión + CSRF; cuerpo `{"horizonte": 1\|3\|6}` |

`/panel/datos` se conserva como alias del tablero. `/panel/api/**` no acepta JWT.

### Tablero con datos reales

| Elemento | De dónde sale |
|---|---|
| Indicadores (muestras, alertas, estaciones, hora) | ventana de **24 h** |
| Tarjetas N1-N4 | **última muestra de cada estación**, sin límite de antigüedad |
| Badge de estado | campo `alert` **persistido** por la ingesta, no se reevalúa |
| Gráfico | puntos de las últimas 24 h, una línea por estación |
| Predicciones | tabla `prediction`, con origen `synthetic`/`field` y evaluación posterior |

Decisiones que conviene no deshacer:

- **El estado no se recalcula en React.** El backend envía `estado`, `etiqueta`, `motivos` y
  `clase`; el frontend solo pinta. Si React reevaluara el pH habría dos reglas ECA-Agua que
  podrían divergir.
- **Solo tres estados**: `SIN_DATOS`, `DENTRO`, `ALERTA`. No existe un "cerca del límite".
- **La tarjeta no aplica la ventana de 24 h.** Si N2 lleva tres días callado se muestra su
  último dato con "hace 3 días"; es más honesto que fingir que nunca midió.
- **La antigüedad se escribe en palabras** ("hace 3 minutos") y el backend la formatea con
  `Locale.ROOT`; el JSON reutiliza esos textos.
- **El gráfico es SVG hecho a mano** en React, sin Chart.js/CDN: la demo funciona sin
  internet. Si la ventana dura menos de 30 min, el eje se abre hasta 30 min para que las
  horas no se aplasten.
- **Refresco cada 20 s** contra `/panel/api/datos`, en pausa si la pestaña está oculta. El
  botón "Actualizar" hace lo mismo y confirma con un toast. En React se usa un `ref` para
  evitar que el intervalo se recree en cada render.

### Mapa de la laguna

El tablero dibuja el contorno **real** de la laguna (no un esquema) con las cuatro estaciones
encima. El polígono sale de OpenStreetMap (`way 26947746`, 194 puntos, simplificado a 140 con
Douglas-Peucker) y se guarda como GeoJSON en
`backend/src/main/resources/geo/laguna-paca.json`: el panel no descarga teselas.

| Objetivo | Comando (desde la raíz) |
|---|---|
| Regenerar el contorno y las coordenadas | `ia-model\.venv\Scripts\python.exe backend\tools\fetch_laguna.py` |
| Arrancar con el mapa de Google | definir `GOOGLE_MAPS_API_KEY` antes de `spring-boot:run` |

**Mapa de Google (opcional).** Con `GOOGLE_MAPS_API_KEY` definida, React carga Google Maps;
si la API no carga (sin internet, clave restringida o cuota), el contorno SVG sigue ahí y la
tarjeta no queda vacía. La clave **nunca** se versiona: viaja en `/panel/api/sesion` y no en
`/panel/api/datos`.

Decisiones que conviene no deshacer:

- **La clave es de navegador y debe restringirse por referente** en Google Cloud
  (`http://127.0.0.1:8080/*`, `http://localhost:8080/*` y, en producción, el dominio real),
  habilitando solo *Maps JavaScript API*.
- **Sin clave no se pide nada a Google**: el SVG es el camino por defecto.
- **El contenedor de Google se muestra antes de construir el mapa.** Google mide el div al
  crear el `Map`; si está oculto recibe 0x0 y no dibuja teselas.
- **No se usa el parámetro `callback` de la API** para inicializar: no se dispara de forma
  fiable cuando el `<script>` se inserta dinámicamente. Se usa el evento `load`.
- **En SVG no existe el atributo global `hidden`**: el contorno se oculta con
  `display: none`; las marcas usan los tokens CSS leídos con `getComputedStyle`.
- **El color es el veredicto persistido**, igual que en las tarjetas: verde `DENTRO`, rojo
  `ALERTA`, gris `SIN_DATOS`.
- **Cada punto enlaza al historial filtrado** (`/panel/historial?estacion=N2`).
- **Las coordenadas de las estaciones no se copian.** `fetch_laguna.py` las importa de
  `ia-model/lagunapaca_gnn/stations.py`, que sigue siendo la fuente de verdad del grafo.
- **La proyección es equirectangular corregida por `cos(latitud)`** y la caja incluye las
  estaciones, no solo el lago.
- **En móvil se agrandan las etiquetas** dentro del SVG (`font-size` 19/16 px).

### Historial con filtros y paginación

`GET /panel/api/historial` lista las muestras de la más reciente a la más antigua, 20 por
página. Filtros: estación, desde/hasta (días de calendario en **hora de Lima**) y solo
alertas; además busca texto dentro del motivo que redactó el backend.

- **No hay una segunda consulta.** El panel llama a `TelemetryService.search`, que ya aplica
  `WaterQualitySampleSpecifications`. Duplicar los `LIKE`/rangos crearía dos formas de
  filtrar que podrían divergir.
- **Los filtros llegan como texto, no como `LocalDate`.** Una fecha mal tecleada avisa y
  conserva el resto del historial en vez de responder 400.
- **Un rango invertido no se aplica y se avisa** en vez de romper o devolver una tabla vacía.
- **Las fechas son de Lima, no UTC.** `recorded_at` es un instante; interpretar el día en UTC
  desplazaría el corte cinco horas. Hay un test que fija `2026-10-18T04:30Z` = 17/10 23:30
  en Lima.
- **Una página fuera de rango se ajusta a la última** (se vuelve a consultar).
- **La búsqueda vive en la URL** (`useSearchParams`): se puede guardar, compartir y el botón
  "atrás" funciona. La paginación conserva todos los filtros.
- La tabla se desplaza en horizontal dentro de su tarjeta en móvil.

### Gestión de alertas

`GET /panel/api/alertas` lista las muestras **con alerta** ECA-Agua. Por defecto muestra las
**pendientes**, porque es la pantalla de lo que exige acción. Filtros: estación, estado
(pendientes/atendidas/todas) y desde/hasta en hora de Lima.

- **Atender no cambia el veredicto.** `alert` y sus motivos siguen siendo lo que decidió
  `EcaWaterQualityRule` al ingerir. La atención se guarda aparte:
  `acknowledged_at`, `acknowledged_by` y `acknowledge_note` (migración V4).
- **Solo las muestras con alerta se pueden atender.** Una muestra dentro del rango o un id
  inexistente responde 404.
- **Es un POST con CSRF**, no un enlace: cambia estado persistido y registra al usuario. El
  nombre sale de la sesión (`Principal`), nunca del formulario.
- **Doble envío no pisa la auditoría.** Si la alerta ya estaba atendida, el segundo POST no
  reescribe quién la atendió primero.
- **Los filtros se conservan al atender y al reabrir** en la URL de React; no se acepta una
  URL de retorno del cliente (sería un redirect abierto).
- **Tres contadores, cada uno con su ventana escrita en la etiqueta.** Pendientes no caduca;
  "atendidas (24 h)" corta por la hora de atención; "alertas (24 h)" corta por la hora de
  medición.
- **El tablero refleja la atención** con una línea "Atendida por ..." en la tarjeta, sin
  añadir un cuarto estado.
- **La API REST pública no expone la atención.** `openapi.yaml` no cambia;
  `TelemetryResponse` sigue igual. Publicarlo afectaría al cliente Android y es una decisión
  aparte.

### Predicciones e IA (contrato que no se debe romper)

- Paquete `prediction/`: entidad `Prediction`, `PredictionService`,
  `PredictionWindowBuilder`, `PredictionEvaluator`, `AiClient` (`java.net.http`,
  2 s de conexión / 15 s de lectura), `AiProperties`.
- `POST /api/v1/predictions` genera 1, 3 o 6 h para las 4 estaciones y persiste 4 filas
  `PENDING` con el mismo `model_run_id`. Otro horizonte responde 400.
- La evaluación ocurre al llegar una muestra real dentro de +/-30 min del `target_at`, en
  `REQUIRES_NEW`, y **nunca rompe la ingesta**. El valor previsto no se reescribe; se guardan
  observado, error y fecha.
- Si la IA está caída: 503; respuesta inválida: 502; ventana incompleta: 409.
  **No se inventa ninguna predicción.**
- El panel tiene `POST /panel/api/predicciones` (sesión + CSRF) y muestra origen
  `synthetic`/`field`, estado y error. No cambia el badge ECA-Agua.
- `PanelPrediccionesIntegrationTest` fija sesión, CSRF, IA caída, evaluación y estado vacío.

### Reglas de interfaz que el código hace cumplir

- **Todo el texto visible en español**; los identificadores técnicos (CSS, JS, TS, Java) en
  inglés.
- **Modo claro por defecto** y oscuro opcional, persistido en `localStorage` (`laguna-tema`).
  Un script mínimo en `index.html` aplica el tema antes del CSS para evitar el destello;
  React solo lo alterna y lo persiste.
- **Iconos siempre acompañados de texto** (Lucide incrustado como SVG con
  `aria-hidden="true"`); nunca sustituyen a una etiqueta.
- **Áreas táctiles de 44 px**, anillo de foco visible (`:focus-visible`), enlace "Saltar al
  contenido", `aria-current="page"`, `aria-expanded`, `aria-pressed` y `aria-live` en los
  toasts.
- **Responsive**: barra lateral fija de 264 px en >=1024 px, colapsable a 76 px; en móvil es
  un cajón con velo que se cierra con `Escape` devolviendo el foco al botón que lo abrió.
- **Estados vacíos honestos**: lo que todavía no existe se muestra como "Pronto"
  deshabilitado, en vez de un enlace que lleva a un 404.
- `prefers-reduced-motion` desactiva las animaciones.
- **El panel no reutiliza los controladores de la API**: el contrato de `openapi.yaml` no
  debe arrastrar responsabilidades de presentación.

### Tests del panel y del frontend

`PanelIntegrationTest` (contexto completo, H2) fija el contrato: la raíz redirige al panel,
`/panel` sin sesión redirige a `/login`, `/login` devuelve el shell React (`id="root"` y
assets de Vite), los estáticos son públicos, el login válido entra, el inválido vuelve con
`?error`, el POST exige CSRF, un JWT no abre el panel, la API JSON responde 401 sin sesión,
`/panel/csrf` es público y `/logout` invalida la sesión.

`PanelDashboardIntegrationTest` fija el tablero: las cuatro estaciones existen sin datos, la
tarjeta muestra la muestra **más reciente**, una alerta se pinta con el motivo del backend, el
JSON trae las cuatro estaciones con su estado y la serie, exige sesión y ni la serie ni los
contadores incluyen muestras de más de 24 h.

`PanelHistorialIntegrationTest` fija orden, paginación de 20 en 20, ajuste de página fuera de
rango, filtros por estación, día **de Lima**, solo alertas y texto del motivo; avisa sin
romper ante rango invertido o fecha inválida.

`PanelAlertasIntegrationTest` fija el listado solo con alerta, pendientes por defecto, filtros,
contadores, CSRF, auditoría, 404 y reapertura; el veredicto ECA-Agua no cambia.

`PanelMapaIntegrationTest` fija el contorno, una marca por estación, puntos dentro del lienzo,
norte arriba del sur, coordenadas reales, color persistido, texto accesible y ausencia de
teselas/scripts externos. `PanelMapaGoogleIntegrationTest` fija que la clave llega solo por
`/panel/api/sesion`, no se incrusta en el HTML ni viaja en `/panel/api/datos`.

Tests del frontend (Vitest + Testing Library): `estado.test.ts` (etiquetas y clases),
`client.test.ts` (CSRF, 401, reintento por 403, login), `components.test.tsx` (badge, mapa,
paginación, gráfico) y `pages.test.tsx` (historial, alertas y tablero con datos simulados).

**Total del backend: 145 tests.** Cobertura JaCoCo tras la migración: **91.7 %** de
instrucciones y **68.8 %** de ramas sobre 89 clases. Se reporta como indicador, no como meta.

## Firmware IoT (`iot-firmware/`) — comandos

Toolchain: **PlatformIO Core 6.2.0** (sobre Python 3.12) y un compilador C++ host (**MinGW-w64 / g++ 16.2.0**). Ninguno está en el PATH del sistema.

- Python: `C:\Users\USER\AppData\Local\Programs\Python\Python312\python.exe`
- PlatformIO: `C:\Users\USER\AppData\Local\Programs\Python\Python312\Scripts\pio.exe`
- g++ (solo para los tests nativos): `C:\Users\USER\AppData\Local\Programs\mingw64\bin`

Comandos (desde `iot-firmware/`, con `g++` ya en el PATH):

| Objetivo | Comando |
|---|---|
| Tests de la lógica portable (sin hardware) | `pio test -e native` |
| Compilar el firmware para la placa (transporte HTTP) | `pio run -e esp32dev` |
| Compilar la variante MQTT | `pio run -e esp32dev-mqtt` |
| Subir a la placa (requiere hardware) | `pio run -e esp32dev -t upload` |

```powershell
$env:PATH="C:\Users\USER\AppData\Local\Programs\mingw64\bin;$env:PATH"
& "C:\Users\USER\AppData\Local\Programs\Python\Python312\Scripts\pio.exe" test -e native
```

Notas de testing:
- Los tests corren en la **PC**, no en la placa: `lib/lagunapaca_core/` es C++ puro (sin Arduino) y los sensores se simulan con dobles de prueba. Es la única forma de tener RED→GREEN sin hardware.
- `-DUNITY_INCLUDE_DOUBLE` está en `platformio.ini` porque `unity.c` se compila como una unidad aparte; sin el flag, `UnityAssertDoublesWithin` queda sin enlazar.
- Instalación de la toolchain (se hizo con `winget`): `BrechtSanders.WinLibs.POSIX.UCRT` y `Python.Python.3.12`, más `pip install platformio`.

Estructura y credenciales:
- `lib/lagunapaca_core/` — lógica portable (C++ sin Arduino): filtros, calibraciones, payload, política de Deep Sleep, `TelemetryNode`. Puertos en `include/lagunapaca/TelemetryPorts.h`.
- `src/` — glue de Arduino: `main.cpp` (orquestación + Deep Sleep), `src/hardware/` (ADC, DS18B20, transporte HTTP), `src/net/` (WiFi).
- `include/config.h` — pines, intervalos y calibraciones. **Los valores de calibración son nominales**: hay que medirlos en laboratorio.
- `include/secrets.h` — WiFi y backend (**no se versiona**, está en `.gitignore`). Copiar de `include/secrets.example.h`. Si falta, el firmware compila con credenciales de ejemplo y emite un `#warning`.
- **El core debe seguir siendo C++11**: el framework Arduino del ESP32 compila con `gnu++11` (los tests nativos usan `gnu++17`). Una `struct` con inicializadores de miembro deja de ser agregado en C++11 y rompe `TelemetryNodeConfig{...}`; por eso esa estructura usa constructor explícito.
- El nodo se autentica con `POST /api/v1/auth/token` y **cachea el JWT en memoria RTC** (`RTC_DATA_ATTR`) para no re-autenticar en cada despertar. Sin NTP, el vencimiento se lleva de forma monótona sumando el tiempo dormido.
- **`sample_id`**: el nodo envía `N1-<bootId 8 hex>-<secuencia>` (`formatSampleId`, en `SampleIdentity.h`). El `bootId` se genera con `esp_random()` en cada arranque en frío y vive en RTC; la secuencia también vive en RTC y **solo avanza cuando el transporte confirma**. Así el reintento reutiliza el id y un corte de luz no choca con ids anteriores. `TelemetryNode::runOnce(const char* sampleId)` mantiene el payload histórico si el puntero es nulo.
- `HTTPClient` es HTTP plano: en producción requiere TLS (`WiFiClientSecure` + huella/bundle de CA) y el backend en HTTPS.
- Flash al **72.3 %** (947 261 B de 1 310 720 B) con `WiFi` + `HTTPClient` + `ArduinoJson`: si se agrega OTA, cambiar el esquema de particiones (`board_build.partitions`).
- El `#warning` de `secrets.h` hace que PowerShell devuelva salida con código 1 aunque el build sea `SUCCESS` (gcc escribe el warning en stderr).
- **Canal de telemetría**: HTTP POST es el default; MQTT se activa con `-DLAGUNA_TELEMETRY_MQTT` (entorno `esp32dev-mqtt`) o descomentando el `#define` en `include/config.h` (para Arduino IDE). Ambos implementan `laguna::TelemetryTransport`, así que el núcleo no cambia.
- La vía MQTT usa `256dpi/MQTT` (arduino-mqtt) con **QoS 1**, coherente con `MQTT_QOS=1` del backend: el nodo solo da la muestra por entregada cuando llega el PUBACK. PubSubClient **no** sirve para esto porque solo publica con QoS 0.
- Tópico MQTT: `laguna-paca/telemetry`, debe coincidir con `app.mqtt.topic`. El canal MQTT **no usa JWT**: se autentica con las credenciales del broker (nuevas variables `MQTT_BROKER_HOST`, `MQTT_USERNAME`, `MQTT_PASSWORD` en `secrets.h`).
- El client id MQTT se construye con `buildMqttClientId()` (núcleo, testeado): `lp-<8 hex de la MAC>-<nodeId>`, recortado a **23 bytes** porque MQTT 3.1.1 solo obliga al broker a aceptar ese largo. La MAC va antes del node id para que el recorte nunca sacrifique la unicidad.
- Presupuesto de flash: **HTTP 72.3 %** (947 261 B) vs **MQTT 59.8 %** (784 321 B). La vía HTTP arrastra `HTTPClient` + `WiFiClientSecure` + bundle de CA. `build_src_filter` excluye de cada entorno el transporte que no usa.
- **38 tests nativos** (32 previos + 6 de `sample_id` y del payload con id). Los tests del nodo cubren también que una muestra inválida **no** se envíe con id.

## Modelo de IA (`ia-model/`) — comandos

Toolchain: **Python 3.12.10** con **entorno virtual propio** en `ia-model/.venv` (no se instala nada en el Python global). Se creó con:

```powershell
& "C:\Users\USER\AppData\Local\Programs\Python\Python312\python.exe" -m venv ia-model\.venv
ia-model\.venv\Scripts\python.exe -m pip install numpy pytest
# PyTorch CPU no está en PyPI con el sufijo "+cpu": se pide a su propio índice.
ia-model\.venv\Scripts\python.exe -m pip install torch==2.14.1+cpu --index-url https://download.pytorch.org/whl/cpu
ia-model\.venv\Scripts\python.exe -m pip install torch_geometric
```

Versiones instaladas (en `requirements.txt`): torch 2.14.1+cpu, torch_geometric 2.8.1, numpy 2.5.4, pytest 9.1.1, fastapi 0.115.12, starlette 0.46.2, uvicorn 0.34.0, pydantic 2.14.1, httpx 0.28.1.

Comandos (desde `ia-model/`):

| Objetivo | Comando |
|---|---|
| Todos los tests | `.venv\Scripts\python.exe -m pytest` |
| Un solo archivo | `.venv\Scripts\python.exe -m pytest tests/test_graph.py` |
| Experimento del criterio de la tesis | `.venv\Scripts\python.exe train_stgnn.py` |
| Experimento en varios horizontes | `.venv\Scripts\python.exe train_stgnn.py --horizons 1 3 6` |
| Entrenar y guardar el artefacto | `.venv\Scripts\python.exe train_stgnn.py --horizons 1 3 6 --save artifacts\stgnn.pt` |
| Entrenar con un CSV de campo | `.venv\Scripts\python.exe train_stgnn.py --csv datos\campo.csv --max-gap-hours 2 --save artifacts\stgnn-field.pt` |
| Servicio de inferencia | `.venv\Scripts\python.exe -m uvicorn serve:app --host 127.0.0.1 --port 8000` |

Estructura y decisiones:

- `lagunapaca_gnn/stations.py` — las 4 estaciones (`N1`…`N4`, los mismos identificadores del campo `node_id` del ESP32) y la matriz de distancias en km con la fórmula de **haversine**. `PROVISIONAL_STATIONS` está marcado con una advertencia: las coordenadas se derivaron del polígono del lago en OpenStreetMap (`way 26947746`, extremos norte/este/sur/oeste desplazados 5 % hacia el centroide y verificados con *point-in-polygon*), **no son puntos medidos en campo**. Deben reemplazarse por las coordenadas reales antes de reportar resultados.
- `lagunapaca_gnn/graph.py` — kernel gaussiano `exp(-d² / (2σ²))` y poda por **MST ∪ KNN**. El MST no es decorativo: es lo que **garantiza que el grafo nunca quede desconectado**; sin él, una estación sin vecinos cercanos quedaría aislada y no recibiría información del resto de la laguna. Se conservan los pesos gaussianos (no se binariza), porque la intensidad de la relación espacial también informa.
- `lagunapaca_gnn/windows.py` — construye el tensor de entrada `[nodos, tiempo, variables]` (la `T` de la tesis). La ventana objetivo empieza **justo después** de la de entrada y hay un test específico que lo comprueba: es la forma de evitar la fuga de información.
- `lagunapaca_gnn/metrics.py` — R², RMSE y MAE **sin depender de scikit-learn**, para que el criterio de éxito (`R² > 0.90`) quede explícito y sea auditable. Convenio heredado de scikit-learn para series constantes: `1.0` si la predicción es exacta, `0.0` si no (nunca `NaN` ni infinito).
- `lagunapaca_gnn/model.py` — el ST-GNN: **TCN causales** (dilatación exponencial 1, 2, 4…) sobre el tiempo, **GCN** sobre el grafo y **atención multi-cabeza enmascarada por la adyacencia**. Salida `(lote, estaciones, horizonte)`, una predicción por estación y paso.
  - La máscara de atención es lo que mantiene la estructura del grafo: sin ella la atención sería completamente densa y el GCN quedaría de adorno. Hay tests de que **una comunidad desconectada no se contamina** y de que **una estación aislada no produce `NaN`** (por eso la diagonal de la máscara siempre se permite: un `softmax` sobre una fila de `-infinitos` daría `NaN`).
  - Los tests del modelo verifican **propiedades**, no valores: formas, causalidad del TCN, que la topología del grafo cambie la salida, que los gradientes lleguen a todas las capas y que la atención sume 1.
  - **El modelo predice la *corrección* sobre el último valor observado**, no el valor absoluto: `predicción = cabeza(atendido) + x[:, :, -1, target_index]`. Con eso, un modelo sin entrenar ya equivale a la persistencia y el aprendizaje nunca puede empeorar la línea base. Fue la solución a un R² que, sin el anclaje, no batía a "copiar el último pH".
- `lagunapaca_gnn/dataset.py` — generador de la serie **sintética** `(estaciones, tiempo, variables)`. No reutiliza `backend/tools/simulate_node.py` porque ese genera **muestras independientes**: sirve para probar la ingesta y las alertas, pero no da nada que un modelo temporal pueda aprender. Aquí sí hay las tres propiedades que el ST-GNN necesita: **memoria temporal** (proceso AR(1)), **ciclo diario** (el pH con amplitud marcada: en un lago eutrófico sube de día por la fotosíntesis y baja de noche por la respiración) y **correlación espacial** (cada estación mezcla su proceso con el de sus vecinas vía la adyacencia gaussiana; sin esto el GCN sería decorativo). Los datos están marcados como simulación en el propio docstring: un R² alto aquí **valida la arquitectura**, no el comportamiento real de la laguna.
- `lagunapaca_gnn/field_data.py` — convierte un CSV de campo en formato largo (`timestamp,node_id,ph,turbidity_ntu,temp_c,tds_ppm`) en la serie horaria `[estaciones, horas, variables]`. Exige **zona horaria** (convierte a UTC), promedia las muestras de la misma hora, recorta al **rango común** de las cuatro estaciones y **no extrapola**. `max_gap_hours=0` por defecto: cualquier hueco detiene la carga; si se permite interpolar, se reporta cuántos instantes se rellenaron. No evalúa ECA-Agua: el veredicto sigue siendo del backend. `FieldSeries.fingerprint` alimenta la versión del artefacto para que dos campañas distintas no se pisen.
- `lagunapaca_gnn/normalization.py` — `StandardScaler` por variable. Sin normalizar, el TDS (≈320 ppm) domina el gradiente y el pH (≈7) se vuelve invisible: medido en este proyecto, el R² pasó de **−0.02 a 0.79** solo con estandarizar. Regla que no se rompe: la media y la desviación se calculan **solo con el conjunto de entrenamiento**.
- `lagunapaca_gnn/training.py` — `chronological_split`, `train_model`, `predict` y `evaluate_model`. La partición es **cronológica y con hueco** (igual al tamaño de la ventana de entrada): las ventanas se solapan, así que sin hueco la última de entrenamiento compartiría instantes con la primera de prueba. `evaluate_model` acepta el `scaler` para devolver las métricas a unidades reales (el R² es invariante a la escala, pero un RMSE en desviaciones típicas no le dice nada a nadie).
- `train_stgnn.py` — el experimento ejecutable: simula o carga un CSV de campo, construye el grafo, parte, estandariza, entrena, evalúa **y compara contra la línea base trivial** de repetir el último pH. Devuelve código de salida 1 si el criterio no se cumple, para poder encadenarlo al CI. Con `--save` guarda el artefacto y devuelve en el resultado el contexto (`model`, `scaler`, `edge_index`, `edge_weight`, `config`). Con `--csv` el reporte declara filas leídas/usadas, rango UTC, promedios por hora y huecos interpolados; el artefacto sale con `data_source=field` y una huella del contenido en la versión.
- `artifacts/stgnn.pt` (~100 KB) — artefacto versionado para que la demo y Compose funcionen sin entrenar. `artifacts/README.md` explica cómo regenerarlo y declara que es sintético.
  - **Resultado medido** (semilla 42, 2000 pasos, ventana 24 h, 150 épocas, 1478 ventanas de entrenamiento y 469 de prueba):

    | Horizonte | R² persistencia | R² ST-GNN | RMSE (pH) | MAE (pH) |
    |---|---|---|---|---|
    | 1 h | 0.9675 | 0.9817 | 0.0296 | 0.0235 |
    | 3 h | 0.8986 | 0.9681 | 0.0391 | 0.0308 |
    | 6 h | 0.7403 | **0.9289** | 0.0584 | 0.0445 |

    El criterio (`R² > 0.90`) se cumple con holgura en el escenario principal (6 h) y el modelo **bate a la persistencia en los tres horizontes**. Cuanto más largo el horizonte, más aporta el modelo: a 1 h la persistencia ya es casi perfecta, a 6 h se desmorona (0.74) y la ST-GNN se mantiene en 0.93.
- `lagunapaca_gnn/registry.py` — formato del artefacto (`FORMAT_VERSION = 1`): pesos, configuración, escalador, adyacencia, métricas y `data_source`. `new_version` produce `stgnn-synthetic-s{seed}-i{input_len}-e{epochs}` para sintético y `stgnn-field-s{seed}-i{input_len}-e{epochs}-d{huella}` para campo. `torch.load(..., weights_only=False)` es deliberado: el archivo contiene arrays de NumPy y lo produce el propio proyecto.
- `lagunapaca_gnn/api.py` — contrato FastAPI: `/health` (público, 200 aunque falte el modelo), `/model` y `/predict` (token `X-Internal-Token` si está configurado). Valida forma `[4][24][4]`, finitos, `base_time` con zona y horizonte entrenado. Sin artefacto, `/predict` responde 503; no se inventan valores.
- `serve.py` — `app` para uvicorn; lee `PORT`, `LAGUNA_MODEL_PATH` e `IA_INTERNAL_TOKEN`. El modelo se carga al arrancar, **no se entrena al arrancar**.
- `requirements.txt` incluye FastAPI 0.115.12, Starlette 0.46.2, Uvicorn 0.34.0, Pydantic 2.14.1 y httpx 0.28.1 (este último para el `TestClient`). **172 tests** en total (134 previos + 8 de `test_registry.py` + 11 de `test_api.py` + 19 de `test_field_data.py`).
- `pyproject.toml` fija `pythonpath = ["."]` para que pytest importe `lagunapaca_gnn` sin instalarlo.
- `lagunapaca_gnn/__init__.py` **no reexporta** el modelo a propósito: importar el paquete no debe arrastrar PyTorch. Se usa `from lagunapaca_gnn.model import SpatioTemporalGNN`.
- **El criterio de la tesis ya se demuestra de extremo a extremo con datos sintéticos**: `.venv\Scripts\python.exe train_stgnn.py` entrena y reporta `R² = 0.9289` a 6 h contra `0.7403` de la persistencia. Lo que falta no es código de entrenamiento: el cargador de CSV de campo ya existe y está probado. Faltan **datos de campo reales**: `dataset.py` simula una dinámica que elegimos nosotros, así que el R² demuestra que la arquitectura aprende una dinámica espacio-temporal, **no** que prediga la laguna real.

Hardware de desarrollo (**importante para planificar**): Ryzen 5 2500U, 10.9 GB de RAM, sin GPU dedicada (Radeon Vega 8 integrada). Sirve para desarrollar y validar el modelo con datos sintéticos o series cortas; el entrenamiento pesado con datos reales tendrá que ir a Colab o a una máquina con GPU.

**Pendiente de datos**: las coordenadas de `PROVISIONAL_STATIONS` son **propuestas** (derivadas de OpenStreetMap), no medidas. El grafo ya es geográficamente realista y el modelo se puede entrenar, pero la tesis exige reemplazarlas por los puntos de muestreo reales antes de reportar resultados. `graph.py` sigue recibiendo las distancias como parámetro justamente para que ese reemplazo no obligue a tocar la lógica del grafo. La carga de CSV (`field_data.py`, `train_stgnn.py --csv`) ya está lista para la primera campaña; no se incluye ningún CSV de campo real.

## Notas para agentes

- El proyecto y su documentación están en **español (es-PE)**.
- `README.md` es la puerta de entrada para humanos (arranque de la demo, estado de los
  módulos, resultados y pendientes). Si cambia el arranque, un total de pruebas o el
  estado de un módulo, actualiza el README junto con este archivo.
- `docs/` es material binario de referencia (tesis y libros), **no código**, salvo `docs/SDD/`, que es la documentación de diseño versionada (índice + 00–28 + 10 ADR). `docs/LIBROS/` contiene libros de consulta (Spring Boot, Neo4j, ESP32, GNN). Las herramientas de lectura no extraen PDF/DOCX directamente: DOCX/PPTX son ZIP y hay que descomprimirlos para leer su XML de texto.
- Los comandos de build/test de cada módulo están documentados arriba (backend y firmware). Si agregas scripts o tareas nuevas, documéntalas aquí.
- Git remoto: `origin` → `https://github.com/46855344-ux/tesis-laguna-paca.git`, rama única `main`.
