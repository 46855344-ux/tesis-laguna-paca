# 19 · Plan de sprints

Metodología: Scrum con sprints de 2 semanas para el software y CRISP-DM para el
modelo. El plan siguiente es el **propuesto**; la ejecución fue iterativa y varios
sprints se solaparon. La columna «Estado» refleja lo que existe hoy, no lo que se
planificó.

| Sprint | Objetivo | Entregables | Criterio de salida | Estado |
|---|---|---|---|---|
| S0 | Línea base y auditoría | `00-auditoria-inicial.md`, `.gitignore`, línea base de 254 tests | Hallazgos priorizados | Hecho |
| S1 | Requisitos y arquitectura | `01`–`08`, ADR-001…004 | Requisitos trazables y arquitectura aprobada | Hecho |
| S2 | Backend de ingesta | API, JWT, ECA-Agua, histórico, MQTT | 88 tests verdes | Hecho |
| S3 | Panel operativo | Tablero, métricas, historial, alertas, mapa, tokens | 51 tests de panel | Hecho |
| S4 | Firmware | Núcleo portable, HTTP/MQTT, Deep Sleep | 32 tests nativos + builds | Hecho |
| S5 | Modelo | Grafo, ventanas, ST-GNN, entrenamiento | R² > 0.90 sintético | Hecho |
| S6 | Servicio IA e integración | FastAPI, artefacto, predicciones, evaluación, carga de CSV de campo | 172 tests IA + 50 tests backend nuevos | Hecho |
| S7 | Calidad y despliegue | Docker, Compose, CI, JaCoCo, Sonar config | `config` validado y V5 + IT en PostgreSQL 16.9; imágenes y CI no ejecutados | Parcial |
| S8 | Documentación y defensa | `docs/SDD`, README, guion de demo | Documento completo y reproducible; limitaciones declaradas en 26 y 28.4 | Hecho |

## 19.1 Historias representativas

| Historia | Puntos | Sprint |
|---|---|---|
| Como nodo, quiero reintentar sin duplicar la muestra | 5 | S6 |
| Como operador, quiero ver si el pH está fuera de la ECA | 3 | S3 |
| Como operador, quiero atender una alerta sin borrar el veredicto | 5 | S3 |
| Como investigador, quiero predecir el pH a 6 h | 8 | S5–S6 |
| Como investigador, quiero saber si la predicción se cumplió | 5 | S6 |
| Como jurado, quiero distinguir datos sintéticos de campo | 2 | S6 |
| Como operador, quiero que el panel funcione sin internet | 3 | S3 |
| Como autor, quiero desplegar con un comando | 5 | S7 |

## 19.2 Definición de terminado

Una historia está terminada cuando:

1. tiene al menos una prueba que falla antes y pasa después;
2. el total de la suite sigue verde;
3. el texto visible está en español;
4. si cambia el contrato, se actualiza `openapi.yaml` en el mismo commit;
5. si cambia un total o un comando, se actualizan README y AGENTS;
6. si algo no se pudo verificar, se declara en `26-limitaciones.md`.

## 19.3 Riesgos de planificación

- El sprint de datos de campo no existe todavía: el cargador `field_data.py` y
  `train_stgnn.py --csv` ya están probados, pero sin campaña de muestreo el modelo
  no puede validarse científicamente.
- S7 no se puede cerrar en esta máquina por falta de Docker; se cierra cuando
  haya un entorno con Docker o un runner de CI.
- La app Android no está en el plan de esta entrega; si se incluye, requiere al
  menos dos sprints (contrato, MVVM, FCM, pruebas).
