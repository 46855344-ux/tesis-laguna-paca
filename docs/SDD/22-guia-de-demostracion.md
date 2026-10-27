# 22 · Guía de demostración

Guion para la defensa. Todo lo marcado como **verificado** se ejecutó en esta
máquina el 21/10/2026; lo que no, se dice.

## 22.1 Antes de empezar (5 min)

1. Verificar que los puertos 8080 y 8000 están libres.
2. Tener abiertas dos terminales: backend y IA.
3. No usar `docs/` como material en vivo: pesa 257 MB y no es código.
4. Si se usará Google Maps, definir `GOOGLE_MAPS_API_KEY`; si no, el mapa SVG
   funciona igual.

## 22.2 Paso 1 · Pruebas (3 min)

```powershell
# Backend: 145 tests
cd backend
$env:JAVA_HOME="D:\Intellij\IntelliJ IDEA 2025.2.1\jbr"
& "C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.9\977a63e90f436cd6ade95b4c0e10c20c\bin\mvn.cmd" test
```

```powershell
# Frontend: 17 tests
cd frontend
npm test
```

```powershell
# Firmware: 38 tests
$env:PATH="C:\Users\USER\AppData\Local\Programs\mingw64\bin;$env:PATH"
cd iot-firmware
& "C:\Users\USER\AppData\Local\Programs\Python\Python312\Scripts\pio.exe" test -e native
```

```powershell
# IA: 172 tests (~3.5 min; si hay poco tiempo, omitir y mostrar el artefacto)
cd ia-model
.venv\Scripts\python.exe -m pytest -q
```

**Qué decir:** «386 pruebas automatizadas; el backend tiene 91.7 % de cobertura
de instrucciones».

## 22.3 Paso 2 · El modelo (2 min)

Mostrar `ia-model/artifacts/README.md` y el artefacto:

```powershell
Get-ChildItem ia-model\artifacts
```

**Qué decir:** «El ST-GNN se entrenó con una serie sintética; a 6 h obtiene
R² = 0.9289 frente a 0.7403 de la persistencia. Eso valida la arquitectura y la
integración, no la laguna real. La validación de campo está pendiente».

**No decir:** «el modelo predice la laguna con 93 % de precisión». Sería falso.

## 22.4 Paso 3 · Backend en modo demo (2 min)

```powershell
cd backend
$env:JAVA_HOME="D:\Intellij\IntelliJ IDEA 2025.2.1\jbr"
& "C:\Users\USER\.m2\wrapper\dists\apache-maven-3.9.9\977a63e90f436cd6ade95b4c0e10c20c\bin\mvn.cmd" spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.useTestClasspath=true"
```

Abrir `http://localhost:8080/panel` → redirige a `/login` → `admin` / `admin123`.
La SPA y sus assets ya vienen en el JAR; no hace falta `npm run dev` para la demo.

**Qué mostrar:** tablero con cuatro estaciones sin datos, mapa con el contorno
real, modo oscuro, navegación por teclado.

## 22.5 Paso 4 · Telemetría y alerta (3 min)

En otra terminal:

```powershell
cd backend
python tools\simulate_node.py --scenario normal --count 3
python tools\simulate_node.py --scenario alert --count 3
```

- Refrescar el tablero (o esperar 20 s).
- Abrir «Métricas»: los KPIs, la tendencia de pH, la dona de estados y las alertas
  recientes se construyen con la misma telemetría.
- La tarjeta de la estación que recibió pH fuera de rango se pinta en rojo con el
  motivo redactado por el backend.
- Abrir «Historial» y filtrar por esa estación y «solo alertas».
- Abrir «Alertas», atender una con una nota y volver al tablero: aparece
  «Atendida por …» sin cambiar el veredicto.

**Qué decir:** «La regla vive en un solo lugar; el panel no reevalúa el pH. 6.5 y
9.0 no son alerta, y hay pruebas para ese borde».

## 22.6 Paso 5 · Predicción (5 min)

Terminal de IA:

```powershell
cd ia-model
.venv\Scripts\python.exe -m uvicorn serve:app --host 127.0.0.1 --port 8000
```

- `http://localhost:8000/health` debe decir `model_loaded: true`.
- En el panel, sección «Predicciones del ST-GNN»: elegir 6 h y generar.
- Debe aparecer una tarjeta por estación con «Datos sintéticos» y «Pendiente».
- Si se vuelve a simular una muestra cercana al objetivo, la predicción pasa a
  «Evaluada» con el valor observado y el error.

**Plan B (si la IA no arranca):** detener el servicio y pulsar «Generar». El
panel muestra «No se pudo generar la predicción»; la ingesta y las alertas siguen
funcionando. Eso demuestra la degradación controlada, que es un requisito.

## 22.7 Paso 6 · API y Swagger (2 min)

`http://localhost:8080/swagger-ui.html` → Authorize con `admin` / `admin123`.

- `GET /api/v1/telemetry` devuelve las muestras.
- `POST /api/v1/predictions` con `{"horizon_hours": 6}`.
- `GET /api/v1/ai/health` muestra `available`.

**Qué decir:** «La API es stateless con JWT; el panel es sesión con CSRF. Un JWT
no abre el panel, y hay un test que lo fija».

## 22.8 Paso 7 · Código y documentación (2 min)

- `backend/src/main/java/.../prediction/` — paquete de predicciones.
- `backend/src/main/resources/db/migration/V5...sql` — idempotencia y tabla.
- `ia-model/lagunapaca_gnn/model.py` — TCN + GCN + atención.
- `frontend/src/` — SPA React y cliente CSRF; el build está en
  `backend/src/main/resources/static/panel`.
- `android-app/` — MVP Kotlin + Compose: login JWT, tablero, alertas, mapa y predicciones (14 tests).
- `docs/SDD/` — esta documentación.
- `compose.yaml`, `.github/workflows/ci.yml`.

**App Android (opcional, 2 min).** Con el emulador y el backend demo arrancados,
abrir `android-app`, ejecutar `.\gradlew.bat installDebug` e iniciar sesión con
`10.0.2.2:8080`. Mostrar el tablero, la pestaña de alertas, la pestaña **Mapa**
(contorno embebido, sin Google) y la pestaña **Predicciones** (elegir 6 h y
pulsar Generar; la IA responde 4 filas, una por estación). Aclarar que FCM
**no** está integrado. Si se pregunta por la CI, el job `android` corre los
tests y genera el APK en GitHub Actions.

## 22.9 Preguntas previsibles

| Pregunta | Respuesta corta |
|---|---|
| ¿Por qué no Neo4j? | Cuatro estaciones y una matriz de distancias; un motor de grafos añadiría un servicio sin mejorar el modelo |
| ¿Por qué no TimescaleDB? | ~140 k muestras/año; un índice compuesto basta |
| ¿Por qué React y no Thymeleaf? | La SPA separa presentación y API JSON; el build viaja en el JAR, la demo no necesita Node y el mapa/gráfico siguen siendo SVG propios sin CDN |
| ¿Por qué la IA está separada? | Python es el entorno del modelo; el contrato HTTP aísla versiones y permite degradar |
| ¿Qué pasa si la IA falla? | La ingesta y las alertas no dependen de ella; solo se pierde la predicción |
| ¿Los datos son reales? | No: son sintéticos y el sistema lo declara en la tabla, la API y el panel |
| ¿La predicción cambia la alerta? | No: el veredicto es de la medición; la predicción es otra dimensión |
| ¿Qué falta? | Datos de campo, coordenadas medidas, TLS, Docker/Sonar ejecutados, FCM y mapa interactivo Android |

## 22.10 Tiempo total

20–25 minutos con las pruebas de IA; 12–15 minutos si se omiten y se muestra el
artefacto.

## 22.11 Problemas frecuentes

| Síntoma | Causa | Solución |
|---|---|---|
| El backend no arranca | JDK 26 en el PATH | Usar el JBR de IntelliJ (Java 21) |
| `H2 not found` | Falta `useTestClasspath=true` | Añadirlo al comando |
| El panel redirige a login en bucle | Cookies bloqueadas | Usar `localhost`, no una IP con proxy |
| La predicción da 503 | FastAPI no está o no encontró el artefacto | Revisar `LAGUNA_MODEL_PATH` y `/health` |
| El puerto 8080 está ocupado | Otro proceso | `Get-NetTCPConnection -LocalPort 8080` |
| El simulador da 401 | Token vencido o credenciales cambiadas | Revisar `APP_USER`/`APP_PASSWORD` |
