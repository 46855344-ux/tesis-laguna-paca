# 30 · FCM (Firebase Cloud Messaging) — guía de implementación

> **Estado:** no implementado. Requiere un proyecto Firebase real y credenciales.
> Esta guía deja listos los pasos y los puntos de integración con el código
> existente. **No se simulan notificaciones**: sin credenciales, el código debe
> quedar desactivado por defecto.

## 0. Prerrequisitos

1. Cuenta de Google y proyecto en https://console.firebase.google.com.
2. App Android registrada en Firebase con el package
   `pe.edu.continental.lagunapaca.mobile` y el SHA-1 del certificado de debug
   (y de release, si aplica):
   ```powershell
   keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
   ```
3. Descargar `google-services.json` y colocarlo en `android-app/app/`.
4. Generar una clave de cuenta de servicio (Project settings → Service accounts
   → Generate new private key) y guardarla **fuera del repo**, por ejemplo en
   `C:\secrets\laguna-fcm.json`. Nunca la commitees.

## 1. Backend (Spring Boot)

### 1.1 Dependencia

En `backend/pom.xml`:
```xml
<dependency>
    <groupId>com.google.firebase</groupId>
    <artifactId>firebase-admin</artifactId>
    <version>9.3.0</version>
</dependency>
```

### 1.2 Configuración (`FirebaseConfig`)

Paquete nuevo `pe.edu.continental.lagunapaca.notification`:

```java
@Configuration
public class FirebaseConfig {
    @Bean
    public FirebaseApp firebaseApp(@Value("${app.fcm.credentials-path:}") String path) {
        if (path == null || path.isBlank()) return null; // FCM desactivado
        try (FileInputStream in = new FileInputStream(path)) {
            return FirebaseApp.initializeApp(
                FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(in)).build());
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
}
```

Variables de entorno: `FCM_ENABLED=true`, `FIREBASE_CREDENTIALS_PATH=C:\secrets\laguna-fcm.json`.

### 1.3 Tabla `device_token` (migración Flyway V6)

```sql
CREATE TABLE device_token (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    token VARCHAR(512) NOT NULL UNIQUE,
    platform VARCHAR(16) NOT NULL,        -- 'android'
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 1.4 Endpoint de registro

`POST /api/v1/devices/register` (JWT), body `{ "token": "...", "platform": "android" }`.
Upsert por token; actualiza `last_seen_at`.

### 1.5 `FcmService`

```java
@Service
public class FcmService {
    private final FirebaseApp app; // null si desactivado
    public void enviarAlerta(String titulo, String cuerpo, Map<String,String> data) {
        if (app == null) return; // desactivado, no se simula
        // multicast a los tokens registrados
    }
}
```

### 1.6 Disparo

En `TelemetryService`, cuando `EcaWaterQualityRule` devuelve `alert = true`,
llamar a `fcmService.enviarAlerta("Alerta ECA-Agua", motivo, data)` con el
`node_id`, `ph`, `recorded_at`. **Nunca** romper la ingesta si FCM falla:
capturar la excepción y loguear.

### 1.7 Tests

- `FcmServiceTest` con un doble de `FirebaseMessaging` (o desactivado).
- `DeviceTokenControllerTest` con `@WebMvcTest` para el registro.
- No se prueba el envío real sin credenciales: el CI no tiene Firebase.

## 2. App Android (Kotlin + Compose)

### 2.1 Gradle

`android-app/build.gradle.kts` (project):
```kotlin
plugins {
    id("com.google.gms.google-services") version "4.4.2" apply false
}
```
`android-app/app/build.gradle.kts`:
```kotlin
plugins { id("com.google.gms.google-services") }
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging")
}
```

### 2.2 `google-services.json`

Colocar en `android-app/app/`. **No commitear** (añadir a `.gitignore` si no
está; hoy `*.json` no está ignorado, añadir `google-services.json`).

### 2.3 Servicio FCM

```kotlin
class LagunaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // registrar en el backend con el JWT en memoria si la sesión está activa;
        // si no, guardar el token en SharedPreferences y registrarlo al iniciar sesión
    }
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // mostrar notificación con NotificationCompat, canal "alertas-eca"
    }
}
```

Registrar en `AndroidManifest.xml`:
```xml
<service android:name=".LagunaMessagingService" android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

### 2.4 Canal de notificación

Crear el canal `alertas-eca` (importancia HIGH) al arrancar la app. Android 8+
exige canal para mostrar notificaciones.

### 2.5 Registro del token

- Al iniciar sesión correctamente, llamar `FirebaseMessaging.getInstance().token`
  y registrar en `POST /api/v1/devices/register`.
- `onNewToken` cubre la rotación: re-registrar.

### 2.6 Tests

- `LagunaMessagingService` no se testea en JVM (depende del SDK).
- Lógica de registro del token en `MainViewModel` sí se puede testear con un
  doble del cliente HTTP.

## 3. Seguridad

- La clave de servicio del backend **nunca** se versiona ni se loguea.
- La API key del cliente Android se restringe por package + SHA-1 en Google
  Cloud Console (solo *Firebase Cloud Messaging API*).
- El endpoint de registro exige JWT; un token FCM sin sesión no se acepta.
- Rotación: si un dispositivo se desinstala, el token queda inválido; el
  backend debe limpiar tokens que respondan `UNREGISTERED` al enviar.

## 4. Orden sugerido de implementación

1. Backend: dependencia + `FirebaseConfig` (desactivado por defecto) + tests.
2. Backend: migración `device_token` + endpoint de registro + tests.
3. Backend: `FcmService` + disparo en `TelemetryService` + tests con doble.
4. Android: plugin + dependencia + `google-services.json` (local, no commiteado).
5. Android: `LagunaMessagingService` + canal + registro del token.
6. Prueba end-to-end con un dispositivo/emulador con Google Play Services.

## 5. Lo que NO se hace

- No se simulan notificaciones sin credenciales.
- No se hardcodean tokens ni claves en el código.
- No se envía a todos los dispositivos sin filtrar por usuario/estación.
