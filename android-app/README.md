# App Android · Laguna Paca

MVP móvil en **Kotlin + Jetpack Compose + Material 3** para consultar el
monitoreo de la Laguna de Paca desde un teléfono o emulador Android.

## Qué incluye

- Inicio de sesión contra `POST /api/v1/auth/token` con JWT.
- Dirección del backend editable (por defecto `http://10.0.2.2:8080`, la PC
  anfitriona vista desde el emulador).
- Tablero con ventana de **24 h**: muestras, alertas, cumplimiento ECA-Agua,
  estaciones con datos y última lectura de `N1`…`N4`.
- Listado de alertas ECA-Agua con el motivo redactado por el backend.
- Tema Material 3 claro/oscuro y estados vacíos honestos.
- La app **no recalcula el pH**: muestra el campo `alert` persistido.

## Requisitos

- JDK 17 **con `jlink`**. En esta máquina:
  `C:\Users\USER\.jdks\jbr-17.0.14`.
  El JBR de IntelliJ 2025 usa Java 21, pero no incluye `jlink`, y AGP lo
  necesita para `core-for-system-modules.jar`.
- Android SDK con plataforma 36 y build-tools instalados.
- Backend en modo demo:

```powershell
cd backend
$env:JAVA_HOME="D:\Intellij\IntelliJ IDEA 2025.2.1\jbr"
& "C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.9\977a63e90f436cd6ade95b4c0e10c20c\bin\mvn.cmd" spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.useTestClasspath=true"
```

## Compilar y probar

```powershell
cd android-app
$env:JAVA_HOME="C:\Users\USER\.jdks\jbr-17.0.14"

.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

El APK queda en:

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

Para instalarlo en un emulador o dispositivo conectado:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n pe.edu.continental.lagunapaca.mobile/.MainActivity
```

## Conexión

| Entorno | URL |
|---|---|
| Emulador Android | `http://10.0.2.2:8080` |
| Dispositivo físico en la misma red | `http://<IP-de-la-PC>:8080` |
| Producción | `https://<dominio>` |

En **debug** se permite HTTP local porque la demo no tiene TLS. El manifiesto
principal de release exige HTTPS; no se debe publicar una app con tráfico
plano.

La app usa la **API pública con JWT**, no `/panel/api/**`: el panel usa sesión
y CSRF y no es el contrato del cliente móvil.

## Limitaciones de este MVP

- No incluye **Firebase Cloud Messaging (FCM)**: falta `google-services.json`
  y credenciales del proyecto Firebase. No se simulan notificaciones.
- No incluye mapa de Google Maps en Android; el tablero muestra las estaciones
  como tarjetas. Añadirlo requiere una clave restringida para Android.
- El JWT vive **solo en memoria**: al cerrar la app hay que iniciar sesión de
  nuevo. No se guarda la contraseña.
- No hay caché offline ni sincronización en segundo plano.
- Los tests actuales son unitarios (mapeo del tablero); no hay pruebas
  instrumentadas ni E2E de UI.
- Los datos que se ven en la demo son del simulador o de H2; no son mediciones
  de campo.

## Estructura

```text
app/src/main/java/pe/edu/continental/lagunapaca/mobile/
├── MainActivity.kt
├── data/
│   ├── Models.kt           # muestra, estación y resumen
│   ├── DashboardMapper.kt  # agrupación y ventana de 24 h
│   └── LagunaApi.kt        # HTTP + JSON con HttpURLConnection
└── ui/
    ├── MainViewModel.kt    # estado, login y carga
    ├── LagunaPacaApp.kt    # pantallas Compose
    └── theme/Theme.kt      # paleta Material 3
```
