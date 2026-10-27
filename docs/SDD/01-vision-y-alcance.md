# 01 · Visión y alcance

## 1.1 Problema

La Laguna de Paca (Junín, Perú) recibe presión antrópica y no cuenta con un
monitoreo continuo y trazable de la calidad del agua. Las mediciones puntuales
no permiten anticipar episodios de acidificación o alcalinización, y un dato
aislado no dice nada sobre la evolución de la laguna.

La tesis propone un sistema que:

1. mide pH, turbidez, temperatura y sólidos disueltos totales (TDS) en cuatro
   estaciones;
2. registra cada medición con su veredicto ECA-Agua;
3. predice el pH a 1, 3 y 6 horas con una red neuronal de grafos
   espacio-temporal (ST-GNN);
4. evalúa después cada predicción contra la medición real, para saber si el
   modelo sirve.

## 1.2 Objetivo general

Diseñar, implementar y evaluar un sistema de software e IoT con ST-GNN para el
monitoreo de la contaminación de la Laguna de Paca, con trazabilidad de las
mediciones y de las predicciones.

## 1.3 Objetivos específicos

- Ingerir telemetría desde nodos ESP32 por HTTP y MQTT, de forma idempotente.
- Aplicar la regla ECA-Agua Categoría 4-E1 (pH) en un único lugar del backend.
- Servir un panel web operativo sin depender de internet.
- Entrenar y servir un ST-GNN con artefacto versionado, separado del backend.
- Evaluar las predicciones contra mediciones reales y registrar el error.
- Desplegar el conjunto con Docker Compose y verificar la calidad con pruebas
  automatizadas.

## 1.4 Alcance funcional (lo que el sistema hace hoy)

| Módulo | Estado | Evidencia |
|---|---|---|
| Ingesta HTTP con JWT | Implementado | `TelemetryController`, `ApiIntegrationTest` |
| Ingesta MQTT QoS 1 | Implementado, deshabilitado por defecto | `TelemetryMqttHandler`, tests |
| Regla ECA-Agua de pH | Implementado | `EcaWaterQualityRule`, 4 tests |
| Histórico con filtros y paginación | Implementado | `TelemetryService`, `PanelHistorialIntegrationTest` |
| Panel React (acceso, tablero, métricas, historial, alertas, mapa) | Implementado | 66 tests de panel + 17 de frontend |
| App Android (login, tablero, alertas, mapa sin Google y predicciones) | MVP implementado | `android-app/`, 14 tests unitarios |
| Predicciones 1/3/6 h | Implementado | `PredictionService`, `PredictionIntegrationTest` |
| Servicio FastAPI + artefacto | Implementado | `ia-model/lagunapaca_gnn/api.py`, 172 tests de IA |
| Evaluación posterior ±30 min | Implementado | `PredictionEvaluator`, tests |
| Idempotencia por `sample_id` | Implementado | V5, `TelemetryServiceTest`, firmware |
| Docker Compose | Configurado, **no ejecutado** en esta máquina | `compose.yaml` |

## 1.5 Fuera de alcance

- Notificaciones push (FCM) y mapa de Google Maps dentro de la app Android: el MVP
  actual permite login, tablero y alertas, pero esas dos integraciones requieren
  credenciales externas.
- Persistencia de la sesión y funcionamiento offline en Android: el JWT vive solo
  en memoria.
- Analítica de otras variables distintas del pH. El modelo puede extenderse,
  pero la regla ECA-Agua implementada es la de pH.
- Certificación ambiental oficial: el sistema es una herramienta académica, no
  un instrumento certificado por una autoridad.
- Telemetría real de campo: no hay datos medidos en la laguna todavía.

## 1.6 Restricciones del proyecto

- Hardware de desarrollo sin GPU dedicada (Ryzen 5 2500U, 10.9 GB de RAM).
- Sin Docker, PostgreSQL local ni SonarQube en la máquina de desarrollo; el
  stack se entrega configurado y validado solo en sintaxis.
- Sin nodos ESP32 físicos: el firmware se prueba en el host con dobles.
- Los datos del experimento son **sintéticos** y las coordenadas de las
  estaciones son **provisionales** (derivadas de OpenStreetMap).

## 1.7 Criterio de éxito

El criterio declarado en la tesis es `R² > 0.90` a 6 horas. El experimento con
datos sintéticos lo cumple (`R² = 0.9289`) y supera la línea base de persistencia
(`0.7403`). Esa cifra valida la **arquitectura**, no el comportamiento real de la
laguna; la validación de campo queda pendiente y se declara en
`26-limitaciones.md`.
