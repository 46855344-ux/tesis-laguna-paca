# 24 · Manual técnico

## 24.1 Estructura del repositorio

```
tesis-laguna-paca/
├── backend/            Java 21 + Spring Boot 3.2 (API + panel)
├── ia-model/           Python 3.12 + PyTorch/PyG (modelo + FastAPI)
├── iot-firmware/       ESP32 C++/PlatformIO
├── android-app/        Vacío (fuera de alcance)
├── deploy/             Configuración de Mosquitto
├── docs/SDD/           Esta documentación
├── compose.yaml        PostgreSQL + backend + IA/MQTT opcionales
├── .github/workflows/  CI
├── sonar-project.properties
└── .env.example
```

## 24.2 Toolchain

| Herramienta | Versión | Nota |
|---|---|---|
| JDK | 21 | El JDK 26 del sistema rompe Spring Boot 3.2; usar el JBR de IntelliJ |
| Maven | 3.9.9 | En la caché del wrapper |
| PostgreSQL | 16 | Producción; H2 en tests |
| Python | 3.12.10 | `.venv` propio de `ia-model` |
| PyTorch | 2.14.1+cpu | Índice propio de PyTorch |
| PyG | 2.8.1 | |
| PlatformIO | 6.2.0 | Sobre Python 3.12 |
| g++ | MinGW-w64 16.2.0 | Solo tests nativos |

## 24.3 Comandos

### Backend

```powershell
cd backend
$env:JAVA_HOME="D:\Intellij\IntelliJ IDEA 2025.2.1\jbr"
$mvn = "C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.9\977a63e90f436cd6ade95b4c0e10c20c\bin\mvn.cmd"

& $mvn test                                  # 138 tests + JaCoCo
& $mvn -Dtest=EcaWaterQualityRuleTest test   # un solo test
& $mvn -DskipTests package                   # JAR
& $mvn spring-boot:run -Dspring-boot.run.profiles=demo -Dspring-boot.run.useTestClasspath=true
```

### IA

```powershell
cd ia-model
.venv\Scripts\python.exe -m pytest
.venv\Scripts\python.exe train_stgnn.py --horizons 1 3 6 --save artifacts\stgnn.pt
.venv\Scripts\python.exe -m uvicorn serve:app --host 127.0.0.1 --port 8000
```

### Firmware

```powershell
$env:PATH="C:\Users\USER\AppData\Local\Programs\mingw64\bin;$env:PATH"
$pio = "C:\Users\USER\AppData\Local\Programs\Python\Python312\Scripts\pio.exe"
cd iot-firmware
& $pio test -e native
& $pio run -e esp32dev
& $pio run -e esp32dev-mqtt
& $pio run -e esp32dev -t upload
```

## 24.4 Configuración

### Backend (`application.yml`)

| Variable | Por defecto | Obligatoria en producción |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/laguna_paca` | Sí |
| `DB_USER` / `DB_PASSWORD` | `laguna` / `laguna` | Sí |
| `JWT_SECRET` | secreto de desarrollo | Sí (32+ bytes) |
| `APP_USER` / `APP_PASSWORD` | `admin` / `admin123` | Sí |
| `IA_BASE_URL` | `http://localhost:8000` | No |
| `IA_INTERNAL_TOKEN` | vacío | Recomendada |
| `IA_CONNECT_TIMEOUT_MS` | 2000 | No |
| `IA_READ_TIMEOUT_MS` | 15000 | No |
| `MQTT_*` | ver `13-mqtt.md` | No |
| `GOOGLE_MAPS_API_KEY` | vacío | No |

### IA

| Variable | Por defecto | Descripción |
|---|---|---|
| `LAGUNA_MODEL_PATH` | `artifacts/stgnn.pt` | Artefacto |
| `IA_INTERNAL_TOKEN` | vacío | Debe coincidir con el backend |
| `PORT` | 8000 | Puerto de uvicorn |

### Firmware

- `include/secrets.h` (copiar de `secrets.example.h`): WiFi, backend, usuario,
  contraseña, broker.
- `include/config.h`: pines, intervalo, calibración nominal.

## 24.5 Migraciones

- Ubicación: `backend/src/main/resources/db/migration/V{n}__{descripcion}.sql`.
- Contiguas, sin huecos; `FlywayMigrationsTest` lo verifica.
- Aditivas: no borrar columnas en una migración nueva sin plan de dos pasos.
- En tests, Flyway se desactiva y Hibernate crea el esquema; eso **no** valida el
  SQL de PostgreSQL.

Para añadir una migración:

1. Crear `V6__descripcion.sql`.
2. Actualizar la entidad si hace falta.
3. Añadir/ajustar `FlywayMigrationsTest` si cambia la política.
4. Probar en PostgreSQL antes de afirmar que funciona.

## 24.6 Tareas frecuentes

### Añadir una estación

1. Agregar el id a `StationCatalog.IDS`.
2. Agregar la coordenada en `ia-model/lagunapaca_gnn/stations.py`.
3. Reejecutar `backend/tools/fetch_laguna.py` para regenerar el GeoJSON.
4. Actualizar `PROVISIONAL_STATIONS` si hay coordenadas reales.
5. Reentrenar y guardar un artefacto nuevo.
6. Añadir el nodo al firmware (`NODE_ID`) y recompilar.

### Cambiar la regla ECA-Agua

1. Editar **solo** `EcaWaterQualityRule`.
2. Actualizar `EcaWaterQualityRuleTest` (incluidos los bordes).
3. Revisar los textos de los motivos.
4. **No** tocar el panel: no reevalúa.
5. Documentar el cambio en `03-requisitos-funcionales.md`.

### Regenerar el modelo

1. `train_stgnn.py --horizons 1 3 6 --save artifacts/stgnn.pt`.
2. Verificar las métricas impresas.
3. Actualizar `artifacts/README.md` y `10-contrato-ia.md`.
4. Reiniciar FastAPI (carga el artefacto al arrancar).
5. Si cambia el formato, subir `FORMAT_VERSION` y documentar la incompatibilidad.

### Añadir una variable al modelo

1. Añadir la columna/atributo y la migración.
2. Actualizar `TelemetryRequest`/`TelemetryResponse` y `openapi.yaml`.
3. Actualizar el orden de variables en `ia-model` (hoy
   `ph, turbidity_ntu, temp_c, tds_ppm`).
4. Reentrenar; el artefacto anterior deja de servir.
5. Actualizar el simulador y el firmware.

## 24.7 Diagnóstico

| Síntoma | Dónde mirar |
|---|---|
| 401 en la API | `Authorization: Bearer`, expiración (2 h), `JWT_SECRET` |
| 403 en el panel | Falta CSRF o el token de sesión |
| 409 en ingesta | `sample_id` duplicado por concurrencia |
| 503 en predicción | FastAPI caído, sin artefacto, token distinto |
| 502 en predicción | Respuesta de IA que no cumple el contrato |
| 409 en predicción | Menos de 24 muestras por estación |
| Panel sin datos | Base vacía o ventana de 24 h sin muestras |
| Migración falla | Flyway y el esquema real divergieron; no usar `ddl-auto=update` |

## 24.8 Convenciones

- Texto visible en español; identificadores en inglés.
- Paquetes por dominio (`telemetry`, `prediction`, `panel`), no por capa global.
- DTOs como `record`; entidades con setters solo donde el dominio lo exige.
- `Clock` inyectable para pruebas temporales.
- Commits en ASCII, en español, con alcance (`backend:`, `ia:`, `panel:`).

## 24.9 Dónde está cada cosa

| Necesito… | Archivo |
|---|---|
| Regla ECA-Agua | `telemetry/service/EcaWaterQualityRule.java` |
| Idempotencia | `telemetry/service/TelemetryService.java`, `V5` |
| Ventana de inferencia | `prediction/service/PredictionWindowBuilder.java` |
| Cliente IA | `prediction/client/AiClient.java` |
| Evaluación | `prediction/service/PredictionEvaluator.java` |
| Seguridad | `security/SecurityConfig.java` |
| Panel | `panel/`, `templates/panel/`, `static/css/`, `static/js/` |
| Modelo | `ia-model/lagunapaca_gnn/model.py` |
| Entrenamiento | `ia-model/train_stgnn.py` |
| API IA | `ia-model/lagunapaca_gnn/api.py`, `serve.py` |
| Núcleo del nodo | `iot-firmware/lib/lagunapaca_core/` |
