# Documentación de diseño (SDD)

Documentación de la tesis **«Sistema de software e IoT con Redes Neuronales de
Grafos (GNN) para el monitoreo de la contaminación en la Laguna de Paca»**.

Esta carpeta es la excepción versionada de `docs/`: el resto es material de
referencia (tesis y libros) y no se versiona. Todo está en español; los
identificadores técnicos van en inglés.

## Cómo leerla

- Si buscas **qué hace el sistema**: `01` y `03`.
- Si buscas **cómo está construido**: `06`, `07`, `08` y `09`.
- Si buscas **el modelo y su contrato**: `10`.
- Si buscas **cómo se opera y despliega**: `11`–`16`.
- Si buscas **qué se probó y qué no**: `17`, `18`, `26` y `28`.
- Si buscas **por qué se tomó una decisión**: los `ADR-00x`.

## Índice

### Contexto y requisitos

| Documento | Contenido |
|---|---|
| [00 · Auditoría inicial](00-auditoria-inicial.md) | Línea base, hallazgos H-01…H-15 y plan por fases |
| [01 · Visión y alcance](01-vision-y-alcance.md) | Problema, objetivos, alcance y restricciones |
| [02 · Interesados y actores](02-interesados-y-actores.md) | Quién usa el sistema y qué espera |
| [03 · Requisitos funcionales](03-requisitos-funcionales.md) | RF-01…RF-29 con su prueba |
| [04 · Requisitos no funcionales](04-requisitos-no-funcionales.md) | RNF-01…RNF-20 y lo que no se midió |
| [05 · Casos de uso](05-casos-de-uso.md) | CU-01…CU-08 con flujos y alternativas |

### Arquitectura y datos

| Documento | Contenido |
|---|---|
| [06 · Arquitectura](06-arquitectura.md) | Contexto, contenedores, paquetes y flujos |
| [07 · Modelo de dominio](07-modelo-de-dominio.md) | Entidades, invariantes y estados |
| [08 · Modelo de datos](08-modelo-de-datos.md) | Tablas, índices, migraciones y retención |
| [09 · API REST](09-api-rest.md) | Endpoints, códigos y ejemplos |
| [10 · Contrato de IA](10-contrato-ia.md) | `/health`, `/model`, `/predict` y artefacto |

### Producto y operación

| Documento | Contenido |
|---|---|
| [11 · Panel web](11-panel-web.md) | Rutas, tablero, historial, alertas, mapa y diseño |
| [12 · Firmware IoT](12-firmware-iot.md) | Pines, ciclo, payload, `sample_id` y transportes |
| [13 · MQTT](13-mqtt.md) | Topic, QoS, configuración y broker |
| [14 · Seguridad](14-seguridad.md) | Amenazas, JWT, sesión, CSRF y deuda |
| [15 · Despliegue](15-despliegue.md) | Dockerfiles, Compose, perfiles y variables |
| [16 · Operación](16-operacion-y-observabilidad.md) | Salud, logs, runbook y respaldos |

### Calidad y gestión

| Documento | Contenido |
|---|---|
| [17 · Estrategia de pruebas](17-estrategia-de-pruebas.md) | Pirámide, 348 tests, cobertura y límites |
| [18 · Trazabilidad](18-trazabilidad.md) | RF/RNF → código → prueba |
| [19 · Plan de sprints](19-plan-de-sprints.md) | Sprints, historias y definición de terminado |
| [20 · Riesgos](20-riesgos.md) | R-01…R-17 con mitigación |
| [21 · Deuda técnica](21-deuda-tecnica.md) | DT-01…DT-20 y orden de pago |
| [22 · Guía de demostración](22-guia-de-demostracion.md) | Guion para la defensa, con planes B |
| [23 · Manual de usuario](23-manual-de-usuario.md) | Uso del panel paso a paso |
| [24 · Manual técnico](24-manual-tecnico.md) | Toolchain, comandos y tareas frecuentes |
| [25 · Ética y datos](25-etica-y-datos.md) | Datos personales, transparencia y licencias |
| [26 · Limitaciones](26-limitaciones.md) | Lo que el sistema **no** demuestra |
| [27 · Glosario](27-glosario.md) | Términos técnicos y ambientales |
| [28 · Registro de evidencias](28-registro-de-evidencias.md) | Comandos, resultados y lo no ejecutado |

### Decisiones de arquitectura (ADR)

| ADR | Decisión |
|---|---|
| [ADR-001](ADR-001-monolito-modular.md) | Monolito modular con Spring Boot |
| [ADR-002](ADR-002-postgresql-flyway.md) | PostgreSQL + Flyway; sin TimescaleDB ni Neo4j |
| [ADR-003](ADR-003-thymeleaf.md) | Panel con Thymeleaf renderizado en servidor |
| [ADR-004](ADR-004-dos-autenticaciones.md) | Dos superficies, dos mecanismos de autenticación |
| [ADR-005](ADR-005-predicciones-tabla-propia.md) | Predicciones en tabla propia y evaluación diferida |
| [ADR-006](ADR-006-fastapi-artefacto.md) | FastAPI con artefacto y cliente HTTP del JDK |
| [ADR-007](ADR-007-idempotencia-sample-id.md) | Idempotencia con `sample_id` opcional |
| [ADR-008](ADR-008-mapa-svg-google.md) | Mapa SVG propio y Google Maps opcional |
| [ADR-009](ADR-009-datos-sinteticos.md) | Datos sintéticos y coordenadas provisionales, declarados |

## Resumen de evidencia

| Suite | Tests | Estado |
|---|---|---|
| Backend | 138 | Verde |
| Firmware | 38 | Verde |
| IA | 172 | Verde |
| **Total** | **348** | |

Cobertura del backend: 91.5 % de instrucciones, 70.0 % de ramas. Criterio de la
tesis: `R² = 0.9289` a 6 h con datos sintéticos (persistencia: 0.7403).

**No ejecutado en esta máquina** (declarado en `26` y `28`): Docker Compose,
SonarQube, migración V5 contra PostgreSQL real, CI en un runner y pruebas en
hardware. Los datos de campo siguen pendientes.
