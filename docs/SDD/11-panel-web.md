# 11 · Panel web

## 11.1 Rutas

| Ruta | Método | Protección | Qué hace |
|---|---|---|---|
| `/` | GET | Público | Redirige a `/panel` |
| `/login` | GET/POST | Público | Formulario de acceso (POST con CSRF) |
| `/logout` | POST | Sesión | Cierra sesión e invalida `JSESSIONID` |
| `/panel` | GET | Sesión | Tablero |
| `/panel/datos` | GET | Sesión | JSON del tablero para el refresco de 20 s |
| `/panel/historial` | GET | Sesión | Historial filtrado y paginado |
| `/panel/alertas` | GET | Sesión | Gestión de alertas |
| `/panel/alertas/{id}/atender` | POST | Sesión + CSRF | Registra la atención |
| `/panel/alertas/{id}/reabrir` | POST | Sesión + CSRF | Borra la auditoría |
| `/panel/predicciones` | POST | Sesión + CSRF | Genera una predicción |
| `/css/**`, `/js/**` | GET | Público | Recursos estáticos (sin datos) |

## 11.2 Autenticación y CSRF

El panel usa **sesión con formulario** (`JSESSIONID`) y **CSRF activo**. Es la
única forma razonable de proteger formularios en un navegador: con el JWT en
`localStorage` el token quedaría expuesto a XSS, y sin CSRF el formulario de
acceso sería falsificable.

Consecuencia deliberada: **un JWT válido no abre el panel**. Hay un test que lo
fija (`PanelIntegrationTest.elPanelNoAceptaJwt`). El orden de las cadenas de
filtros importa: la del panel es `@Order(1)`; si la de la API fuera primero, su
`anyRequest().authenticated()` respondería el panel con un 401 en JSON.

Un solo usuario configurable (`APP_USER` / `APP_PASSWORD`, con BCrypt en
memoria). No hay registro ni recuperación de contraseña: es un panel de
operación, no un portal de usuarios.

## 11.3 Tablero

| Elemento | Origen |
|---|---|
| Indicadores (muestras, alertas, estaciones, hora) | Ventana de **24 h** |
| Tarjetas N1–N4 | **Última muestra de cada estación**, sin límite de antigüedad |
| Badge de estado | Campo `alert` persistido; no se reevalúa |
| Gráfico | Puntos de las últimas 24 h, una línea por estación |
| Predicciones | Últimas filas de `prediction`, aparte de las mediciones |

Decisiones que conviene no deshacer:

- **El panel lee del repositorio, no de la API REST.** El navegador usa sesión;
  meter un JWT en `localStorage` sería un riesgo XSS y duplicaría el cliente.
- **El estado no se recalcula.** Solo se lee `sample.isAlert()` y los motivos ya
  redactados. Si el panel reevaluara el pH habría dos reglas ECA-Agua que
  podrían divergir.
- **Solo tres estados**: `SIN_DATOS` (gris), `DENTRO` (verde), `ALERTA` (rojo).
  No existe «cerca del límite»: sería una cuarta regla que el backend no conoce.
- **La tarjeta no aplica la ventana de 24 h.** Si una estación lleva tres días
  callada se muestra su último dato con «hace 3 días», que es más honesto que
  fingir que nunca midió o que el dato es actual.
- **La antigüedad se escribe en palabras** porque un `HH:mm` suelto invita a
  creer que el dato es actual.
- **Formato numérico con `Locale.ROOT`**, no con `#numbers` de Thymeleaf: el
  separador decimal no puede depender del locale de la máquina.
- **Refresco cada 20 s** contra `/panel/datos`, en pausa si la pestaña está
  oculta. El botón «Actualizar» hace lo mismo y confirma con un toast.
- **Sin JavaScript** las tarjetas, los indicadores y las reglas siguen completos;
  solo el gráfico muestra su estado vacío y el `<noscript>` lo explica.

## 11.4 Predicciones en el panel

La sección «Predicciones del ST-GNN» es independiente de las mediciones:

- Formulario `POST /panel/predicciones` con horizonte 1, 3 o 6 h y **CSRF**.
- Cada tarjeta muestra estación, horizonte, valor previsto, antigüedad del
  objetivo y estado (`Pendiente` / `Evaluada`).
- Si está evaluada, muestra el valor observado y el error con signo.
- Siempre declara el origen: «Datos sintéticos» o «Datos de campo».
- Si la IA falla, el panel vuelve con un aviso claro; **no** se crea una
  predicción de relleno.
- La predicción **no** cambia el badge ECA-Agua de la estación.

## 11.5 Historial

`/panel/historial` lista 20 muestras por página, de la más reciente a la más
antigua.

| Filtro | Comportamiento |
|---|---|
| Estación | `N1`…`N4` o todas |
| Desde / Hasta | Días de calendario en **hora de Lima** (00:00 y 23:59:59.999) |
| Solo alertas | Usa el campo `alert` persistido |
| Buscar en el motivo | Texto dentro del mensaje que redactó el backend |

- **No hay una segunda consulta**: el panel llama a `TelemetryService.search`,
  que ya aplica `WaterQualitySampleSpecifications`.
- **Los filtros llegan como texto, no como `LocalDate`.** Una fecha mal tecleada
  avisa y conserva el formulario en vez de responder 400.
- **Un rango invertido no se aplica y se avisa**, en vez de devolver una tabla
  vacía que parecería «no hay datos».
- **Las fechas son de Lima, no UTC.** Hay un test que fija el caso de las 23:30
  de Lima (`2026-10-18T04:30Z`).
- **Una página fuera de rango se ajusta a la última.**
- **Formulario GET, sin JavaScript**: la búsqueda queda en la URL.
- La tabla se desplaza en horizontal dentro de su tarjeta en móvil.

## 11.6 Alertas

`/panel/alertas` lista solo muestras **con alerta**; por defecto, pendientes.

- **Atender no cambia el veredicto.** `alert` y sus motivos siguen siendo lo que
  decidió `EcaWaterQualityRule`. La atención se guarda aparte
  (`acknowledged_at`, `acknowledged_by`, `acknowledge_note`).
- **Solo las muestras con alerta se pueden atender.** Una muestra dentro de
  rango o un id inexistente responde 404.
- **Es un POST con CSRF**, no un enlace: cambia estado persistido y registra al
  usuario. El nombre sale de la sesión, nunca del formulario.
- **Doble envío no pisa la auditoría.**
- **Los filtros se conservan al atender y al reabrir** reenviándolos como
  atributos de redirección. No se acepta una URL de retorno del cliente: sería
  un redirect abierto.
- **Tres contadores con su ventana escrita en la etiqueta**: pendientes (sin
  caducidad), atendidas (24 h por hora de atención), alertas (24 h por hora de
  medición).
- La API REST **no** expone la atención; el panel es hoy el único lugar donde se
  atiende una alerta.

## 11.7 Mapa

- Contorno **real** de la laguna desde OpenStreetMap (`way 26947746`, 194 puntos,
  simplificado a 140 con Douglas-Peucker) guardado en
  `backend/src/main/resources/geo/laguna-paca.json`. El panel no descarga teselas.
- Proyección equirectangular corregida por `cos(latitud)`; sin esa corrección, a
  11° sur el lago se vería estirado horizontalmente.
- La caja incluye las estaciones, no solo el lago.
- El color es el veredicto persistido: verde `DENTRO`, rojo `ALERTA`, gris
  `SIN_DATOS`.
- Cada punto enlaza a `/panel/historial?estacion=N2`.
- El texto accesible de cada punto se construye en el servidor y viaja también en
  el JSON.
- **Sin clave de Google no se pide nada a Google**: el SVG es el camino por
  defecto y funciona sin internet.
- Con `GOOGLE_MAPS_API_KEY`, Google Maps es una mejora opcional; si la API no
  carga, el contorno propio se queda. La clave **nunca** viaja en
  `/panel/datos` (`@JsonIgnore`).

## 11.8 Sistema de diseño

- `tokens.css` es la fuente única de color y escala; `panel.css` no escribe
  ningún color a mano.
- Tokens principales: `--color-primary` (#0F5C8C), `--color-secondary`
  (#178C7F), `--color-success`, `--color-warning`, `--color-danger`,
  `--color-info`; `--color-primary-text` existe porque el primario profundo
  sobre fondo oscuro daba 2.79:1 en el enlace activo (AA exige 4.5:1).
- Modo claro por defecto y oscuro persistido en `localStorage` (`laguna-tema`),
  aplicado en el `<head>` antes del CSS para evitar el destello blanco.
- Iconos Lucide incrustados como SVG con `aria-hidden="true"`, siempre
  acompañados de texto.
- Áreas táctiles de 44 px, foco visible, «Saltar al contenido»,
  `aria-current`, `aria-expanded`, `aria-pressed`, `aria-live` en los toasts.
- Barra lateral fija de 264 px en ≥1024 px, colapsable a 76 px; en móvil es un
  cajón que se cierra con `Escape` devolviendo el foco.
- `prefers-reduced-motion` desactiva las animaciones.
- Contrastes medidos entre 3.84:1 y 17.06:1 según el elemento y el tema; los
  elementos gráficos del mapa cumplen el mínimo de 3:1.

## 11.9 Pruebas

| Test | Qué fija |
|---|---|
| `PanelIntegrationTest` (9) | Redirecciones, login válido/inválido, CSRF, JWT no abre el panel, API conserva 401 |
| `PanelDashboardIntegrationTest` (6) | Cuatro estaciones sin datos, última muestra, alerta con motivo, JSON con sesión, ventana de 24 h |
| `PanelHistorialIntegrationTest` (12) | Sesión, orden, paginación, ajuste de página, filtros, día de Lima, avisos, enlaces |
| `PanelAlertasIntegrationTest` (12) | Sesión, solo alertas, pendientes, filtros, contadores, CSRF, auditoría, 404, reabrir |
| `PanelMapaIntegrationTest` (7) | Contorno, cuatro marcas, enlaces, proyección, colores, sin teselas |
| `PanelMapaGoogleIntegrationTest` (3) | Contenedor de Google, SVG de respaldo, clave fuera del JSON |
| `PanelPrediccionesIntegrationTest` (8) | Predicción visible, origen sintético, evaluación, estado vacío, sesión, CSRF, IA caída, horizonte inválido |
