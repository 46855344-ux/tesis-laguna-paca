# ADR-010 · Panel React SPA con API JSON

**Estado:** aceptado · **Fecha:** 2026 · **Reemplaza:** [ADR-003](ADR-003-thymeleaf.md)

## Contexto

El panel creció: tablero con refresco cada 20 s, filtros y paginación con estado
en la URL, atención de alertas con auditoría, predicciones y un mapa con
contorno propio y Google Maps opcional. Thymeleaf obligaba a repetir fragmentos
y a mezclar la presentación con los controladores. Además, el usuario pidió
migrar todo el frontend web a React + TypeScript sin romper la demo, la
seguridad ni los contratos del backend.

## Decisión

- **SPA React 19 + TypeScript + Vite** con React Router.
- El **build de Vite** se escribe en
  `backend/src/main/resources/static/panel` y Spring Boot lo sirve desde el
  mismo JAR (`base: '/panel/'`). No hay un servicio Node en producción.
- Los datos se exponen como **API JSON bajo `/panel/api/**`**, reutilizando
  `PanelDashboardService`, `PanelHistorialService`, `PanelAlertasService` y
  `PredictionService`.
- La autenticación del panel sigue siendo **sesión con formulario + CSRF**; la
  API pública `/api/**` sigue siendo **JWT stateless**. Un JWT no abre el panel.
- `/panel/api/**` responde **401 JSON** si la sesión caduca; las rutas de página
  redirigen a `/login`. `/login`, `/panel/csrf` y los assets de Vite son
  públicos.
- La clave opcional de Google se entrega solo en `GET /panel/api/sesion`; no
  viaja en el tablero que se refresca cada 20 s.
- Se conserva `/panel/datos` como alias del tablero para no romper el contrato
  anterior.

## Alternativas

- **Mantener Thymeleaf y añadir islas de React**: reduce el cambio, pero deja dos
  formas de renderizar y no elimina la duplicación de fragmentos.
- **SPA con servidor estático separado**: obliga a un segundo servicio y a
  configurar CORS/cookies entre orígenes; complica la demo de un solo JAR.
- **JWT en `localStorage` para la SPA**: expone el token a XSS y rompe la
  separación entre navegador y API. Se descartó.
- **Next.js/SSR**: añade un servidor Node en producción sin un requisito de SEO
  que lo justifique.

## Consecuencias

- La demo sigue arrancando con Spring Boot y **sin Node** porque el build está
  versionado en el JAR.
- El frontend tiene pruebas propias con **Vitest + Testing Library** (16 tests);
  el backend conserva las pruebas de integración del contrato JSON y de la
  seguridad (66 tests de panel, 145 en total).
- Hay que ejecutar `npm run build` después de modificar React; CI lo hace antes
  de `mvn test`.
- La SPA requiere JavaScript. El mapa y el gráfico siguen siendo SVG propios,
  así que la demo funciona sin internet; Google Maps es opcional.
- El contrato de `openapi.yaml` no cambia: `/api/v1` sigue siendo la API del
  ESP32 y del futuro Android. El panel no la reutiliza.
