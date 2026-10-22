# 28 · Registro de evidencias

Fecha de la validación: **20 de octubre de 2026**.
Máquina: Ryzen 5 2500U, 10.9 GB de RAM, Windows, sin GPU dedicada, sin Docker,
sin PostgreSQL instalado, sin SonarQube. Las migraciones se validaron con un
**PostgreSQL 16.9 embebido** (ver 28.3).

## 28.1 Línea base y resultado

| Suite | Antes | Después | Delta |
|---|---|---|---|
| Backend | 88 | **138** | +50 |
| Firmware | 32 | **38** | +6 |
| IA | 134 | **172** | +38 |
| **Total** | **254** | **348** | **+94** |

Además de la suite por defecto, `PostgresMigrationIT` (1 test) se ejecuta con
PostgreSQL real; con él, el total es **349**.

## 28.2 Comandos y resultados

### Backend

```powershell
$env:JAVA_HOME="D:\Intellij\IntelliJ IDEA 2025.2.1\jbr"
& "C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.9\977a63e90f436cd6ade95b4c0e10c20c\bin\mvn.cmd" test
```

Resultado: `BUILD SUCCESS`, **138 tests**, 0 fallos, 0 errores, 0 omitidos.

Cobertura JaCoCo (`target/site/jacoco/jacoco.csv`):

- instrucciones: **91.5 %** (5321/5813);
- ramas: **70.0 %** (254/363).

### Firmware

```powershell
$env:PATH="C:\Users\USER\AppData\Local\Programs\mingw64\bin;$env:PATH"
& "C:\Users\USER\AppData\Local\Programs\Python\Python312\Scripts\pio.exe" test -e native
```

Resultado: **38 tests**, todos `[PASSED]`.

```powershell
& "C:\Users\USER\AppData\Local\Programs\Python\Python312\Scripts\pio.exe" run -e esp32dev -e esp32dev-mqtt
```

| Entorno | Resultado | RAM | Flash |
|---|---|---|---|
| `esp32dev` | SUCCESS | 14.5 % (47 588 B) | 72.3 % (947 261 B de 1 310 720 B) |
| `esp32dev-mqtt` | SUCCESS | 14.0 % (45 824 B) | 59.8 % (784 321 B) |

Nota: PowerShell devuelve código 1 por el `#warning` de `secrets.h` en stderr,
aunque el build sea `SUCCESS`. Es esperado.

### IA

```powershell
cd ia-model
.venv\Scripts\python.exe -m pytest -q
```

Resultado: **172 passed**, 2 warnings, 207 s.

Artefacto:

```powershell
.venv\Scripts\python.exe train_stgnn.py --horizons 1 3 6 --save artifacts\stgnn.pt --quiet
```

| Horizonte | R² ST-GNN | R² persistencia | RMSE | MAE |
|---|---|---|---|---|
| 1 h | 0.9817 | 0.9675 | 0.0296 | 0.0235 |
| 3 h | 0.9681 | 0.8986 | 0.0391 | 0.0308 |
| 6 h | **0.9289** | **0.7403** | 0.0584 | 0.0445 |

- Criterio `R² > 0.90` a 6 h: **cumple**.
- Supera la persistencia en los tres horizontes.
- Artefacto: `artifacts/stgnn.pt`, **102 635 bytes**, versión
  `stgnn-synthetic-s42-i24-e150`, horizontes `[1, 3, 6]`.
- Ventanas: 1482/470 (1 h), 1480/470 (3 h), 1478/469 (6 h).

### Importación de datos de campo (canal, no evidencia de campo)

```powershell
.venv\Scripts\python.exe -m pytest tests\test_field_data.py -q
```

Resultado: **19 passed**. Cubre CSV válido, promedio horario, UTC obligatorio,
nodo/columna/valor inválido, duplicado, rango común, estación sin datos y huecos
interpolados solo con permiso explícito.

También se ejecutó el camino completo con un CSV **temporal generado a partir de
la serie sintética** (200 h, 800 filas), solo para comprobar que el canal
`--csv` produce un artefacto de campo:

```powershell
.venv\Scripts\python.exe train_stgnn.py --csv <temporal>\campo.csv --horizons 1 --input-len 12 --epochs 3 --hidden 8 --quiet --save <temporal>\campo.pt
```

Resultado: artefacto `stgnn-field-s42-i12-e3-d96f271ed`, `data_source = field`,
estaciones `N1`…`N4`, horizontes `[1]`. El R² de ese fixture (0.8903 a 1 h) **no
es un resultado de la laguna** y no se reporta como tal; el criterio no se
cumple con ese recorte. El artefacto quedó fuera del repositorio.

### Sintaxis de configuración

```powershell
.venv\Scripts\python.exe -c "import yaml; [yaml.safe_load(open(p, encoding='utf-8')) for p in ['../compose.yaml', '../.github/workflows/ci.yml', '../backend/openapi.yaml']]; print('YAML OK')"
```

Resultado: `YAML OK`.

## 28.3 PostgreSQL 16.9 y Compose

### Migraciones V1–V5 en PostgreSQL real

No hay PostgreSQL instalado ni Docker, así que se usó un **PostgreSQL 16.9
embebido** (`io.zonky.test:embedded-postgres` 2.1.0 + binarios
`embedded-postgres-binaries-windows-amd64` 16.9.0) con Flyway 10.10.0, en un
proyecto temporal **fuera del repositorio**. Se aplicaron las migraciones del
backend tal cual están versionadas:

```text
MIGRATIONS_EXECUTED=5
FLYWAY_HISTORY=[1:true, 2:true, 3:true, 4:true, 5:true]
```

Comprobaciones ejecutadas contra el esquema resultante:

| Comprobación | Resultado |
|---|---|
| Columnas de `water_quality_sample` y `prediction` | 12/12 presentes |
| Tipo de `recorded_at` | `timestamp with time zone` |
| Índice único de `sample_id` | `... (sample_id) WHERE (sample_id IS NOT NULL)` |
| Restricciones de `prediction` | 4/4 (`horizon`, `status`, `target`, `evaluation`) |
| Duplicado de `sample_id` | rechazado con SQLSTATE **23505** |
| Dos muestras con `sample_id NULL` | aceptadas (índice parcial) |
| Predicción `EVALUATED` incompleta | rechazada con SQLSTATE **23514** |
| Predicción `PENDING` válida | aceptada |

El test `PostgresMigrationIT` (`backend/src/test/.../migration/`) se ejecutó
contra este servidor embebido con `-Dtest=PostgresMigrationIT`: **1 test, 0
fallos**. No forma parte de `mvn test` (Surefire no incluye `*IT`); el job
`backend-postgres` del CI lo ejecuta contra `postgres:16-alpine`.

Esto valida el **SQL y las restricciones** en PostgreSQL 16. No valida el
arranque de Flyway desde Spring Boot, el contenedor `postgres:16-alpine` ni una
configuración de producción; eso sigue en la tabla 28.4.

### Docker Compose (configuración, sin motor)

Se descargó el binario standalone **Docker Compose v5.6.0** (no requiere el
demonio para `config`) y se validó el archivo con los dos perfiles:

```powershell
docker-compose -f compose.yaml --profile integrated --profile mqtt config --quiet
```

Resultado: código de salida **0**. Servicios resueltos: `db`, `backend`, `ai`,
`mqtt`. Imágenes: `postgres:16-alpine`, `laguna-paca-backend`, `laguna-paca-ai`,
`eclipse-mosquitto:2`. Puertos publicados al host: 8080, 5432 y 1883.

**No se ejecutó** `build` ni `up`: no hay motor Docker. Por tanto no se afirma
que las imágenes construyan ni que los healthchecks funcionen.

## 28.4 Lo que **no** se ejecutó

| Actividad | Motivo | Estado |
|---|---|---|
| `docker compose build/up` | No hay motor Docker | `config` validado; imágenes no construidas |
| PostgreSQL en contenedor / producción | No hay motor Docker | Migraciones validadas en PostgreSQL 16.9 embebido (28.3) |
| SonarQube | No hay servidor | Configurado; sin análisis |
| GitHub Actions | Sin runner conectado | Workflow escrito (incluye job PostgreSQL); IT ejecutado en local |
| Prueba en placa ESP32 | Sin hardware | Firmware compila y tests nativos pasan |
| Calibración de sensores | Sin laboratorio | Valores nominales |
| Datos de campo | Sin campaña de muestreo | Cargador `--csv` probado; sin mediciones reales |
| Prueba de carga | Sin entorno representativo | No medida |
| Auditoría de accesibilidad | Sin herramienta | Contrastes medidos a mano |
| Prueba de penetración | Sin alcance | No realizada |

## 28.5 Archivos principales creados o modificados

> Las rutas de esta sección corresponden a la entrega del 20/10/2026. El panel
> Thymeleaf fue reemplazado por la SPA React el 21/10/2026; la evidencia nueva
> está en 28.8.

### Backend

- `station/StationCatalog.java`
- `config/ClockConfig.java`
- `db/migration/V5__predictions_and_idempotency.sql`
- `prediction/…` (domain, api, client, service)
- `telemetry/service/TelemetryService.java` (idempotencia y evaluación)
- `telemetry/api/TelemetryRequest.java`, `TelemetryController.java`
- `telemetry/mqtt/TelemetryMqttHandler.java`
- `shared/api/GlobalExceptionHandler.java`
- `panel/…` (predicciones en el tablero, controlador)
- `templates/panel/panel.html`, `static/css/panel.css`
- `openapi.yaml`, `pom.xml` (JaCoCo)
- Tests: `PredictionIntegrationTest`, `PanelPrediccionesIntegrationTest`,
  `AiClientTest`, `PredictionWindowBuilderTest`, `PredictionEvaluatorTest`,
  `PredictionServiceTest`, `PredictionControllerTest`, `FlywayMigrationsTest`,
  y ampliaciones de los slices existentes.

### IA

- `lagunapaca_gnn/registry.py`
- `lagunapaca_gnn/field_data.py`
- `lagunapaca_gnn/api.py`
- `serve.py`
- `train_stgnn.py` (`--save`, `--csv`, contexto)
- `artifacts/stgnn.pt`, `artifacts/README.md`
- `tests/test_registry.py`, `tests/test_api.py`, `tests/test_field_data.py`
- `requirements.txt`, `Dockerfile`, `.dockerignore`

### Firmware

- `lib/lagunapaca_core/include/lagunapaca/SampleIdentity.h`
- `lib/lagunapaca_core/src/SampleIdentity.cpp`
- `TelemetryPayload.h/.cpp`, `TelemetryNode.h/.cpp`
- `src/main.cpp`
- `test/test_core/test_core.cpp` (+6 tests)

### Infraestructura y documentación

- `compose.yaml`, `deploy/mosquitto.conf`
- `backend/Dockerfile`, `backend/.dockerignore`
- `.env.example`, `.gitignore`
- `.github/workflows/ci.yml`
- `sonar-project.properties`
- `docs/SDD/00…28` y `docs/SDD/ADR-001…010`
- `README.md`, `AGENTS.md`

## 28.6 Cómo reproducir esta evidencia

1. Seguir `17-estrategia-de-pruebas.md` para las tres suites.
2. Para PostgreSQL: definir `POSTGRES_URL`, `POSTGRES_USER` y
   `POSTGRES_PASSWORD` y ejecutar `mvn -Dtest=PostgresMigrationIT test` desde
   `backend/`.
3. Para el canal de campo, usar un CSV con el contrato de `10-contrato-ia.md` y
   ejecutar `train_stgnn.py --csv`; no etiquetar como campo un archivo simulado.
4. Seguir `22-guia-de-demostracion.md` para el flujo funcional.
5. Comparar los totales con la tabla 28.1.
6. Si algo difiere, revisar la versión del JDK (21), de Python (3.12) y de las
   dependencias de `requirements.txt`.

## 28.7 Declaración

Los resultados de este registro se obtuvieron ejecutando los comandos
indicados. No se reportan como ejecutados los que aparecen en 28.3. Las métricas
del modelo corresponden a datos sintéticos y así deben citarse. El CSV usado
para validar `--csv` se generó a partir de la simulación: **no es una medición
de campo** y no se presenta como evidencia de la laguna.

## 28.8 Migración del panel a React (21/10/2026)

Se reemplazó el panel Thymeleaf por una **SPA React 19 + TypeScript + Vite**. El
build se escribe en `backend/src/main/resources/static/panel` y Spring Boot lo
sirve desde el JAR; la demo sigue sin Node. La API del panel quedó en
`/panel/api/**` y conserva `/panel/datos` como alias.

### Comandos ejecutados

```powershell
cd frontend
npm test
npm run build

cd ..\backend
$env:JAVA_HOME="D:\Intellij\IntelliJ IDEA 2025.2.1\jbr"
& "C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.9\977a63e90f436cd6ade95b4c0e10c20c\bin\mvn.cmd" test
```

### Resultados

| Suite | Tests | Resultado |
|---|---|---|
| Backend | **145** | `BUILD SUCCESS`, 0 fallos, 0 errores, 0 omitidos |
| Frontend | **17** | 4 archivos, 17 tests verdes (Vitest + Testing Library) |
| **Total del proyecto** | **372** | 145 backend + 17 frontend + 38 firmware + 172 IA |

Cobertura JaCoCo tras la migración (89 clases):

- instrucciones: **91.7 %** (5491/5989);
- ramas: **68.8 %** (265/385).

### Demostración verificada

Con el perfil `demo` y H2 en memoria se comprobó por HTTP:

- `GET /login` → 200 con el shell React y assets de Vite;
- `GET /panel` sin sesión → 302 a `/login`;
- `GET /panel/api/datos` sin sesión → 401 JSON;
- `GET /panel/assets/...` → 200;
- `GET /panel/csrf` → token; login `admin/admin123` → `/panel`;
- `/panel/metricas` → 200 con el shell React y sesión;
- `/panel/api/sesion`, `/panel/api/datos`, `/panel/api/historial` y
  `/panel/api/alertas` → 200 con sesión;
- `POST /logout` con CSRF → `/login?logout` y `/panel/api/sesion` → 401.

Se corrigió un fallo detectado al probar el cierre de sesión: `/logout` no estaba
en la cadena del panel y la cadena stateless respondía 401. Ahora hay tests que
fijan que el logout invalida la sesión y que exige CSRF.

### Lo que no se ejecutó

- Pruebas E2E con un navegador real (solo jsdom).
- `docker compose build/up`, CI en un runner y SonarQube.
- Carga real de Google Maps (no hay clave configurada en esta máquina).

El build de React sí se generó y el backend lo sirvió desde el JAR. Los datos
mostrados en la demo siguen siendo del simulador o de la base vacía; no se
presentan como mediciones de campo.
