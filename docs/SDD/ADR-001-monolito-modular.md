# ADR-001 · Monolito modular con Spring Boot

**Estado:** aceptado · **Fecha:** 2026

## Contexto

La tesis necesita una API, un panel, ingesta HTTP/MQTT y reglas ambientales. El
equipo es una persona, el hardware es limitado y la demo debe arrancar con un
comando.

## Decisión

Un solo JAR de Spring Boot con módulos por dominio (`telemetry`, `prediction`,
`panel`, `security`, `station`). Los límites son de paquetes y de dependencias
declaradas, no de red.

## Alternativas

- **Microservicios** (telemetría, panel, IA): más despliegues, más configuración,
  más puntos de fallo y ninguna ventaja con este volumen.
- **Monolito en capas globales** (`controller/service/repository`): mezcla
  dominios y dificulta la trazabilidad.

## Consecuencias

- Un solo `mvn spring-boot:run` para la demo.
- El panel puede leer repositorios sin llamar a la API (menos duplicación).
- Si un módulo crece, se puede extraer: los paquetes ya están separados.
- Riesgo: acoplamiento accidental si se importa `panel` desde `telemetry`
  (regla prohibida y revisada).
