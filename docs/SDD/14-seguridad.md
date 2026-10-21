# 14 · Seguridad

## 14.1 Modelo de amenazas (resumen)

| Activo | Amenaza | Mitigación |
|---|---|---|
| Credenciales del panel | Fuerza bruta, reutilización | BCrypt, un solo usuario, red controlada |
| JWT de la API | Robo y uso desde otro cliente | TTL de 2 h, HTTPS pendiente, no se guarda en el navegador |
| Formularios del panel | CSRF | Token CSRF de Spring Security |
| Sesión del panel | XSS | React escapa por defecto; no se usa `dangerouslySetInnerHTML` |
| Datos de telemetría | Inyección SQL | JPA/Criteria API con parámetros |
| Secretos | Filtración en git | `.env` y `secrets.h` ignorados; variables de entorno |
| Servicio de IA | Acceso desde fuera de la red | Sin puerto publicado en Compose + `X-Internal-Token` |
| Base de datos | Acceso remoto | Solo red interna; contraseña por entorno |
| Mapa de Google | Uso de la clave desde otro sitio | Restricción por referente (obligatoria) |

## 14.2 Dos superficies, dos mecanismos

`SecurityConfig` define dos cadenas:

1. **`@Order(1)` — panel** (`/`, `/panel/**`, `/login`, `/logout`):
   sesión con formulario (`JSESSIONID`) y **CSRF activo**. Logout por POST.
2. **`@Order(2)` — API** (`/api/**`): **stateless con JWT**, sin cookies y sin
   CSRF, porque su cliente es el ESP32 y no un navegador.

Consecuencia deliberada: **un JWT válido no abre el panel**. Con el JWT en
`localStorage` el token quedaría expuesto a XSS, y sin CSRF el formulario de
acceso sería falsificable.

La SPA obtiene el token en `GET /panel/csrf` (`{token, headerName,
parameterName}`) y lo envía en la cabecera `X-CSRF-TOKEN`. Si recibe 403 por un
token rotado, lo descarta, pide uno nuevo y reintenta **una sola vez**. Al
iniciar y cerrar sesión también descarta el token cacheado, porque Spring
Security puede cambiar el id de sesión. El nombre del usuario que atiende una
alerta sale de `Principal`, nunca del cuerpo de la petición.

Públicos: `/login`, `/panel/csrf`, `/panel/assets/**`,
`/panel/favicon.svg`, `/api/v1/auth/token`, Swagger
(`/swagger-ui*`, `/v3/api-docs/**`) y `actuator/health|info`. Todo lo demás exige
autenticación. `/panel/api/**` responde **401 JSON** sin sesión mediante
`PanelApiAuthenticationEntryPoint` (`{"error": "La sesión caducó..."}`); las
rutas de página redirigen a `/login`.

## 14.3 JWT

- HS256 con `JWT_SECRET` (mínimo 32 bytes; en producción, aleatorio).
- `issuer = laguna-paca`, `ttl = PT2H` (2 horas).
- El filtro se registra como `@Bean`, no como `@Component`: así
  `@WebMvcTest` con `addFilters = false` no lo instancia.
- Token inválido o ausente en la API → **401** con `RestAuthenticationEntryPoint`
  (no 403). Hay un test que lo fija.

El firmware cachea el token en RTC y lleva el vencimiento de forma monótona
(sumando el tiempo dormido) porque no tiene NTP. Si el token vence, se
reautentica con las credenciales del nodo.

## 14.4 Contraseñas

- El usuario del panel se configura con `APP_USER` / `APP_PASSWORD`.
- La contraseña se codifica con **BCrypt** en memoria al arrancar; no se guarda
  en claro en la base (no hay tabla de usuarios).
- `admin/admin123` es el valor de desarrollo. **No debe usarse en producción.**

## 14.5 Secretos

| Secreto | Dónde vive | Versionado |
|---|---|---|
| `JWT_SECRET` | `.env` / entorno | No |
| `APP_PASSWORD` | `.env` / entorno | No |
| `DB_PASSWORD` | `.env` / entorno | No |
| `IA_INTERNAL_TOKEN` | `.env` / entorno | No |
| WiFi y backend del nodo | `include/secrets.h` | No |
| `GOOGLE_MAPS_API_KEY` | Entorno | No |

`application.yml` solo tiene valores de desarrollo para que la demo arranque.
En producción, `compose.yaml` exige `JWT_SECRET` y `APP_PASSWORD` con
`${VAR:?mensaje}`: sin ellos, Compose no levanta el stack.

## 14.6 Clave de Google Maps: incidente y acción

Durante el desarrollo se compartió una clave de Maps en el chat. Aunque no está
en el repositorio, **debe rotarse** y la nueva debe restringirse por referente:

- `http://127.0.0.1:8080/*`
- `http://localhost:8080/*`
- el dominio real en producción

y habilitar solo *Maps JavaScript API*. Una clave sin restricción en un
repositorio público se puede usar a costa de la cuenta de facturación.

La clave no viaja en `/panel/api/datos` ni en `/panel/datos`; solo se entrega
en `GET /panel/api/sesion`. Hay tests que comprueban que no aparece en el HTML
ni en el JSON del tablero.

Verificación del 20/10/2026: la clave **no** está en el árbol de trabajo, en
`HEAD` ni en el historial (`git log -S "AIza"` no devuelve nada). Rotarla exige
acceso a la consola de Google Cloud del proyecto; no se puede hacer desde el
repositorio.

## 14.7 Lo que falta (deuda de seguridad)

| Falta | Riesgo | Prioridad |
|---|---|---|
| HTTPS/TLS en backend y broker | Token y contraseñas en claro en la red | Alta para producción |
| Rotación de la clave de Google | Uso no autorizado | Inmediata |
| Rate limiting en `/auth/token` | Fuerza bruta | Media |
| Más de un usuario y roles | Sin separación de funciones | Media |
| Escaneo de secretos en CI (gitleaks) | Filtración futura | Media |
| CORS explícito | Hoy no hay clientes de otro origen | Baja |
| Content-Security-Policy | Mitigación adicional de XSS | Baja |
| Firma/verificación de la imagen Docker | Cadena de suministro | Baja |

## 14.8 Decisiones que no se deben deshacer

- **No guardar el JWT en `localStorage`** para el panel.
- **No desactivar CSRF** en la cadena del panel.
- **No exponer la atención de alertas en la API** sin decidir antes el contrato
  de la app Android.
- **No publicar el puerto de la IA** en Compose.
- **No versionar** `.env`, `secrets.h` ni claves.
