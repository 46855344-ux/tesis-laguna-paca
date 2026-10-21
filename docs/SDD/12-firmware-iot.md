# 12 · Firmware IoT

## 12.1 Estructura

```
iot-firmware/
├── lib/lagunapaca_core/     C++ puro, sin Arduino: lógica verificable
│   ├── include/lagunapaca/  Puertos, filtros, calibración, payload, identidad
│   └── src/                 Implementación
├── src/
│   ├── main.cpp             Orquestación + Deep Sleep
│   ├── hardware/            ADC, DS18B20, transporte HTTP
│   └── net/                 WiFi
├── include/config.h         Pines, intervalos y calibración nominal
├── include/secrets.h        Credenciales (ignorado por git)
├── test/test_core/          38 tests nativos (host)
├── platformio.ini
└── secrets.example.h
```

El núcleo es **C++11** porque el framework Arduino del ESP32 compila con
`gnu++11`; los tests nativos usan `gnu++17`. Por eso `TelemetryNodeConfig` tiene
constructor explícito: una `struct` con inicializadores de miembro deja de ser
agregado en C++11 y rompe `TelemetryNodeConfig{...}`.

## 12.2 Hardware y pines

| Sensor | Pin | Nota |
|---|---|---|
| pH (PH-4502C) | GPIO 34 (ADC1_CH6) | Divisor 1.0 |
| Turbidez (TSW-20M) | GPIO 35 (ADC1_CH7) | Divisor 1.56 (su salida llega a ~4.5 V) |
| TDS (Gravity) | GPIO 32 (ADC1_CH4) | Divisor 1.0 |
| Temperatura (DS18B20) | GPIO 4 | 1-Wire, resistencia de 4.7 k a 3V3 |

Los tres canales analógicos **deben** ir a ADC1: ADC2 deja de funcionar mientras
el WiFi está activo. ADC de 12 bits, referencia 3.3 V, sobremuestreo 8.

## 12.3 Ciclo de medición

1. Despierta por temporizador (900 s por defecto).
2. Lee cada canal analógico 3 veces y aplica **media móvil causal** (ventana
   `w = 3`, el valor de la tesis).
3. Convierte a unidades físicas con la calibración de `config.h`.
4. Valida plausibilidad: pH 0–14, turbidez ≥ 0, temperatura −5…60 °C, TDS ≥ 0.
   Una muestra implausible **no se envía**.
5. Conecta WiFi (timeout 15 s). Sin conexión, no inventa ni reintenta en el
   mismo ciclo: la muestra se descarta y se registra en serie.
6. Obtiene un JWT (HTTP) o usa las credenciales del broker (MQTT).
7. Envía el payload con `sample_id`.
8. Si el transporte confirma, incrementa la secuencia; si no, la conserva para
   el reintento.
9. Desconecta WiFi y entra en Deep Sleep descontando el tiempo activo, con un
   mínimo de seguridad de 10 s.

## 12.4 Payload

```json
{
  "sample_id": "N1-3FA4C21B-17",
  "node_id": "N1",
  "ph": 7.2,
  "turbidity_ntu": 15.4,
  "temp_c": 18.5,
  "tds_ppm": 320
}
```

Precisión: pH y turbidez con 2 decimales; temperatura y TDS con 1. El formato se
fija con tests de cadena exacta (`test_payload_esElContratoExacto`).

## 12.5 Identidad de muestra e idempotencia

Formato: `<nodeId>-<bootId en 8 hex>-<secuencia>`.

- `bootId` se genera con `esp_random()` en cada **arranque en frío** y se guarda
  en RTC (`RTC_DATA_ATTR`); sobrevive al Deep Sleep.
- `secuencia` también vive en RTC y **solo avanza cuando el transporte confirma
  la entrega**. Un reintento reutiliza el mismo identificador y el backend
  responde 200 sin duplicar.
- Si se pierde la energía, la secuencia vuelve a 0 pero el `bootId` es nuevo, de
  modo que no choca con identificadores anteriores.

Sin esta separación, un contador solo habría reutilizado ids tras cada corte de
luz y el backend habría descartado mediciones reales como duplicadas.

## 12.6 Transportes

| Vía | Entorno | Autenticación | Flash |
|---|---|---|---|
| HTTP POST | `esp32dev` (por defecto) | JWT cacheado en RTC | 72.3 % (947 KB) |
| MQTT QoS 1 | `esp32dev-mqtt` (`-DLAGUNA_TELEMETRY_MQTT`) | Credenciales del broker | 59.8 % (784 KB) |

Ambos implementan `laguna::TelemetryTransport`; el núcleo no cambia. La vía MQTT
usa `256dpi/MQTT` porque PubSubClient solo publica con QoS 0. El cliente MQTT se
construye con `buildMqttClientId()`: `lp-<8 hex MAC>-<nodeId>`, recortado a 23
bytes (el mínimo que MQTT 3.1.1 obliga a aceptar).

El JWT se cachea en memoria RTC y su vencimiento se lleva de forma monótona
sumando el tiempo dormido, porque el nodo no tiene NTP.

## 12.7 Calibración: advertencia

Los valores de `config.h` son **nominales**. Hay que medirlos en laboratorio con
soluciones buffer (pH 4.01 / 7.00 / 10.01) y con el montaje definitivo. Ningún
resultado de este repositorio debe presentarse como calibración de campo.

## 12.8 Pruebas

38 tests nativos con `pio test -e native`, sin hardware:

- filtros y calibración;
- payload exacto y `sample_id`;
- plausibilidad y transporte fallido;
- política de Deep Sleep;
- client id MQTT;
- orquestación del nodo con dobles de sensores.

Ambas variantes compilan: `pio run -e esp32dev -e esp32dev-mqtt`.

## 12.9 Limitaciones

- `HTTPClient` es HTTP plano. En producción requiere TLS (`WiFiClientSecure` con
  huella o bundle de CA) y backend en HTTPS.
- Sin OTA: si se agrega, hay que cambiar el esquema de particiones.
- Sin medición de consumo: la autonomía solar está sin validar.
- Sin reloj: la hora la pone el servidor; el nodo no puede fechar la muestra.
- Los sensores no se probaron contra patrones; la calibración es nominal.
