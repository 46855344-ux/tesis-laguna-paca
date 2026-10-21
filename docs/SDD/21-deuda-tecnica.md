# 21 · Deuda técnica

Deuda **declarada**, no oculta. Cada ítem indica por qué no se pagó en esta
entrega.

| ID | Deuda | Impacto | Prioridad | Por qué no se pagó |
|---|---|---|---|---|
| DT-01 | Validación del PostgreSQL de producción (contenedor y arranque desde Spring Boot) | Una diferencia con el PostgreSQL embebido podría aparecer al desplegar | Media | La V1–V5 ya se ejecutó en PostgreSQL 16.9 embebido; falta el contenedor |
| DT-02 | `docker compose build/up` no ejecutado | Las imágenes podrían no construir | Alta | No hay Docker |
| DT-03 | TLS en backend y MQTT | Credenciales y datos en claro | Alta | Requiere certificados y proxy |
| DT-04 | SonarQube no ejecutado | Sin análisis estático real | Media | No hay servidor |
| DT-05 | Sin escaneo de secretos en CI | Fuga futura | Media | Se priorizó el pipeline básico |
| DT-06 | Sin rate limiting en `/auth/token` | Fuerza bruta | Media | Red controlada |
| DT-07 | Un solo usuario sin roles | Sin separación de funciones | Media | Alcance académico |
| DT-08 | Sin métricas ni trazas | Diagnóstico manual | Media | Volumen pequeño |
| DT-09 | Sin notificaciones push/correo | Alerta solo visible en panel | Media | App Android fuera de alcance |
| DT-10 | `HTTPClient` sin TLS en el nodo | Tráfico interceptable | Alta | Requiere hardware y CA |
| DT-11 | Calibración nominal de sensores | Medidas no confiables | Alta | Requiere laboratorio |
| DT-12 | Coordenadas provisionales | El grafo no es el real | Alta | Requiere campaña de campo |
| DT-13 | Modelo entrenado con sintético | No valida la laguna | Alta | Cargador `--csv` listo; faltan datos de campo |
| DT-14 | Sin prueba de carga | Límite desconocido | Baja | Sin operación real |
| DT-15 | Sin OTA en el firmware | Actualización manual | Baja | Cambia particiones |
| DT-16 | Sin CSP ni cabeceras de seguridad | Defensa en profundidad | Baja | No hay despliegue público |
| DT-17 | `LIKE` del historial no escapa `%`/`_` | Búsqueda con comodines | Baja | No es inyección SQL; es UX |
| DT-18 | Sin Android | Funcionalidad móvil ausente | Media | Fuera de alcance |
| DT-19 | `requirements.txt` incluye pytest/httpx en la imagen | Imagen algo más grande | Baja | Simplicidad; PyTorch domina el tamaño |
| DT-20 | Sin SBOM ni firma de imágenes | Cadena de suministro | Baja | Etapa académica |

## 21.1 Deuda que **no** existe (decisiones deliberadas)

- **No** hay JWT en `localStorage`: no es deuda, es una decisión de seguridad.
- **No** hay una tabla de estaciones: no hay atributos reales que guardar.
- **No** hay TimescaleDB/Neo4j: no se justifican con 4 nodos.
- **No** hay React: el panel renderizado en servidor cumple y funciona sin build.
- **No** se recalculan alertas en el panel: sería duplicar la regla.

## 21.2 Orden sugerido para pagar

1. DT-01/DT-02 (contenedor PostgreSQL + `compose build/up`) → valida el despliegue real.
2. DT-03/DT-10 (TLS) → antes de cualquier exposición.
3. DT-12/DT-13 (datos de campo) → lo que la tesis necesita para ser ciencia.
4. DT-04/DT-05 (calidad y secretos).
5. DT-09/DT-18 (Android y notificaciones), si el alcance lo permite.
