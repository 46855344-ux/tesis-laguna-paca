# 06 · Arquitectura del sistema

## 6.1 Vista de contexto

```mermaid
flowchart LR
    subgraph Campo
        N1[Nodo ESP32 N1]
        N2[Nodo ESP32 N2]
        N3[Nodo ESP32 N3]
        N4[Nodo ESP32 N4]
    end

    subgraph Servidor
        B[Spring Boot 3.2<br/>API + JSON del panel]
        FE[React SPA<br/>build en el JAR]
        DB[(PostgreSQL 16)]
        IA[FastAPI<br/>ST-GNN]
    end

    OP[Operador<br/>navegador]

    N1 & N2 & N3 & N4 -->|HTTP POST / JWT<br/>o MQTT QoS 1| B
    B --> DB
    B -->|X-Internal-Token<br/>/health /predict| IA
    OP -->|sesión + CSRF| FE
    FE -->|/panel/api/**| B
    M[Cliente API / app Android] -->|JWT| B
```

El backend es la **única fuente de verdad**: decide las alertas, persiste las
mediciones y es el único que escribe predicciones. El servicio de IA es un
componente interno sin acceso a la base.

## 6.2 Vista de contenedores

| Contenedor | Tecnología | Responsabilidad | Puerto |
|---|---|---|---|
| Backend | Java 21, Spring Boot 3.2 | API REST, API JSON del panel, ingesta, reglas, predicciones y servido del build React | 8080 |
| Frontend | React 19, TypeScript, Vite | SPA del panel; el build se copia al JAR y no corre como servicio aparte | — |
| Base de datos | PostgreSQL 16 | Persistencia y migraciones Flyway | 5432 |
| IA | Python 3.12, FastAPI | Inferencia del ST-GNN desde un artefacto | 8000 (interno) |
| Broker MQTT | Eclipse Mosquitto 2 | Transporte alternativo de telemetría | 1883 (perfil) |

## 6.3 Arquitectura interna del backend

```mermaid
flowchart TB
    subgraph API
        TC[TelemetryController]
        PC[PredictionController]
        AC[AiStatusController]
    end

    subgraph Panel
        PNL[PanelApiController + SpaController]
        PDS[PanelDashboardService]
        PHS[PanelHistorialService]
        PAS[PanelAlertasService]
        MLS[MapaLagunaService]
    end

    subgraph Dominio
        TS[TelemetryService]
        ECA[EcaWaterQualityRule]
        PS[PredictionService]
        PWB[PredictionWindowBuilder]
        PE[PredictionEvaluator]
    end

    subgraph Adaptadores
        AIC[AiClient]
        MQTT[TelemetryMqttHandler]
        REPO[(Repositorios JPA)]
    end

    TC --> TS
    MQTT --> TS
    TS --> ECA
    TS --> REPO
    TS -.efecto secundario.-> PE
    PC --> PS
    PS --> AIC
    PS --> PWB
    PWB --> REPO
    PS --> REPO
    PE --> REPO
    PNL --> PDS & PHS & PAS
    PDS --> REPO & MLS
    PDS --> REPO
```

### Paquetes

```
pe.edu.continental.lagunapaca
├── config/        ClockConfig
├── security/      SecurityConfig, JwtService, filtros
├── shared/api/    ApiError, GlobalExceptionHandler
├── station/       StationCatalog (N1..N4)
├── telemetry/
│   ├── api/       DTO y controlador REST
│   ├── domain/    WaterQualitySample, AlertReason, repositorio, specs
│   ├── service/   TelemetryService, EcaWaterQualityRule, IngestOutcome
│   └── mqtt/      handler y propiedades
├── prediction/
│   ├── api/       DTO y controladores
│   ├── client/    AiClient, AiProperties, payloads
│   ├── domain/    Prediction, PredictionRepository, estado
│   └── service/   PredictionService, WindowBuilder, Evaluator, errores
└── panel/         API JSON, SPA, servicios de vista y modelos
```

El frontend vive fuera del backend, en `frontend/`:

```
frontend/
├── src/api/        cliente CSRF/fetch y tipos del contrato
├── src/components/ componentes reutilizables
├── src/hooks/      sesión, tema y toasts
├── src/pages/      acceso, tablero, métricas, historial y alertas
├── src/styles/     tokens y CSS de la SPA
└── src/test/       Vitest + Testing Library
```

### Reglas de dependencia

- `panel` puede leer repositorios y `prediction.service`, pero **no** llama a
  los controladores de la API.
- El frontend consume **`/panel/api/**`**, no `/api/v1`; así no necesita un JWT
  en `localStorage` y conserva la sesión con CSRF.
- `telemetry` no depende de `prediction.client`: la ingesta nunca espera a la
  IA. Solo depende de `PredictionEvaluator`, que absorbe sus errores.
- `prediction` no importa `panel`.
- `shared` no importa dominio.

## 6.4 Flujo de una medición

```mermaid
sequenceDiagram
    participant N as ESP32
    participant C as TelemetryController
    participant S as TelemetryService
    participant R as EcaWaterQualityRule
    participant D as PostgreSQL
    participant E as PredictionEvaluator

    N->>C: POST /api/v1/telemetry (JWT)
    C->>S: ingest(request)
    S->>D: findBySampleId
    alt sample_id repetido
        D-->>S: muestra existente
        S-->>C: IngestOutcome(created=false)
        C-->>N: 200
    else muestra nueva
        S->>R: evaluate(ph)
        R-->>S: alerta + motivos
        S->>D: save(muestra)
        S->>E: evaluarSinRomperIngesta(...)
        E->>D: predicciones ±30 min
        S-->>C: IngestOutcome(created=true)
        C-->>N: 201
    end
```

## 6.5 Flujo de una predicción

```mermaid
sequenceDiagram
    participant U as Operador
    participant B as Backend
    participant I as FastAPI
    participant D as PostgreSQL

    U->>B: POST /panel/api/predicciones (CSRF) o /api/v1/predictions (JWT)
    B->>I: GET /health
    I-->>B: input_len, horizontes, versión
    B->>D: últimas 24 muestras por estación
    B->>I: POST /predict [4][24][4]
    I-->>B: 4 pH + target_at + versión + origen
    B->>D: 4 filas PENDING (mismo model_run_id)
    B-->>U: 201 + tarjetas
```

## 6.6 Decisiones estructurales

| Decisión | Alternativa descartada | Motivo |
|---|---|---|
| Monolito modular Spring Boot | Microservicios separados | Un solo JAR simplifica la demo y la tesis; los límites son de paquetes, no de red |
| PostgreSQL + Flyway | TimescaleDB | El volumen de la tesis no justifica una extensión; se documenta cuándo sí |
| Sin Neo4j | Grafo en memoria | El grafo tiene 4 nodos y se construye determinísticamente; Neo4j añadiría un servicio sin aportar |
| SPA React + API JSON | Thymeleaf renderizado en servidor | Mejor separación, estados de carga y refresco sin recargar; el build se sirve desde el JAR y la demo sigue sin Node |
| FastAPI separado | Cargar PyTorch en la JVM | Python es el entorno natural del modelo; el contrato HTTP aísla versiones |
| `java.net.http.HttpClient` | Feign/WebClient | Sin dependencias nuevas y con timeouts explícitos |
| Predicción en tabla propia | Columna en la muestra | Una predicción no es una medición y debe evaluarse después |
| `sample_id` opcional | Obligatorio | No rompe a los clientes existentes y permite idempotencia cuando el nodo lo envía |

## 6.7 Despliegue lógico

```mermaid
flowchart LR
    subgraph Docker Compose
        DB[(db: postgres:16)]
        BE[backend:8080]
        AI[ai:8000<br/>perfil integrated]
        MQ[mqtt:1883<br/>perfil mqtt]
    end
    HOST[Navegador / ESP32] -->|8080| BE
    BE --> DB
    BE -->|red interna| AI
    ESP32 -.->|1883| MQ
    MQ -.-> BE
```

La IA no publica puerto al host: solo el backend la alcanza por la red de
Compose. Ver `15-despliegue.md`.
