# 02 · Interesados y actores

## 2.1 Interesados

| Interesado | Qué espera | Cómo se atiende |
|---|---|---|
| Autor de la tesis (Rony) | Un sistema defendible, reproducible y honesto | Pruebas, artefacto versionado, documentación de límites |
| Jurado / evaluador académico | Trazabilidad requisito → código → prueba | Matriz en `18-trazabilidad.md` |
| Comunidad de la laguna | Saber si el agua está dentro de la norma | Panel con veredicto ECA-Agua y predicción |
| Municipalidad / autoridad ambiental | Evidencia de episodios | Histórico con fechas, motivos y atención de alertas |
| Operador del sistema | Ver el estado y atender alertas | Panel web con sesión |
| Investigador de datos | Reproducir el experimento | `train_stgnn.py`, artefacto, métricas |

## 2.2 Actores del sistema

### Operador (usuario humano)

Hoy es el mismo usuario `admin` configurado por variables de entorno. Puede:

- iniciar sesión en el panel;
- ver el tablero, el mapa, el historial y las alertas;
- atender o reabrir una alerta;
- lanzar una predicción.

No puede modificar el veredicto ECA-Agua de una muestra: la atención es una
dimensión aparte (ver `08-modelo-de-datos.md`).

### Nodo sensor ESP32

Se autentica contra `/api/v1/auth/token` (HTTP) o con las credenciales del
broker (MQTT). Envía el payload de telemetría y, desde esta entrega, un
`sample_id` que hace idempotente el reintento.

### Servicio de inferencia (FastAPI)

No es un actor de negocio: es un componente interno. El backend lo consulta y
valida su respuesta. Nunca decide alertas ni escribe en la base.

### Servicio de identidad (Spring Security)

Emite y valida JWT para la API; gestiona la sesión y el CSRF del panel. No es un
servicio externo: vive en el mismo JAR.

## 2.3 Necesidades por actor

| Necesidad | Actor | Requisito |
|---|---|---|
| Registrar una muestra sin duplicarla | ESP32 | RF-15 |
| Saber si el pH está fuera de la ECA | Operador | RF-03 |
| Ver la evolución de la laguna | Operador | RF-05, RF-11 |
| Anticipar un episodio | Operador | RF-07 |
| Saber si la predicción se cumplió | Investigador | RF-09 |
| Distinguir dato real de simulado | Jurado | RF-21 |
| Operar sin internet | Operador | RNF-06 |
| No perder la ingesta si la IA falla | Operador | RNF-03 |

## 2.4 Supuestos

- Los nodos tienen conectividad WiFi intermitente; el reintento es normal.
- El backend es la única fuente de verdad del veredicto ambiental.
- El usuario del panel es de confianza y accede desde una red controlada; por
  eso hoy hay un único usuario configurable.
- El servicio de IA puede estar caído sin afectar al monitoreo.
