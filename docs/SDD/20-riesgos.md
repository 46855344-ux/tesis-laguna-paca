# 20 · Riesgos

Escala: probabilidad e impacto `Baja` / `Media` / `Alta`.

| ID | Riesgo | Prob. | Impacto | Mitigación | Residual |
|---|---|---|---|---|---|
| R-01 | No conseguir datos de campo de la laguna | Alta | Alto | Entrenar con sintético y declararlo; cargador `--csv` listo; plan de campaña de muestreo | Alto |
| R-02 | Coordenadas de estaciones provisionales | Alta | Medio | Importarlas de `stations.py`; reemplazarlas antes de reportar | Medio |
| R-03 | Sobreajuste al simulador | Media | Alto | Propiedades del modelo, partición cronológica con hueco, línea base | Medio |
| R-04 | La IA no está disponible en la demo | Media | Bajo | La ingesta no depende de ella; aviso claro; artefacto versionado | Bajo |
| R-05 | Pérdida de datos por cortes de luz en el nodo | Media | Medio | `bootId` aleatorio + secuencia en RTC | Bajo |
| R-06 | Clave de Google expuesta | Alta | Medio | Rotarla y restringir por referente | Medio hasta rotar |
| R-07 | Docker no validado en la máquina | Alta | Medio | YAML validado; build en CI; declarar limitación | Medio |
| R-08 | Sin TLS en backend/MQTT | Alta | Alto en producción | Reverse proxy y 8883; no exponer a internet | Alto en producción |
| R-09 | Un solo usuario sin roles | Media | Medio | Aceptable para tesis; documentar | Medio |
| R-10 | La migración falla en el contenedor de producción | Baja | Medio | V1–V5 ejecutadas en PostgreSQL 16.9 embebido; falta probar el contenedor | Bajo |
| R-11 | Deriva del modelo (dataset shift) | Media | Alto | Evaluación continua con `prediction.error`; reentrenar | Alto sin campo |
| R-12 | Alcance creciente (Android, notificaciones) | Media | Medio | MVP Android acotado; FCM/mapa declarados fuera | Bajo |
| R-13 | Pérdida de la base de datos | Baja | Alto | `pg_dump` diario; volumen nombrado | Bajo |
| R-14 | Broker MQTT anónimo en producción | Media | Alto | `mosquitto_passwd` + TLS | Medio hasta configurar |
| R-15 | Saturación de la IA (CPU) | Baja | Medio | Un lock de inferencia; timeout; límites de recursos | Bajo |
| R-16 | Fuga de secretos en git | Media | Alto | `.gitignore`, `.env.example`, revisión; falta gitleaks | Medio |
| R-17 | Tiempo de la tesis | Alta | Alto | Priorizar criterio R², demo y documentación | Medio |

## 20.1 Riesgos aceptados

- **Android acotado**: hay MVP de login, tablero, alertas y mapa estático (Canvas
  con contorno embebido); FCM, mapa interactivo y modo offline no se entregan y
  se declaran como pendientes.
- **Sin notificaciones push**: el panel y la app son los canales de alerta.
- **Sin métricas Prometheus**: el volumen es pequeño y no hay operación real.
- **Un solo usuario**: suficiente para una demo académica controlada.

## 20.2 Disparadores de revisión

- Si se consiguen datos de campo → R-01/R-03 cambian y hay que reentrenar.
- Si se publica el repositorio → rotar claves y activar escaneo de secretos.
- Si se expone a internet → TLS y autenticación del broker son bloqueantes.
- Si el número de nodos crece a decenas → revisar TimescaleDB y colas.
