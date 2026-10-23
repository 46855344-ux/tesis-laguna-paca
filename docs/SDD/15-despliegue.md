# 15 · Despliegue

## 15.1 Componentes

| Servicio | Imagen / build | Puerto | Perfil |
|---|---|---|---|
| `db` | `postgres:16-alpine` | 5432 (opcional al host) | siempre |
| `backend` | `backend/Dockerfile` | 8080 | siempre |
| `ai` | `ia-model/Dockerfile` | 8000 (solo red interna) | `integrated` |
| `mqtt` | `eclipse-mosquitto:2` | 1883 | `mqtt` |

`docker compose up` levanta **db + backend**. La IA y el broker son opt-in: así
el monitoreo funciona aunque no haya modelo o broker.

## 15.2 Requisitos

- Docker Engine 24+ con Compose v2.20+ (perfiles).
- 4 GB de RAM libres para la imagen de IA (PyTorch CPU).
- Puertos 8080 (y 5432/1883 si se publican) libres.

## 15.3 Puesta en marcha

```bash
cp .env.example .env      # editar JWT_SECRET, APP_PASSWORD, DB_PASSWORD
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

- Panel React: `http://localhost:8080/panel`
- Swagger: `http://localhost:8080/swagger-ui.html`
- Salud: `http://localhost:8080/actuator/health`

Con IA:

```bash
docker compose --profile integrated up -d --build
```

Con IA y MQTT:

```bash
docker compose --profile integrated --profile mqtt up -d --build
```

El backend **no** declara `depends_on: ai`: si la IA no está activa, el backend
arranca igual y solo se degradan `/api/v1/predictions` y el botón del panel.

## 15.4 Variables

| Variable | Obligatoria | Ejemplo |
|---|---|---|
| `DB_NAME` | no | `laguna_paca` |
| `DB_USER` | no | `laguna` |
| `DB_PASSWORD` | sí en producción | `…` |
| `JWT_SECRET` | sí | 48 bytes aleatorios |
| `APP_USER` | no | `admin` |
| `APP_PASSWORD` | sí en producción | `…` |
| `IA_INTERNAL_TOKEN` | recomendada | token compartido |
| `MQTT_ENABLED` | no | `false` |
| `GOOGLE_MAPS_API_KEY` | no | clave restringida |
| `BACKEND_PORT` | no | `8080` |

`.env` está en `.gitignore`. `compose.yaml` usa `${JWT_SECRET:?...}` y
`${APP_PASSWORD:?...}` para que el stack no arranque con valores por defecto.

## 15.5 Imágenes

- **Backend**: build multi-etapa `maven:3.9.9-eclipse-temurin-21` →
  `eclipse-temurin:21-jre-jammy`. Copia `src`, que ya incluye el build de React
  en `static/panel`; la imagen **no** necesita Node. Usuario sin privilegios
  (uid 1001), `-XX:MaxRAMPercentage=75`, `curl` solo para el healthcheck.
  Si se modifica React, ejecutar `npm run build` en `frontend/` antes de
  `docker compose build`.
- **IA**: `python:3.12-slim`, instala primero la rueda de PyTorch CPU desde su
  índice y después `requirements.txt`, copia `lagunapaca_gnn/`, `serve.py` y
  `artifacts/`. Usuario sin privilegios, sin puerto publicado.
- `.dockerignore` excluye `target/`, `.venv/`, `__pycache__`, tests y logs.

El artefacto `artifacts/stgnn.pt` (~100 KB) se versiona para que la demo no
tenga que entrenar. Es sintético y así se declara.

## 15.6 Healthchecks

| Servicio | Comando | Efecto |
|---|---|---|
| `db` | `pg_isready` | El backend espera a que esté sano |
| `backend` | `curl -fsS /actuator/health` | Reinicio si deja de responder |
| `ai` | `urllib.request` a `/health` | Reinicio si el proceso muere |
| `mqtt` | `mosquitto_sub` a `$SYS/#` | Detecta broker caído |

`/actuator/health` es público a propósito: un orquestador no debe necesitar un
JWT para saber si el proceso vive. No revela datos de negocio.

## 15.7 Demo sin Docker ni PostgreSQL

```powershell
cd backend
$env:JAVA_HOME="D:\Intellij\IntelliJ IDEA 2025.2.1\jbr"
mvn spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.useTestClasspath=true"
```

- H2 en memoria, esquema creado por Hibernate, Flyway desactivado.
- `useTestClasspath=true` es necesario porque H2 está en ámbito `test`.
- **No es el entorno real**: no valida las migraciones ni el plan de consultas.
- El simulador del nodo está en `backend/tools/simulate_node.py`.

## 15.8 Producción: lo que falta

1. **TLS**: reverse proxy (Caddy/Nginx/Traefik) delante del backend; HTTPS
   también para MQTT (8883).
2. **No publicar** `5432` ni `1883` al host; hoy están publicados por comodidad
   de desarrollo.
3. Gestión de secretos (Docker secrets, Vault o el gestor del proveedor).
4. Backups automáticos de `pgdata` (ver `16-operacion-y-observabilidad.md`).
5. Restringir Swagger o moverlo a un perfil de desarrollo.
6. Límites de recursos (`mem_limit`, `cpus`) para la IA.
7. Firmar imágenes y escanear vulnerabilidades.

## 15.9 Validación realizada

- **Sintaxis YAML** de `compose.yaml`, `ci.yml` y `openapi.yaml` verificada con
  PyYAML.
- **Configuración de Compose** validada con Docker Compose v5.6.0
  (`config --quiet`, perfiles `integrated` y `mqtt`): salida 0, cuatro servicios
  y las imágenes esperadas.
- **Migraciones V1–V5** ejecutadas contra PostgreSQL 16.9 embebido: columnas,
  índice parcial, restricciones y SQLSTATE 23505/23514 comprobados (ver
  `28-registro-de-evidencias.md`). El job `backend-postgres` del CI ejecutó
  `PostgresMigrationIT` contra `postgres:16-alpine` en el run `39881860494`.
- **CI completa** ejecutada en GitHub Actions (run `39881860494`): backend,
  frontend, Android, PostgreSQL, IA y firmware en `success`; SonarQube omitido
  por falta de secretos.
- **No se ejecutó** `docker compose build` ni `up`: no hay motor Docker en la
  máquina. Por tanto no se afirma que las imágenes construyan; se entregan con la
  configuración revisada y esta limitación declarada.
