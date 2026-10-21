# ADR-003 · Panel con Thymeleaf renderizado en servidor

**Estado:** aceptado · **Fecha:** 2026

## Contexto

El panel debe mostrar tablero, historial, alertas, mapa y predicciones; debe
funcionar en la demo sin internet y sin un build de frontend.

## Decisión

Thymeleaf en el mismo JAR, con CSS propio (tokens + componentes) y JavaScript
mínimo para el refresco y el gráfico. Sin framework SPA.

## Alternativas

- **React/Vue**: exige Node, build, servidor estático o integración, y añade un
  segundo ecosistema. La interactividad necesaria es baja (formularios GET/POST y
  un refresco).
- **JSP**: habría exigido empaquetar en WAR y perder `spring-boot:run` para
  ganar menos reutilización.

## Consecuencias

- La demo es un solo comando y funciona sin CDN.
- El HTML llega renderizado: sin JavaScript, tarjetas e historial siguen
  completos.
- El estado se lee en el servidor; no hay que exponer un JWT al navegador.
- Si el panel creciera a una aplicación compleja, habría que evaluar una SPA con
  un ADR nuevo.
