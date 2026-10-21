# 00 · Auditoría inicial

**Fecha:** 2026-10-20
**Commit de partida:** `bd10567` (rama `main`, árbol limpio)
**Autor de la auditoría:** equipo de modernización (OpenCode)

---

## 1. Alcance

Inventario del repositorio antes de cualquier cambio, ejecución de la línea base de
pruebas y registro de riesgos. Este documento es la referencia contra la que se compara
la validación final (ver `28-registro-de-evidencias.md`).

## 2. Módulos encontrados

| Módulo | Contenido | Estado |
|---|---|---|
| `backend/` | Spring Boot 3.2.12, Java 21, Maven, Thymeleaf, JPA, Flyway, JWT, MQTT (Paho), springdoc | Funcional |
| `iot-firmware/` | PlatformIO, núcleo C++11 portable, HTTP y MQTT QoS 1 | Funcional (sin hardware) |
| `ia-model/` | Python 3.12, PyTorch 2.14.1 CPU, PyTorch Geometric 2.8.1, ST-GNN | Funcional con datos sintéticos |
| `android-app/` | Vacío | Pendiente |
| `docs/` | Material de referencia (tesis y libros), **no versionado** | Excluido de git |
| `AGENTS.md`, `README.md` | Decisiones y guía de arranque | Actualizados |

## 3. Línea base de pruebas (ejecutada antes de modificar)

| Conjunto | Comando | Resultado real |
|---|---|---|
| Backend | `mvn test` con `JAVA_HOME` en el JBR 21 | **88 tests, 0 fallos** |
| Firmware | `pio test -e native` con MinGW en el PATH | **32 tests, 0 fallos** |
| IA | `.venv\Scripts\python.exe -m pytest` | **134 tests, 0 fallos** (3 min 33 s) |

No hay Docker ni SonarQube instalados en la máquina de desarrollo. No hay CI configurado.

## 4. Arquitectura observada

- Ingesta HTTP (`POST /api/v1/telemetry`) y MQTT opcional (`app.mqtt.enabled=false`).
- Regla ECA-Agua en `EcaWaterQualityRule` (pH < 6.5 → `PH_LOW`, pH > 9.0 → `PH_HIGH`);
  los límites exactos **no** generan alerta.
- Persistencia: `water_quality_sample` + `alert_reason` (Flyway V1–V3) y auditoría de
  atención de alertas (V4).
- Seguridad: dos cadenas de filtros (panel con sesión + CSRF; API con JWT sin estado).
- Panel Thymeleaf con tokens CSS, mapa SVG offline y Google Maps opcional.
- IA: `lagunapaca_gnn` (dataset sintético, grafo, ventanas, ST-GNN, entrenamiento) y
  `train_stgnn.py`. **No existe servicio de inferencia ni artefacto de modelo guardado.**

## 5. Hallazgos y riesgos

| ID | Hallazgo | Riesgo | Prioridad |
|---|---|---|---|
| H-01 | No hay servicio de inferencia: la IA solo se ejecuta como experimento | El sistema no puede predecir en operación | Alta |
| H-02 | No existe tabla de predicciones ni evaluación posterior | No se puede auditar ni medir el error real | Alta |
| H-03 | La ingesta no deduplica: un reintento HTTP o un QoS 1 repetido crea otra fila | Series infladas y alertas duplicadas | Alta |
| H-04 | No hay Docker, Compose ni CI | Despliegue no reproducible | Media |
| H-05 | No hay configuración de SonarQube ni cobertura publicada | Calidad no medible | Media |
| H-06 | Usuarios en memoria (`InMemoryUserDetailsManager`), sin roles diferenciados | No hay gestión de usuarios ni mínimo privilegio real | Media |
| H-07 | Sin límite de intentos de login ni rate limiting | Fuerza bruta sobre el panel | Media |
| H-08 | Swagger UI y actuator públicos | Exposición de contrato y estado | Baja (demo) |
| H-09 | Coordenadas de estaciones **provisionales** (OSM) | El mapa y el grafo no son puntos de campo | Alta (científica) |
| H-10 | Datos de entrenamiento **sintéticos** | El R² 0.9289 no es evidencia de campo | Alta (científica) |
| H-11 | Clave de Google Maps expuesta en el chat (no en el repositorio) | Uso no autorizado con cargo a la cuenta | Alta (operativa) |
| H-12 | Perfil `demo` con H2 y Flyway desactivado | Las migraciones no se prueban en H2 | Media |
| H-13 | `android-app/` vacío | Funcionalidad móvil pendiente | Planificada |
| H-14 | Sin política de retención ni respaldo documentada | Crecimiento y pérdida de datos | Media |
| H-15 | `docs/` no versionado | La documentación de tesis no viaja con el código | Aceptada (decisión previa) |

## 6. Restricciones del entorno

- **Sin Docker**: las imágenes y `compose.yaml` se entregan y se validan sintácticamente,
  pero **no se ejecutan aquí**. Se documenta como limitación en las evidencias.
- **Sin servidor SonarQube**: se prepara configuración y cobertura; el análisis no se
  declara ejecutado.
- **Sin hardware ESP32**: el firmware se valida con tests nativos; TLS y sensores reales
  quedan pendientes de validación física.
- **Sin datos de campo**: cualquier métrica del ST-GNN es sobre simulación.

## 7. Decisiones preservadas

1. Backend como única fuente de verdad de las reglas de negocio.
2. Panel con sesión + CSRF; API con JWT sin estado.
3. Panel lee repositorios, no la API REST.
4. Fechas de presentación en `America/Lima`.
5. Mapa con alternativa offline (SVG) y Google opcional.
6. Perfil `demo` sin PostgreSQL.
7. Sin secretos en el repositorio.
8. Versiones mayores de Java, Spring Boot, Python y PyTorch sin cambios.

## 8. Plan de trabajo priorizado

| Fase | Entregable | Estado |
|---|---|---|
| 0 | Este documento | Hecho |
| 1 | Requisitos, reglas, arquitectura y ADR | Hecho |
| 2 | Predicciones (V5), idempotencia, cliente IA y endpoints | Hecho |
| 3 | Servicio FastAPI de inferencia + pruebas | Hecho |
| 4 | Panel: predicciones y estados de IA | Hecho |
| 5 | Seguridad y firmware (revisión + `sample_id`) | Hecho |
| 6 | Docker, Compose y `.dockerignore` | Hecho (no ejecutado) |
| 7 | Cobertura, SonarQube y CI | Configurado |
| 8 | Documentación SDD y manuales | Hecho |
| 9 | Validación final comparada con la línea base | Hecho |

## 9. Criterio de aceptación de la modernización

Se considera cumplido cuando: el backend compila, la demo arranca sin PostgreSQL, las
reglas de pH conservan sus pruebas de límites, la ingesta funciona con la IA caída, las
predicciones se persisten separadas de las mediciones, la integración Java–Python tiene
pruebas, el panel distingue observado/predicho/ausente, las migraciones son aditivas, el
firmware mantiene sus 32 tests y el README documenta comandos comprobados.
