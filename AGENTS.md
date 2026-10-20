# AGENTS.md

Repositorio de tesis: **"Sistema de software e IoT con Redes Neuronales de Grafos (GNN) para el monitoreo de la contaminación en la Laguna de Paca, Junín 2026"** (autor: Ronald Arroyo Breña / RONY).

## Estado actual (importante)

- `backend/` tiene el esqueleto Spring Boot + ingesta de telemetría (HTTP y MQTT), alertas ECA-Agua y consulta histórica, con Swagger UI, un **panel web Thymeleaf** con sistema de diseño propio y un simulador del nodo. `iot-firmware/` tiene el **núcleo portable** del nodo ESP32 (con tests). `ia-model/` está **completo de extremo a extremo**: estaciones, grafo, ventanas temporales, métricas, modelo ST-GNN (TCN + GCN + Attention), generador de serie sintética, normalización, entrenamiento y el experimento `train_stgnn.py` que **demuestra el criterio de la tesis** (`R² = 0.9289` a 6 h, contra `0.7403` de la línea base de persistencia; 134 tests con pytest). `android-app/` sigue **vacío**.
- `docs/` está **sin trackear** y es material de referencia, no código.
- No hay CI. Las carpetas vacías no se versionan en git hasta que contengan archivos.

## Mapa de componentes (stack planificado)

La fuente de verdad es `docs/Plantilla Plan Tesis Continental Sistemas_RONY.docx`.

- `iot-firmware/` — firmware ESP32 (C++/Arduino): PH-4502C, TSW-20M, DS18B20 y Gravity TDS. Publica el payload JSON al **backend propio** (HTTP POST y/o MQTT) y entra en Deep Sleep. Estructura: `lib/lagunapaca_core/` (lógica portable y testeable) y `src/main.cpp` (glue de Arduino).
- `backend/` — microservicios Java 21 / Spring Boot 3.2; APIs REST + WebSockets, JWT, PostgreSQL 16 + Neo4j AuraDB. Sirve también el **panel web** (Thymeleaf) desde el mismo JAR.
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
| Arrancar en modo demo (H2 en memoria) | `mvn spring-boot:run -Dspring-boot.run.profiles=demo -Dspring-boot.run.useTestClasspath=true` |
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


## Panel web (`backend/`) — sistema de diseño "Laguna Paca"

El backend sirve además un **panel de monitoreo renderizado en el servidor con Thymeleaf**.
Se descartó JSP: habría exigido empaquetar en WAR y perder el arranque con `spring-boot:run`
para obtener menos reutilización. El panel corre en el **mismo JAR** que la API, así que la
demostración sigue siendo un solo comando.

| Objetivo | Comando / URL |
|---|---|
| Abrir el panel (perfil `demo`) | `http://localhost:8080/panel` |
| Formulario de acceso | `http://localhost:8080/login` → `admin` / `admin123` |
| Tokens de diseño | `backend/src/main/resources/static/css/tokens.css` |
| Componentes | `backend/src/main/resources/static/css/panel.css` |
| Interactividad | `backend/src/main/resources/static/js/panel.js` |
| Plantillas | `backend/src/main/resources/templates/` |

### Dos superficies, dos mecanismos de autenticación (decisión)

`SecurityConfig` tiene **dos cadenas de filtros**:

1. `@Order(1)` — panel (`/panel/**`, `/login`, `/css/**`, `/js/**`): **sesión con formulario**
   (`JSESSIONID`) y **CSRF activo**. Es la única forma razonable de proteger formularios en un
   navegador: con el JWT en `localStorage` el token quedaría expuesto a XSS, y sin CSRF el
   formulario de acceso sería falsificable.
2. `@Order(2)` — API (`/api/**`): **stateless con JWT**, sin cookies y sin CSRF, porque su
   cliente es el ESP32 y no un navegador.

Consecuencia deliberada: **un JWT válido no abre el panel**. Hay un test que lo fija
(`PanelIntegrationTest.elPanelNoAceptaJwt`). El orden importa: si la cadena de la API fuera
primero, su `anyRequest().authenticated()` respondería el panel con un 401 en JSON.

### Tokens (fuente única de verdad)

`tokens.css` define la paleta y la escala; `panel.css` **no escribe ningún color a mano**, así el
modo oscuro funciona sin duplicar reglas. Los mismos valores se replicarán en el tema Material 3
de `android-app/`.

| Token | Claro | Oscuro |
|---|---|---|
| `--color-primary` (fondo sólido) | `#0F5C8C` | `#1C6FA8` |
| `--color-primary-text` (texto sobre tenue) | `#0F5C8C` | `#6CB8E8` |
| `--color-secondary` | `#178C7F` | `#2FA79A` |
| `--color-success` | `#157347` | `#4FB383` |
| `--color-warning` | `#8A5A0B` | `#D6A141` |
| `--color-danger` | `#C0392B` | `#E0705F` |
| `--color-info` | `#2B6CB0` | `#6CA9DE` |

**Por qué existe `--color-primary-text`:** el primario profundo sobre fondo oscuro daba
**2.79:1** en el enlace activo del menú (AA exige 4.5:1). Con el token aclarado sube a **6.93:1**.
El primario sólido se reserva para fondos de botón, donde el texto blanco queda en **5.40:1**.
El mismo problema tenía el botón primario en oscuro (3.47:1 por usar texto oscuro) y se corrigió
dejando `--color-text-inverse: #FFFFFF` en ambos temas.

Contrastes medidos en el navegador: enlace activo 6.27 / 6.93, botón primario 7.17 / 5.40, texto
de cuerpo 17.06 / 15.19, texto secundario 4.76 / 6.66, etiquetas semánticas entre 4.67 y 6.23
(primero claro, luego oscuro). Todos cumplen AA.

### Tablero con datos reales

`/panel` deja de ser un estado vacío permanente y muestra telemetría de la base de datos:

| Elemento | De dónde sale |
|---|---|
| Indicadores (muestras, alertas, estaciones, hora) | ventana de **24 h** |
| Tarjetas N1–N4 | **última muestra de cada estación**, sin límite de antigüedad |
| Badge de estado | campo `alert` **persistido** por la ingesta, no se reevalúa |
| Gráfico | puntos de las últimas 24 h, una línea por estación |

Decisiones que conviene no deshacer:

- **El panel lee del repositorio, no de la API REST.** El navegador usa sesión; meter un JWT en
  `localStorage` para consultar `/api/v1/telemetry` sería un riesgo XSS y duplicaría el cliente.
- **El estado no se recalcula en el panel.** `PanelDashboardService` solo lee `sample.isAlert()` y
  los motivos ya redactados por `EcaWaterQualityRule`. Si el panel reevaluara el pH habría dos
  reglas ECA-Agua que podrían divergir.
- **Solo tres estados**: `SIN_DATOS` (gris), `DENTRO` (verde), `ALERTA` (rojo). No existe un
  "cerca del límite": sería una cuarta regla que el backend no conoce.
- **La tarjeta no aplica la ventana de 24 h.** Si N2 lleva tres días callado se muestra su último
  dato con "hace 3 días", que es más honesto que fingir que nunca midió o que el dato es de ahora.
- **La antigüedad se escribe en palabras** ("hace 3 minutos") porque un `HH:mm` suelto invita a
  creer que el dato es actual.
- **Formato numérico en Java con `Locale.ROOT`** (`"%.2f"`), no con `#numbers` de Thymeleaf: el
  separador decimal no puede depender del locale de la máquina. El JSON reutiliza esos textos y
  el gráfico recibe números; así no hay dos redondeos distintos.
- **Gráfico en SVG hecho a mano** (`panel.js`, ~200 líneas) en vez de Chart.js/CDN: la demo debe
  funcionar **sin internet**. Cuatro series con `--chart-1..4` (azul, teal, ámbar, violeta),
  contraste ≥3:1 sobre la superficie en ambos temas.
- **Si la ventana de datos dura menos de 30 min, el eje se abre hasta 30 min.** Una ráfaga del
  simulador aplastaría el eje y todas las horas se leerían iguales. Es legibilidad, no datos:
  las muestras siguen siendo solo las de 24 h.
- **Refresco cada 20 s** contra `GET /panel/datos` (protegido por la misma cadena de sesión), en
  pausa si la pestaña está oculta. El botón "Actualizar" hace lo mismo y confirma con un toast.
- **Sin JavaScript** las tarjetas, los indicadores y las reglas siguen completos; solo el gráfico
  muestra su estado vacío. El `<noscript>` lo dice explícitamente.

### Historial con filtros y paginación

`GET /panel/historial` lista las muestras de la más reciente a la más antigua, 20 por página.

| Filtro | Comportamiento |
|---|---|
| Estación | `N1`…`N4` o todas |
| Desde / Hasta | días de calendario **en hora de Lima** (00:00 y 23:59:59.999) |
| Solo alertas | usa el campo `alert` persistido |
| Buscar en el motivo | texto dentro del mensaje que redactó el backend |

Decisiones que conviene no deshacer:

- **No hay una segunda consulta.** El panel llama a `TelemetryService.search`, que ya aplica
  `WaterQualitySampleSpecifications`. Duplicar los `LIKE`/rangos en el panel crearía dos formas de
  filtrar que podrían divergir.
- **Los filtros llegan como texto, no como `LocalDate`.** Con un `@RequestParam LocalDate`, una
  fecha mal tecleada responde 400 y pierde el formulario. Aquí se parsea a mano: se avisa
  («La fecha «desde» no es válida…») y el resto del historial se sigue mostrando.
- **Un rango invertido no se aplica y se avisa** en vez de romper o devolver una tabla vacía que
  parecería "no hay datos".
- **Las fechas son de Lima, no UTC.** `recorded_at` es un instante; interpretar el día en UTC
  desplazaría el corte cinco horas y una muestra de las 23:30 aparecería en el día siguiente. Hay
  un test que fija justo ese caso (`2026-10-18T04:30Z` = 17/10 23:30 en Lima).
- **Una página fuera de rango se ajusta a la última** (se vuelve a consultar). Si no, cambiar de
  filtro estando en la página 5 mostraría una tabla vacía con "sin resultados", que es mentira.
- **Formulario GET, sin JavaScript.** La búsqueda queda en la URL: se puede guardar, compartir y
  el botón "atrás" del navegador funciona. Los enlaces de paginación arrastran todos los filtros.
- La tabla se desplaza en horizontal dentro de su tarjeta (`min-width: 760px`) en vez de aplastar
  siete columnas en un móvil.

### Reglas de interfaz que el código hace cumplir

- **Todo el texto visible en español**; los identificadores técnicos (CSS, JS, Java) en inglés.
- **Modo claro por defecto** y oscuro opcional, persistido en `localStorage` (`laguna-tema`).
  El tema se aplica con un script en el `<head>` **antes del CSS** para evitar el destello blanco.
- **Iconos siempre acompañados de texto** (Lucide incrustado como SVG con `aria-hidden="true"`);
  nunca sustituyen a una etiqueta.
- **Áreas táctiles de 44 px**, anillo de foco visible (`:focus-visible`), enlace "Saltar al
  contenido", `aria-current="page"`, `aria-expanded`, `aria-pressed` y `aria-live` en los toasts.
- **Responsive**: barra lateral fija de 264 px en ≥1024 px, colapsable a 76 px; en móvil es un
  cajón con velo que se cierra con `Escape` devolviendo el foco al botón que lo abrió.
- **Estados vacíos honestos**: lo que todavía no existe se muestra como "Pronto" deshabilitado,
  en vez de un enlace que lleva a un 404.
- `prefers-reduced-motion` desactiva las animaciones.
- El panel **no reutiliza los controladores de la API**: el contrato de `openapi.yaml` no debe
  arrastrar responsabilidades de presentación.

### Tests

`PanelIntegrationTest` (contexto completo, H2) fija el contrato: la raíz `/` redirige al panel
(en vez del 401 en JSON de la API), `/panel` sin sesión redirige a `/login`, el formulario es
público y está en español, los estáticos son públicos, el login válido entra, el inválido vuelve
con `?error`, el POST exige CSRF, un JWT no abre el panel y la API conserva su 401 sin token.

`PanelDashboardIntegrationTest` (contexto completo, H2, base propia) fija el tablero: las cuatro
estaciones existen sin datos, la tarjeta muestra la muestra **más reciente** y no la primera, una
alerta se pinta con el motivo que redactó el backend, el JSON trae las cuatro estaciones con su
estado y la serie, el JSON **exige sesión**, y ni la serie ni los contadores incluyen muestras de
más de 24 h. Las muestras se insertan por el repositorio para fijar `recordedAt` sin depender del
reloj.

`PanelHistorialIntegrationTest` (contexto completo, H2, base propia) fija el historial: exige
sesión, ordena de la más reciente a la más antigua, pagina de 20 en 20, ajusta una página fuera de
rango a la última, filtra por estación, por día **de Lima**, por "solo alertas" y por el texto del
motivo, avisa (sin romper) cuando el rango está invertido o la fecha no es válida, conserva los
filtros en los enlaces de paginación y muestra un estado vacío con salida.

**Total del backend: 64 tests.**


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
- `HTTPClient` es HTTP plano: en producción requiere TLS (`WiFiClientSecure` + huella/bundle de CA) y el backend en HTTPS.
- Flash al 72 % (946 KB de 1310 KB) con `WiFi` + `HTTPClient` + `ArduinoJson`: si se agrega OTA, cambiar el esquema de particiones (`board_build.partitions`).
- El `#warning` de `secrets.h` hace que PowerShell devuelva salida con código 1 aunque el build sea `SUCCESS` (gcc escribe el warning en stderr).
- **Canal de telemetría**: HTTP POST es el default; MQTT se activa con `-DLAGUNA_TELEMETRY_MQTT` (entorno `esp32dev-mqtt`) o descomentando el `#define` en `include/config.h` (para Arduino IDE). Ambos implementan `laguna::TelemetryTransport`, así que el núcleo no cambia.
- La vía MQTT usa `256dpi/MQTT` (arduino-mqtt) con **QoS 1**, coherente con `MQTT_QOS=1` del backend: el nodo solo da la muestra por entregada cuando llega el PUBACK. PubSubClient **no** sirve para esto porque solo publica con QoS 0.
- Tópico MQTT: `laguna-paca/telemetry`, debe coincidir con `app.mqtt.topic`. El canal MQTT **no usa JWT**: se autentica con las credenciales del broker (nuevas variables `MQTT_BROKER_HOST`, `MQTT_USERNAME`, `MQTT_PASSWORD` en `secrets.h`).
- El client id MQTT se construye con `buildMqttClientId()` (núcleo, testeado): `lp-<8 hex de la MAC>-<nodeId>`, recortado a **23 bytes** porque MQTT 3.1.1 solo obliga al broker a aceptar ese largo. La MAC va antes del node id para que el recorte nunca sacrifique la unicidad.
- Presupuesto de flash: **HTTP 72.4 %** (948 KB) vs **MQTT 60.1 %** (787 KB). La vía HTTP arrastra `HTTPClient` + `WiFiClientSecure` + bundle de CA. `build_src_filter` excluye de cada entorno el transporte que no usa.

## Modelo de IA (`ia-model/`) — comandos

Toolchain: **Python 3.12.10** con **entorno virtual propio** en `ia-model/.venv` (no se instala nada en el Python global). Se creó con:

```powershell
& "C:\Users\USER\AppData\Local\Programs\Python\Python312\python.exe" -m venv ia-model\.venv
ia-model\.venv\Scripts\python.exe -m pip install numpy pytest
# PyTorch CPU no está en PyPI con el sufijo "+cpu": se pide a su propio índice.
ia-model\.venv\Scripts\python.exe -m pip install torch==2.14.1+cpu --index-url https://download.pytorch.org/whl/cpu
ia-model\.venv\Scripts\python.exe -m pip install torch_geometric
```

Versiones instaladas (en `requirements.txt`): torch 2.14.1+cpu, torch_geometric 2.8.1, numpy 2.5.4, pytest 9.1.1.

Comandos (desde `ia-model/`):

| Objetivo | Comando |
|---|---|
| Todos los tests | `.venv\Scripts\python.exe -m pytest` |
| Un solo archivo | `.venv\Scripts\python.exe -m pytest tests/test_graph.py` |
| Experimento del criterio de la tesis | `.venv\Scripts\python.exe train_stgnn.py` |
| Experimento en varios horizontes | `.venv\Scripts\python.exe train_stgnn.py --horizons 1 3 6` |

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
- `lagunapaca_gnn/normalization.py` — `StandardScaler` por variable. Sin normalizar, el TDS (≈320 ppm) domina el gradiente y el pH (≈7) se vuelve invisible: medido en este proyecto, el R² pasó de **−0.02 a 0.79** solo con estandarizar. Regla que no se rompe: la media y la desviación se calculan **solo con el conjunto de entrenamiento**.
- `lagunapaca_gnn/training.py` — `chronological_split`, `train_model`, `predict` y `evaluate_model`. La partición es **cronológica y con hueco** (igual al tamaño de la ventana de entrada): las ventanas se solapan, así que sin hueco la última de entrenamiento compartiría instantes con la primera de prueba. `evaluate_model` acepta el `scaler` para devolver las métricas a unidades reales (el R² es invariante a la escala, pero un RMSE en desviaciones típicas no le dice nada a nadie).
- `train_stgnn.py` — el experimento ejecutable: simula, construye el grafo, parte, estandariza, entrena, evalúa **y compara contra la línea base trivial** de repetir el último pH. Devuelve código de salida 1 si el criterio no se cumple, para poder encadenarlo a un futuro CI.
  - **Resultado medido** (semilla 42, 2000 pasos, ventana 24 h, 150 épocas, 1478 ventanas de entrenamiento y 469 de prueba):

    | Horizonte | R² persistencia | R² ST-GNN | RMSE (pH) | MAE (pH) |
    |---|---|---|---|---|
    | 1 h | 0.9676 | 0.9819 | — | — |
    | 3 h | 0.9009 | 0.9628 | — | — |
    | 6 h | 0.7403 | **0.9289** | 0.0584 | 0.0445 |

    El criterio (`R² > 0.90`) se cumple con holgura en el escenario principal (6 h) y el modelo **bate a la persistencia en los tres horizontes**. Cuanto más largo el horizonte, más aporta el modelo: a 1 h la persistencia ya es casi perfecta, a 6 h se desmorona (0.74) y la ST-GNN se mantiene en 0.93.
- `pyproject.toml` fija `pythonpath = ["."]` para que pytest importe `lagunapaca_gnn` sin instalarlo.
- `lagunapaca_gnn/__init__.py` **no reexporta** el modelo a propósito: importar el paquete no debe arrastrar PyTorch. Se usa `from lagunapaca_gnn.model import SpatioTemporalGNN`.
- **El criterio de la tesis ya se demuestra de extremo a extremo con datos sintéticos**: `.venv\Scripts\python.exe train_stgnn.py` entrena y reporta `R² = 0.9289` a 6 h contra `0.7403` de la persistencia. Lo que falta no es código de entrenamiento, son **datos de campo**: `dataset.py` simula una dinámica que elegimos nosotros, así que el R² demuestra que la arquitectura aprende una dinámica espacio-temporal, **no** que prediga la laguna real.

Hardware de desarrollo (**importante para planificar**): Ryzen 5 2500U, 10.9 GB de RAM, sin GPU dedicada (Radeon Vega 8 integrada). Sirve para desarrollar y validar el modelo con datos sintéticos o series cortas; el entrenamiento pesado con datos reales tendrá que ir a Colab o a una máquina con GPU.

**Pendiente de datos**: las coordenadas de `PROVISIONAL_STATIONS` son **propuestas** (derivadas de OpenStreetMap), no medidas. El grafo ya es geográficamente realista y el modelo se puede entrenar, pero la tesis exige reemplazarlas por los puntos de muestreo reales antes de reportar resultados. `graph.py` sigue recibiendo las distancias como parámetro justamente para que ese reemplazo no obligue a tocar la lógica del grafo.

## Notas para agentes

- El proyecto y su documentación están en **español (es-PE)**.
- `docs/` es material binario de referencia (tesis y libros), **no código**. `docs/LIBROS/` contiene libros de consulta (Spring Boot, Neo4j, ESP32, GNN). Las herramientas de lectura no extraen PDF/DOCX directamente: DOCX/PPTX son ZIP y hay que descomprimirlos para leer su XML de texto.
- Los comandos de build/test de cada módulo están documentados arriba (backend y firmware). Si agregas scripts o tareas nuevas, documéntalas aquí.
- Git remoto: `origin` → `https://github.com/46855344-ux/tesis-laguna-paca.git`, rama única `main`.
