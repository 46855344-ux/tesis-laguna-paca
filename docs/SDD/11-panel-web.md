# 11 · Panel web

## 11.1 Arquitectura

El panel es una **SPA React 19 + TypeScript + Vite** con React Router. Spring Boot
la sirve desde el mismo JAR: el build de Vite se escribe en
`backend/src/main/resources/static/panel` (con `base: '/panel/'`). En desarrollo,
Vite corre en `5173` (abrir `http://localhost:5173/panel`) y hace proxy a Spring
Boot en `8080`.

Ya **no hay Thymeleaf** ni plantillas renderizadas en el servidor. Las rutas del
navegador devuelven el `index.html` de la SPA y React decide qué pantalla pintar.
El build de producción se versiona para que la demo funcione sin Node.

| Ruta | Método | Protección | Qué hace |
|---|---|---|---|
| `/` | GET | Público | Redirige a `/panel` |
| `/login` | GET | Público | Shell React del formulario de acceso |
| `/login` | POST | Público + CSRF | Autentica y redirige a `/panel` |
| `/logout` | POST | Sesión + CSRF | Cierra sesión e invalida `JSESSIONID` |
| `/panel` | GET | Sesión | Shell React del tablero |
| `/panel/` | GET | Sesión | Alias con barra final del tablero |
| `/panel/metricas` | GET | Sesión | Shell React de métricas |
| `/panel/historial` | GET | Sesión | Shell React del historial |
| `/panel/alertas` | GET | Sesión | Shell React de alertas |
| `/panel/assets/**`, `/panel/favicon.svg` | GET | Público | Recursos del build de Vite |
| `/panel/csrf` | GET | Público | Token CSRF para la SPA |

`SpaController` devuelve `static/panel/index.html` como `Resource` para las rutas
de página. No se usa `forward:` porque MockMvc debe recibir el HTML y no hace
falta una segunda pasada por el servlet de recursos estáticos.

## 11.2 Autenticación y CSRF

El panel usa **sesión con formulario** (`JSESSIONID`) y **CSRF activo**. Es la
única forma razonable de proteger un navegador: con el JWT en `localStorage` el
token quedaría expuesto a XSS, y sin CSRF el formulario de acceso sería
falsificable.

Consecuencia deliberada: **un JWT válido no abre el panel**. Hay un test que lo
fija (`PanelIntegrationTest.elPanelNoAceptaJwt`). El orden de las cadenas de
filtros importa: la del panel es `@Order(1)`; si la de la API fuera primero, su
`anyRequest().authenticated()` respondería el panel con un 401 en JSON.

Detalles que no se deben romper:

- `/panel/api/**` responde **401 JSON** si la sesión caducó; las rutas de página
  redirigen a `/login`. Hacen falta **dos** `defaultAuthenticationEntryPointFor`:
  con una sola entrada, Spring Security la aplica a todas las peticiones e
  ignora su matcher.
- `/logout` pertenece a la cadena del panel. Si se deja fuera, la cadena
  stateless lo trata como una petición sin JWT y responde 401.
- `/login`, `/panel/csrf`, `/panel/assets/**` y `/panel/favicon.svg` son públicos.
  El resto del panel exige rol `ADMIN`.
- `GET /panel/csrf` entrega `{token, headerName, parameterName}`. El cliente
  React envía el token en la cabecera indicada (`X-CSRF-TOKEN`), incluso en GET,
  y reintenta **una vez** si recibe 403 por un token rotado.
- El login de la SPA hace POST a `/login` con `_csrf` y considera éxito cuando la
  URL final es `/panel`. Después descarta el token CSRF cacheado, porque Spring
  Security pudo cambiar el id de sesión.
- Un solo usuario configurable (`APP_USER` / `APP_PASSWORD`, con BCrypt en
  memoria). No hay registro ni recuperación de contraseña: es un panel de
  operación, no un portal de usuarios.

## 11.3 API JSON del panel

| Método | Ruta | Uso |
|---|---|---|
| `GET` | `/panel/api/sesion` | Usuario, rol, inicial, estaciones y clave opcional de Google |
| `GET` | `/panel/api/datos` | Tablero completo; refresco cada 20 s |
| `GET` | `/panel/datos` | Alias del tablero, conservado del contrato anterior |
| `GET` | `/panel/api/historial` | Filtros, orden y paginación |
| `GET` | `/panel/api/alertas` | Alertas, contadores y filtros |
| `POST` | `/panel/api/alertas/{id}/atender` | Sesión + CSRF; guarda usuario, hora y nota |
| `POST` | `/panel/api/alertas/{id}/reabrir` | Sesión + CSRF; borra la auditoría |
| `POST` | `/panel/api/predicciones` | Sesión + CSRF; cuerpo `{"horizonte": 1\|3\|6}` |

La API del panel (`PanelApiController`) lee de `PanelDashboardService`,
`PanelHistorialService`, `PanelAlertasService` y `PredictionService`; **no** llama
a `/api/v1` ni duplica la regla ECA-Agua. El contrato público de `openapi.yaml`
no cambia.

`PanelConfig` expone `usuario`, `rol`, `inicial`, `estaciones` y
`googleMapsApiKey` en `/panel/api/sesion`. La clave de Google **no** viaja en
`/panel/api/datos`, porque el tablero se refresca cada 20 segundos y no tiene por
qué repetirla.

## 11.4 Tablero

| Elemento | Origen |
|---|---|
| Indicadores (muestras, alertas, estaciones, hora) | Ventana de **24 h** |
| Tarjetas N1–N4 | **Última muestra de cada estación**, sin límite de antigüedad |
| Badge de estado | Campo `alert` persistido; no se reevalúa |
| Gráfico | Puntos de las últimas 24 h, una línea por estación |
| Predicciones | Últimas filas de `prediction`, aparte de las mediciones |

Decisiones que conviene no deshacer:

- **El panel React consume `/panel/api/**`, no la API REST pública.** El
  navegador usa sesión; meter un JWT en `localStorage` sería un riesgo XSS y
  duplicaría el cliente.
- **El estado no se recalcula en React.** El backend envía `estado`, `etiqueta`,
  `motivos` y `clase`; el frontend solo pinta. Si React reevaluara el pH habría
  dos reglas ECA-Agua que podrían divergir.
- **Solo tres estados**: `SIN_DATOS` (gris), `DENTRO` (verde), `ALERTA` (rojo).
  No existe «cerca del límite»: sería una cuarta regla que el backend no conoce.
- **La tarjeta no aplica la ventana de 24 h.** Si una estación lleva tres días
  callada se muestra su último dato con «hace 3 días», que es más honesto que
  fingir que nunca midió o que el dato es actual.
- **La antigüedad se escribe en palabras** porque un `HH:mm` suelto invita a
  creer que el dato es actual. El backend la formatea con `Locale.ROOT` y el
  JSON reutiliza esos textos.
- **Refresco cada 20 s** contra `/panel/api/datos`, en pausa si la pestaña está
  oculta. El botón «Actualizar» hace lo mismo y confirma con un toast. En React
  se usa un `ref` para evitar que el intervalo se recree en cada render.
- **El gráfico es SVG hecho a mano** en React, sin Chart.js ni CDN: la demo
  funciona sin internet. Si la ventana dura menos de 30 min, el eje se abre
  hasta 30 min para que las horas no se aplasten.

## 11.5 Predicciones en el panel

La sección «Predicciones del ST-GNN» es independiente de las mediciones:

- Formulario `POST /panel/api/predicciones` con horizonte 1, 3 o 6 h y **CSRF**.
- Cada tarjeta muestra estación, horizonte, valor previsto, antigüedad del
  objetivo y estado (`Pendiente` / `Evaluada`).
- Si está evaluada, muestra el valor observado y el error con signo.
- Siempre declara el origen: «Datos sintéticos» o «Datos de campo».
- Si la IA falla, el panel muestra el motivo (503 si está caída, 502 si el
  contrato es inválido, 409 si la ventana está incompleta); **no** se crea una
  predicción de relleno.
- La predicción **no** cambia el badge ECA-Agua de la estación.

## 11.6 Historial

`/panel/historial` lista 20 muestras por página, de la más reciente a la más
antigua. Los filtros viven en la URL (`useSearchParams`), así que se pueden
guardar, compartir y el botón «atrás» funciona; la paginación conserva todos los
filtros.

| Filtro | Comportamiento |
|---|---|
| Estación | `N1`…`N4` o todas |
| Desde / Hasta | Días de calendario en **hora de Lima** (00:00 y 23:59:59.999) |
| Solo alertas | Usa el campo `alert` persistido |
| Buscar en el motivo | Texto dentro del mensaje que redactó el backend |

- **No hay una segunda consulta**: el panel llama a `TelemetryService.search`,
  que ya aplica `WaterQualitySampleSpecifications`.
- **Los filtros llegan como texto, no como `LocalDate`.** Una fecha mal tecleada
  avisa y conserva el resto del historial en vez de responder 400.
- **Un rango invertido no se aplica y se avisa**, en vez de devolver una tabla
  vacía que parecería «no hay datos».
- **Las fechas son de Lima, no UTC.** Hay un test que fija el caso de las 23:30
  de Lima (`2026-10-18T04:30Z`).
- **Una página fuera de rango se ajusta a la última.**
- La tabla se desplaza en horizontal dentro de su tarjeta en móvil.

## 11.7 Métricas

`/panel/metricas` es un cuadro de mando de solo lectura sobre la ventana de 24 h.
**No hay un endpoint nuevo**: se compone con `GET /panel/api/datos` y
`GET /panel/api/alertas`.

| Bloque | Origen |
|---|---|
| Muestras, alertas y estaciones con datos | Contadores del tablero |
| Cumplimiento ECA-Agua | `(muestras - alertas) / muestras`, con el veredicto persistido |
| Tendencia de pH | Serie de 24 h, una línea por estación |
| Dona de estados | `DENTRO`, `ALERTA` y `SIN_DATOS` de las cuatro estaciones |
| Alertas recientes | Primeras cinco filas de `/panel/api/alertas` |
| Muestras por estación | Conteo de puntos de la serie de 24 h |

- **React no reevalúa el pH**: el cumplimiento y los estados salen del veredicto que
  guardó la ingesta. Sin muestras, el cumplimiento se muestra como `—`.
- **Si falla la lista de alertas, la pantalla no se cae**: se conservan los KPIs y
  se avisa de que la tabla no está disponible; no se pinta una lista vacía falsa.
- **El periodo es fijo de 24 h**, porque es la ventana que ya expone el tablero.
  Siete o treinta días exigirían un endpoint de agregación propio.
- Se refresca cada 20 s con el patrón del tablero y se pausa si la pestaña está
  oculta.

## 11.8 Alertas

`/panel/alertas` lista solo muestras **con alerta**; por defecto, pendientes.

- **Atender no cambia el veredicto.** `alert` y sus motivos siguen siendo lo que
  decidió `EcaWaterQualityRule`. La atención se guarda aparte
  (`acknowledged_at`, `acknowledged_by`, `acknowledge_note`).
- **Solo las muestras con alerta se pueden atender.** Una muestra dentro de
  rango o un id inexistente responde 404.
- **Es un POST con CSRF**, no un enlace: cambia estado persistido y registra al
  usuario. El nombre sale de la sesión (`Principal`), nunca del formulario.
- **Doble envío no pisa la auditoría.**
- **Los filtros se conservan al atender y al reabrir** en la URL de React; no se
  acepta una URL de retorno del cliente (sería un redirect abierto).
- **Tres contadores con su ventana escrita en la etiqueta**: pendientes (sin
  caducidad), atendidas (24 h por hora de atención), alertas (24 h por hora de
  medición).
- La API REST pública **no** expone la atención; el panel es hoy el único lugar
  donde se atiende una alerta.

## 11.9 Mapa

- Contorno **real** de la laguna desde OpenStreetMap (`way 26947746`, 194 puntos,
  simplificado a 140 con Douglas-Peucker) guardado en
  `backend/src/main/resources/geo/laguna-paca.json`. El panel no descarga teselas.
- Proyección equirectangular corregida por `cos(latitud)`; sin esa corrección, a
  11° sur el lago se vería estirado horizontalmente.
- La caja incluye las estaciones, no solo el lago.
- El color es el veredicto persistido: verde `DENTRO`, rojo `ALERTA`, gris
  `SIN_DATOS`.
- Cada punto enlaza a `/panel/historial?estacion=N2`.
- El texto accesible de cada punto se construye en el backend y viaja también en
  el JSON; React lo usa como etiqueta del SVG.
- **Sin clave de Google no se pide nada a Google**: el SVG es el camino por
  defecto y funciona sin internet.
- Con `GOOGLE_MAPS_API_KEY`, Google Maps es una mejora opcional; si la API no
  carga, el contorno propio se queda. La clave **nunca** viaja en
  `/panel/api/datos`; solo en `/panel/api/sesion`.
- La clave es de navegador: debe restringirse por referente en Google Cloud y
  habilitar solo *Maps JavaScript API*.

## 11.10 Sistema de diseño y accesibilidad

- `frontend/src/styles/tokens.css` es la fuente única de color y escala; el CSS
  de los componentes no escribe colores a mano.
- Tokens principales: `--color-primary` (#0F5C8C), `--color-secondary`
  (#178C7F), `--color-success`, `--color-warning`, `--color-danger`,
  `--color-info`; `--color-primary-text` existe porque el primario profundo
  sobre fondo oscuro daba 2.79:1 en el enlace activo (AA exige 4.5:1).
- El encabezado de tabla usa `--color-text-muted-strong` (#526174) porque el gris
  normal sobre la superficie muted se quedaba en 4.34:1, por debajo de AA.
- Modo claro por defecto y oscuro persistido en `localStorage` (`laguna-tema`).
  Un script mínimo en `index.html` aplica el tema antes del CSS para evitar el
  destello blanco; React solo lo alterna y lo persiste.
- Iconos Lucide incrustados como SVG con `aria-hidden="true"`, siempre
  acompañados de texto.
- Áreas táctiles de 44 px, foco visible (`:focus-visible`), «Saltar al
  contenido», `aria-current="page"`, `aria-expanded`, `aria-pressed` y
  `aria-live` en los toasts.
- Barra lateral fija de 264 px en ≥1024 px, colapsable a 76 px; en móvil es un
  cajón con velo que se cierra con `Escape` devolviendo el foco al botón que lo
  abrió.
- `prefers-reduced-motion` desactiva las animaciones.
- Contrastes medidos entre 3.84:1 y 17.06:1 según el elemento y el tema; los
  elementos gráficos del mapa cumplen el mínimo de 3:1.
- Los estados vacíos son honestos: lo que todavía no existe se muestra como
  «Pronto» deshabilitado, no como un enlace que lleva a un 404.

## 11.11 Desarrollo y build

```powershell
cd frontend
npm install
npm run dev      # Vite en http://localhost:5173/panel con proxy a 8080
npm test         # Vitest + Testing Library
npm run build    # escribe en backend/src/main/resources/static/panel
```

El build de producción se versiona para que `mvn spring-boot:run` funcione sin
Node. Si se modifica React hay que ejecutar `npm run build` antes de arrancar o
empaquetar el backend.

## 11.11 Pruebas

| Test | Tests | Qué fija |
|---|---|---|
| `PanelIntegrationTest` | 13 | Raíz, shell React, assets, login válido/inválido, CSRF, logout, JWT no abre el panel, 401 JSON y `/panel/csrf` público |
| `PanelDashboardIntegrationTest` | 7 | Cuatro estaciones sin datos, última muestra, alerta con motivo, JSON con sesión, ventana de 24 h |
| `PanelHistorialIntegrationTest` | 12 | Sesión, orden, paginación, ajuste de página, filtros, día de Lima, avisos |
| `PanelAlertasIntegrationTest` | 12 | Sesión, solo alertas, pendientes, filtros, contadores, CSRF, auditoría, 404, reabrir |
| `PanelMapaIntegrationTest` | 6 | Contorno, cuatro marcas, proyección, colores, sin teselas |
| `PanelMapaGoogleIntegrationTest` | 3 | Clave solo en `/panel/api/sesion`, SVG de respaldo, ausencia en el HTML y en `/panel/api/datos` |
| `PanelMapaTest` | 2 | Serialización sin clave |
| `PanelPrediccionesIntegrationTest` | 11 | Predicción, origen, evaluación, estado vacío, sesión, CSRF, IA caída, ventana incompleta, contrato inválido y horizonte inválido |

El frontend usa **Vitest + Testing Library**:

| Archivo | Tests | Qué fija |
|---|---|---|
| `estado.test.ts` | 2 | Etiquetas y clases de los tres estados ECA-Agua |
| `client.test.ts` | 5 | CSRF, 401, reintento por 403, login y detección de éxito por URL |
| `components.test.tsx` | 6 | Badge, mapa, paginación y gráfico |
| `pages.test.tsx` | 3 | Historial, alertas y tablero con datos simulados |

No hay pruebas E2E con navegador real: el refresco de 20 s, el cajón móvil y la
carga opcional de Google Maps se validan a mano. Las pruebas de componentes usan
jsdom y dobles de `fetch`, no datos de campo.
