# AGENTS.md

Repositorio de tesis: **"Sistema de software e IoT con Redes Neuronales de Grafos (GNN) para el monitoreo de la contaminación en la Laguna de Paca, Junín 2026"** (autor: Ronald Arroyo Breña / RONY).

## Estado actual (importante)

- `backend/` tiene el esqueleto Spring Boot + ingesta de telemetría (HTTP y MQTT), alertas ECA-Agua y consulta histórica. `iot-firmware/` tiene el **núcleo portable** del nodo ESP32 (con tests). `ia-model/` y `android-app/` siguen **vacíos**.
- `docs/` está **sin trackear** y es material de referencia, no código.
- No hay CI. Las carpetas vacías no se versionan en git hasta que contengan archivos.

## Mapa de componentes (stack planificado)

La fuente de verdad es `docs/Plantilla Plan Tesis Continental Sistemas_RONY.docx`.

- `iot-firmware/` — firmware ESP32 (C++/Arduino): PH-4502C, TSW-20M, DS18B20 y Gravity TDS. Publica el payload JSON al **backend propio** (HTTP POST y/o MQTT) y entra en Deep Sleep. Estructura: `lib/lagunapaca_core/` (lógica portable y testeable) y `src/main.cpp` (glue de Arduino).
- `backend/` — microservicios Java 21 / Spring Boot 3.2; APIs REST + WebSockets, JWT, PostgreSQL 16 + Neo4j AuraDB.
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

## Notas para agentes

- El proyecto y su documentación están en **español (es-PE)**.
- `docs/` es material binario de referencia (tesis y libros), **no código**. `docs/LIBROS/` contiene libros de consulta (Spring Boot, Neo4j, ESP32, GNN). Las herramientas de lectura no extraen PDF/DOCX directamente: DOCX/PPTX son ZIP y hay que descomprimirlos para leer su XML de texto.
- Los comandos de build/test de cada módulo están documentados arriba (backend y firmware). Si agregas scripts o tareas nuevas, documéntalas aquí.
- Git remoto: `origin` → `https://github.com/46855344-ux/tesis-laguna-paca.git`, rama única `main`.
