# 16 · Operación y observabilidad

## 16.1 Salud

| Endpoint | Qué informa | Autenticación |
|---|---|---|
| `GET /actuator/health` | Proceso, base de datos | Pública |
| `GET /actuator/info` | Metadatos de la app | Pública |
| `GET /api/v1/ai/health` | Contacto con FastAPI y modelo cargado | JWT |
| `GET /health` (FastAPI) | Proceso y `model_loaded` | Pública en la red interna |
| `GET /panel/datos` | Estado agregado para el navegador | Sesión |

`/api/v1/ai/health` responde **200 aunque la IA esté caída**: el cuerpo lleva
`available=false` y `detail`. Así se distingue «el backend no responde» de «el
backend responde y la IA no está disponible».

## 16.2 Registros

- Spring Boot escribe en consola (lo recoge `docker compose logs`).
- La ingesta registra la muestra y el veredicto; la evaluación de predicciones
  fallida se absorbe con `WARN` y **no** tumba la muestra.
- El handler MQTT descarta un mensaje inválido con `WARN` sin detener el
  consumidor.
- El nodo escribe por serie (115200 baudios): muestra, resultado y motivo.
- **No hay correlación de trazas** (no hay `traceId` ni OpenTelemetry). Es deuda
  declarada.

## 16.3 Runbook

### La IA no responde

1. `GET /api/v1/ai/health` → `available=false`, `detail`.
2. `docker compose ps ai` y `docker compose logs ai`.
3. Verificar que el artefacto existe (`/app/artifacts/stgnn.pt`) y que
   `IA_INTERNAL_TOKEN` coincide en ambos servicios.
4. Mientras tanto, la ingesta y las alertas **siguen funcionando**; el panel
   muestra el aviso al intentar predecir.

### La base de datos no responde

1. `/actuator/health` reporta `DOWN`.
2. `docker compose logs db`; verificar espacio en disco y el volumen `pgdata`.
3. El backend no puede operar sin base: es la única dependencia dura.

### El broker MQTT no responde

1. Si `MQTT_ENABLED=false`, no afecta.
2. Si está activo, la ingesta HTTP sigue funcionando.
3. Revisar `deploy/mosquitto.conf`, credenciales y el healthcheck.

### El panel no refresca

1. Confirmar sesión activa (sin sesión, `/panel/datos` redirige a `/login`).
2. Revisar la consola del navegador; el refresco se pausa si la pestaña está
   oculta.
3. El botón «Actualizar» fuerza una lectura.

### Disco lleno

1. `docker system df`.
2. Los logs de contenedor y las imágenes viejas son los mayores consumidores.
3. **No** borrar el volumen `pgdata`: contiene la evidencia de la tesis.

## 16.4 Respaldo y restauración

Propuesta (no ejecutada en esta máquina por no haber PostgreSQL):

```bash
# Respaldo diario
docker compose exec -T db pg_dump -U laguna -Fc laguna_paca > respaldo-$(date +%F).dump

# Restauración en una base vacía
docker compose exec -T db pg_restore -U laguna -d laguna_paca --clean < respaldo.dump
```

- Retención sugerida: 30 días.
- Probar la restauración antes de la defensa, no durante.
- Las predicciones y las mediciones se respaldan juntas: separarlas rompería la
  trazabilidad de la evaluación.

## 16.5 Retención

- Las mediciones no se borran: son la evidencia.
- Las predicciones se pueden archivar por año si el volumen crece; hoy no se
  justifica.
- Los logs de contenedor se rotan con la configuración del motor
  (`max-size`, `max-file`), que no está en `compose.yaml` todavía.

## 16.6 Capacidad estimada

Con 4 nodos cada 15 minutos:

- 384 muestras/día;
- ~140 000/año;
- una predicción cada vez que se pulsa el botón (4 filas por ejecución).

Es un volumen pequeño: una sola instancia de PostgreSQL y el índice
`(node_id, recorded_at DESC)` son suficientes. No se justifica TimescaleDB.

## 16.7 Lo que no hay

| Falta | Consecuencia |
|---|---|
| Métricas (Micrometer/Prometheus) | No hay series de latencia ni de errores |
| Trazas distribuidas | Diagnóstico manual entre backend e IA |
| Alertas operativas (correo/Slack) | Nadie se entera si el backend cae |
| Rotación de logs configurada | Crecimiento del disco a largo plazo |
| Prueba de carga | No se conoce el límite de la instancia |
| SLO/SLA | No hay operación real que medir |
