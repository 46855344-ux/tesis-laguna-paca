# 25 · Ética, datos y licencias

## 25.1 Datos personales

El sistema **no** recoge datos personales:

- no hay registro de usuarios;
- el panel tiene un único usuario operativo configurable;
- la telemetría contiene variables ambientales y un identificador de nodo
  (`N1`…`N4`), no de personas;
- los logs no incluyen direcciones IP ni datos de navegación.

Si en el futuro se añaden usuarios reales, habrá que aplicar la Ley 29733 de
Protección de Datos Personales (Perú): finalidad, consentimiento, seguridad y
derechos ARCO.

## 25.2 Datos ambientales

- Las mediciones son un bien público; el panel las muestra sin restricción a
  usuarios autenticados.
- **Los datos del experimento son sintéticos.** Se marcan como tales en:
  - el artefacto (`data_source = synthetic`);
  - la tabla `prediction`;
  - la API (`data_source`);
  - el panel («Datos sintéticos»).
- Las coordenadas de las estaciones son **provisionales** (derivadas de
  OpenStreetMap), no medidas en campo. Se documenta en `stations.py` y en
  `26-limitaciones.md`.
- No se presenta el sistema como instrumento certificado ni como sustituto de un
  monitoreo oficial.

## 25.3 Uso de IA en el desarrollo

- El modelo es un ST-GNN entrenado localmente; no se usan servicios de IA
  generativa en tiempo de ejecución.
- Las decisiones ambientales (alertas) son **deterministas** y auditables: no las
  toma un modelo.
- El modelo solo produce un pronóstico, que se evalúa después contra la realidad.
- Si se usaron asistentes de código durante el desarrollo, el autor revisó y
  probó el resultado; la responsabilidad académica es del autor.

## 25.4 Transparencia de resultados

- Las métricas del modelo se reportan con su origen sintético.
- No se afirma que el R² de 0.9289 sea precisión sobre la laguna.
- Los tests y comandos que no se pudieron ejecutar se declaran (Docker,
  SonarQube, hardware, PostgreSQL real).
- No se inventan certificaciones, cobertura ni resultados.

## 25.5 Licencias de terceros

| Componente | Licencia | Uso |
|---|---|---|
| Spring Boot / Spring Security | Apache 2.0 | Backend |
| PostgreSQL | PostgreSQL License | Base de datos |
| Flyway Community | Apache 2.0 | Migraciones |
| JJWT | Apache 2.0 | JWT |
| Thymeleaf | Apache 2.0 | Plantillas |
| Lucide | ISC | Iconos SVG |
| PyTorch | BSD-3-Clause | Modelo |
| PyTorch Geometric | MIT | GNN |
| NumPy | BSD-3-Clause | Cálculo |
| FastAPI | MIT | Servicio de IA |
| Uvicorn | BSD-3-Clause | Servidor ASGI |
| PlatformIO | Apache 2.0 | Firmware |
| ArduinoJson | MIT | JSON en el nodo |
| 256dpi/MQTT | MIT | MQTT QoS 1 |
| Eclipse Mosquitto | EPL-2.0 | Broker (solo desarrollo) |
| OpenStreetMap | ODbL 1.0 | Contorno de la laguna |

**Atribución obligatoria:** el contorno de la laguna deriva de OpenStreetMap
(`way 26947746`), © colaboradores de OpenStreetMap, bajo ODbL. Debe citarse en la
tesis y en cualquier publicación del panel.

Las licencias de los libros de `docs/LIBROS/` son de sus autores; ese material no
se versiona ni se redistribuye.

## 25.6 Buenas prácticas ambientales del proyecto

- Deep Sleep para reducir consumo.
- No se despliegan nodos sin autorización de la comunidad o autoridad
  competente.
- Los residuos electrónicos y las baterías se gestionan según la normativa local.
- La instalación física no debe alterar el hábitat ni el acceso al agua.

## 25.7 Declaración de límites

Este sistema es una **herramienta académica de monitoreo y pronóstico**. No
reemplaza:

- un laboratorio acreditado;
- un monitoreo oficial de la autoridad ambiental;
- una evaluación de riesgo sanitario.

Cualquier decisión pública basada en sus datos debe acompañarse de mediciones
certificadas.
