# ADR-004 · Dos superficies, dos mecanismos de autenticación

**Estado:** aceptado · **Fecha:** 2026

## Contexto

El backend sirve una API para el ESP32 (y futuro Android) y un panel para un
navegador. Los clientes tienen amenazas distintas.

## Decisión

Dos cadenas de filtros en `SecurityConfig`:

1. `@Order(1)` panel: sesión con formulario (`JSESSIONID`), CSRF activo, logout
   por POST.
2. `@Order(2)` API: stateless con JWT, sin cookies y sin CSRF.

**Un JWT válido no abre el panel.**

## Alternativas

- **JWT en `localStorage` para el panel**: expuesto a XSS; el token no se puede
  revocar y el formulario seguiría necesitando CSRF.
- **Sesión para la API**: el ESP32 tendría que manejar cookies y CSRF, y la API
  dejaría de ser stateless.
- **Solo JWT en cookie HttpOnly**: viable, pero mezcla CSRF y CORS y complica el
  cliente embebido.

## Consecuencias

- Cada superficie usa el mecanismo adecuado a su cliente.
- El orden de las cadenas es crítico y está probado.
- Hay un test que fija que el JWT no abre el panel y que la API conserva el 401.
- La app Android usará JWT, como el ESP32.
