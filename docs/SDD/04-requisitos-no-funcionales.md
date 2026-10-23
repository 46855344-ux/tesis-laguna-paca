# 04 · Requisitos no funcionales

| ID | Requisito | Cómo se cumple | ¿Verificado? |
|---|---|---|---|
| RNF-01 | Java 21 + Spring Boot 3.2 en el backend. | `pom.xml`, JDK 21 (JBR de IntelliJ; el JDK 26 del sistema rompe Boot 3.2). | Sí: 145 tests |
| RNF-02 | Python 3.12 + PyTorch/PyG para la IA. | `.venv`, `requirements.txt` | Sí: 172 tests |
| RNF-03 | La indisponibilidad de la IA no puede impedir registrar telemetría ni aplicar alertas. | La ingesta no conoce `AiClient`; la evaluación se absorbe en `evaluarSinRomperIngesta`. | Sí: `iaCaida_noImpideLaIngesta` |
| RNF-04 | La IA no puede bloquear al backend: timeouts explícitos. | `HttpClient` con 2 s de conexión y 15 s de lectura. | Sí: `predict_lento_esNoDisponible` |
| RNF-05 | Ninguna credencial ni clave puede estar en el repositorio. | `.env` ignorado; `secrets.h` ignorado; `application.yml` lee variables. | Parcial: revisión manual; no hay escáner de secretos en CI |
| RNF-06 | El panel debe funcionar sin internet. | SVG del mapa y gráfico hechos a mano en React; sin CDN. Google Maps es opcional. | Sí: `PanelMapaIntegrationTest`, `components.test.tsx` |
| RNF-07 | Accesibilidad AA en el panel. | Contrastes medidos, foco visible, 44 px, `aria-*`, `prefers-reduced-motion`. | Parcial: contrastes medidos; sin auditoría automática |
| RNF-08 | Fechas de pantalla en `America/Lima`; instantes persistidos como `Instant` UTC. | `ZoneId` en el panel; `TIMESTAMPTZ`; test de la medianoche de Lima. | Sí: `PanelHistorialIntegrationTest` |
| RNF-09 | La API debe ser stateless y sin CSRF; el panel con sesión y CSRF. | Dos cadenas de filtros en `SecurityConfig`. | Sí: `PanelIntegrationTest`, `ApiIntegrationTest` |
| RNF-10 | Despliegue reproducible con Docker Compose. | `backend/Dockerfile`, `ia-model/Dockerfile`, `compose.yaml`, CI. | Parcial: CI ejecutada; `docker compose build/up` no (sin Docker local) |
| RNF-11 | La telemetría debe sobrevivir a reintentos sin duplicados. | `sample_id` + índice único parcial (V5). | Sí: servicio, API y firmware |
| RNF-12 | Los tests deben correr sin hardware ni servicios externos. | H2, `HttpServer` del JDK, `fetch` simulado, dobles de sensores. | Sí: 145 + 17 + 38 + 172 + 6 |
| RNF-13 | El panel debe refrescar sin recargar y pausar si la pestaña está oculta. | React consulta `/panel/api/datos` cada 20 s y escucha `visibilitychange`. | Manual (no hay test E2E de navegador) |
| RNF-14 | El modelo no debe entrenarse al arrancar el servicio. | `registry.load_bundle`; sin artefacto → `/predict` 503. | Sí: `test_api.py` |
| RNF-15 | El error de una predicción debe ser reproducible y auditable. | Se guardan `predicted_value`, `observed_value`, `error`, `evaluated_at`. | Sí: `PredictionEvaluatorTest` |
| RNF-16 | El sistema debe declarar el origen de los datos del modelo. | `data_source` en el artefacto, la tabla y la API. | Sí |
| RNF-17 | Cobertura de pruebas del backend como indicador, no como meta. | JaCoCo: 91.7 % de instrucciones, 68.8 % de ramas. | Sí: `mvn test` |
| RNF-18 | El firmware debe caber en la partición por defecto del ESP32. | 72.3 % (HTTP) y 59.8 % (MQTT) de 1310 KB. | Sí: build |
| RNF-19 | El sistema debe ser operable con un solo comando en demo. | Perfil `demo` con H2 en memoria. | Sí: documentado y probado en tests de contexto |
| RNF-20 | El código y la documentación visible deben estar en español (es-PE). | Revisión; identificadores técnicos en inglés. | Revisión manual |

## Objetivos no medidos (declarados como pendientes)

| Objetivo | Por qué no se midió |
|---|---|
| Latencia < 300 ms en consultas de 20 filas | No hay PostgreSQL local; H2 no representa el plan real |
| Disponibilidad 99 % | No hay despliegue continuo ni histórico de operación |
| Consumo energético del nodo | Requiere hardware y medición eléctrica |
| Precisión del modelo en la laguna real | No hay datos de campo |
| Cobertura de pruebas del firmware y de la IA | No se configuró `gcov`/`coverage.py`; se reporta el número de casos |
