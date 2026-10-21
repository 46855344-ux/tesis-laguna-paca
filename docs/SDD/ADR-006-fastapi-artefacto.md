# ADR-006 · Servicio FastAPI con artefacto y cliente HTTP del JDK

**Estado:** aceptado · **Fecha:** 2026

## Contexto

El ST-GNN está en Python/PyTorch. El backend está en la JVM. Hay que integrarlos
sin acoplar el despliegue ni permitir que una caída de la IA bloquee la ingesta.

## Decisión

- Servicio FastAPI separado, en `ia-model/`, que carga un **artefacto** `.pt` al
  arrancar. No entrena al arrancar.
- Contrato HTTP: `/health`, `/model`, `/predict`; token interno
  `X-Internal-Token`.
- El backend usa `java.net.http.HttpClient` (sin dependencias nuevas) con 2 s de
  conexión y 15 s de lectura.
- Si la IA falla: 503 (no disponible) o 502 (contrato incumplido). La ingesta y
  las alertas no dependen de ella.
- La IA no publica puerto al host en Compose.

## Alternativas

- **Cargar PyTorch en la JVM** (DJL, ONNX): posible, pero obliga a convertir el
  modelo y a mantener dos representaciones; el ecosistema del grafo está en
  Python.
- **Feign/WebClient**: añade dependencias y no mejora un cliente de dos
  endpoints.
- **Entrenar al arrancar**: arranque lento e impredecible; un artefacto es
  reproducible y versionable.
- **Llamar a la IA desde el navegador**: expondría el servicio y duplicaría la
  validación.

## Consecuencias

- El artefacto es la unidad de versionado del modelo.
- Sin artefacto, `/predict` responde 503; el sistema sigue monitoreando.
- El contrato se valida en ambos lados (Pydantic y `AiClient`).
- El lock de inferencia serializa las peticiones: con CPU y un solo modelo es
  aceptable.
