"""Simulador del nodo sensor de la Laguna de Paca.

Reemplaza al ESP32 cuando no hay hardware: se autentica contra el backend y
publica muestras con el mismo payload que envía el nodo, para poder demostrar
el sistema completo (ingesta, evaluación ECA-Agua y consulta histórica).

Solo usa la biblioteca estándar: no requiere instalar nada.

Uso:
    python simulate_node.py --count 12 --scenario mixed
    python simulate_node.py --url http://localhost:8080 --scenario alert

Escenarios:
    normal  valores dentro de los límites ECA-Agua (no generan alerta)
    alert   pH por encima y por debajo del rango (generan PH_HIGH y PH_LOW)
    limit   pH exactamente en 6.5 y 9.0, que NO son alerta según la regla
    mixed   mezcla los tres (recomendado para una demostración)

Aviso: la regla ECA-Agua vive en el backend, que es la única fuente de verdad.
Aquí solo se generan valores dentro o fuera del rango para ejercitar ambas ramas;
el simulador no decide si hay alerta, solo informa lo que el backend respondió.
"""

from __future__ import annotations

import argparse
import json
import random
import sys
import time
import urllib.error
import urllib.request

# Límites ECA-Agua (categoría 4: lagunas y lagos). Se usan únicamente para
# generar datos de demostración; ver el aviso del encabezado.
PH_ECA_MIN = 6.5
PH_ECA_MAX = 9.0

# Precisión con la que el firmware serializa cada variable.
PH_DECIMALS = 2
TURBIDITY_DECIMALS = 2
TEMPERATURE_DECIMALS = 1
TDS_DECIMALS = 1

DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_TIMEOUT_SECONDS = 10.0

SCENARIOS = ("normal", "alert", "limit", "mixed")


# ---------------------------------------------------------------------------
# Construcción del payload
# ---------------------------------------------------------------------------

def format_decimal(value: float, decimals: int) -> str:
    """Formatea un número como lo hace el firmware: sin ceros finales.

    Reproduce el `formatDecimal` del núcleo portable del ESP32 para que el
    payload del simulador sea idéntico al del nodo real (7.20 -> "7.2").
    """
    text = f"{value:.{decimals}f}"
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text


def build_payload(node_id: str, ph: float, turbidity_ntu: float,
                  temp_c: float, tds_ppm: float) -> str:
    """Devuelve el payload exacto del ESP32, sin espacios y con las claves en orden."""
    return (
        "{"
        f'"node_id":"{node_id}",'
        f'"ph":{format_decimal(ph, PH_DECIMALS)},'
        f'"turbidity_ntu":{format_decimal(turbidity_ntu, TURBIDITY_DECIMALS)},'
        f'"temp_c":{format_decimal(temp_c, TEMPERATURE_DECIMALS)},'
        f'"tds_ppm":{format_decimal(tds_ppm, TDS_DECIMALS)}'
        "}"
    )


# ---------------------------------------------------------------------------
# Escenarios de medición
# ---------------------------------------------------------------------------

def _normal(rng: random.Random) -> dict:
    return {
        "ph": round(rng.uniform(7.0, 7.6), PH_DECIMALS),
        "turbidity_ntu": round(rng.uniform(10.0, 25.0), TURBIDITY_DECIMALS),
        "temp_c": round(rng.uniform(17.0, 19.5), TEMPERATURE_DECIMALS),
        "tds_ppm": round(rng.uniform(280.0, 350.0), TDS_DECIMALS),
    }


def _alert(rng: random.Random, index: int) -> dict:
    # Alterna entre alcalino (PH_HIGH) y ácido (PH_LOW).
    if index % 2 == 0:
        ph = round(rng.uniform(9.2, 9.8), PH_DECIMALS)
    else:
        ph = round(rng.uniform(6.0, 6.4), PH_DECIMALS)
    return {
        "ph": ph,
        "turbidity_ntu": round(rng.uniform(30.0, 60.0), TURBIDITY_DECIMALS),
        "temp_c": round(rng.uniform(18.0, 21.0), TEMPERATURE_DECIMALS),
        "tds_ppm": round(rng.uniform(400.0, 600.0), TDS_DECIMALS),
    }


def _limit(rng: random.Random, index: int) -> dict:
    # Los bordes exactos: la regla los considera DENTRO del rango.
    ph = PH_ECA_MAX if index % 2 == 0 else PH_ECA_MIN
    return {
        "ph": ph,
        "turbidity_ntu": round(rng.uniform(15.0, 20.0), TURBIDITY_DECIMALS),
        "temp_c": round(rng.uniform(17.5, 19.0), TEMPERATURE_DECIMALS),
        "tds_ppm": round(rng.uniform(300.0, 360.0), TDS_DECIMALS),
    }


def readings(scenario: str, count: int, seed: int = 0) -> list[dict]:
    """Genera `count` lecturas del escenario pedido (deterministas para una semilla)."""
    if scenario not in SCENARIOS:
        raise ValueError(
            f"escenario desconocido: {scenario!r} (válidos: {', '.join(SCENARIOS)})")

    rng = random.Random(seed)
    generated = []
    for index in range(count):
        if scenario == "normal":
            generated.append(_normal(rng))
        elif scenario == "alert":
            generated.append(_alert(rng, index))
        elif scenario == "limit":
            generated.append(_limit(rng, index))
        else:  # mixed
            cycle = index % 4
            if cycle == 0:
                generated.append(_normal(rng))
            elif cycle == 1:
                generated.append(_alert(rng, index))
            elif cycle == 2:
                generated.append(_limit(rng, index))
            else:
                generated.append(_normal(rng))
    return generated


# ---------------------------------------------------------------------------
# Cliente HTTP
# ---------------------------------------------------------------------------

def endpoint(base_url: str, path: str) -> str:
    """Compone la URL de la API sin duplicar barras."""
    return f"{base_url.rstrip('/')}/api/v1/{path.lstrip('/')}"


def _post(url: str, body: str, token: str | None = None,
          timeout: float = DEFAULT_TIMEOUT_SECONDS) -> tuple[int, str]:
    """POST con el cuerpo ya serializado. Devuelve (estado, texto de respuesta)."""
    request = urllib.request.Request(url, data=body.encode("utf-8"), method="POST")
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", f"Bearer {token}")

    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        # 4xx/5xx también son respuestas válidas del servidor: se informan, no se lanzan.
        return error.code, error.read().decode("utf-8", errors="replace")


def fetch_token(base_url: str, username: str, password: str,
                timeout: float = DEFAULT_TIMEOUT_SECONDS) -> str:
    """Obtiene el JWT como lo hace el nodo o cualquier cliente de la API."""
    body = json.dumps({"username": username, "password": password})
    status, text = _post(endpoint(base_url, "auth/token"), body, timeout=timeout)

    if status != 200:
        raise RuntimeError(f"autenticación fallida (HTTP {status}): {text}")
    return json.loads(text)["access_token"]


def describe_response(text: str) -> str:
    """Resume la respuesta del backend para la consola de la demostración."""
    try:
        body = json.loads(text)
    except json.JSONDecodeError:
        return "respuesta ilegible"

    if "alert" not in body:
        message = body.get("message", "")
        return f"error: {message}" if message else "error"

    if not body["alert"]:
        return "sin alerta"

    codes = ", ".join(reason["code"] for reason in body.get("alert_reasons", []))
    return f"ALERTA {codes}"


# ---------------------------------------------------------------------------
# Programa principal
# ---------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="simulate_node.py",
        description="Simula el nodo ESP32 enviando telemetría al backend.")
    parser.add_argument("--url", default=DEFAULT_BASE_URL,
                        help=f"URL base del backend (por defecto {DEFAULT_BASE_URL})")
    parser.add_argument("--username", default="admin", help="usuario de la API")
    parser.add_argument("--password", default="admin123", help="contraseña de la API")
    parser.add_argument("--node-id", default="N1", help="identificador del nodo")
    parser.add_argument("--scenario", default="mixed", choices=SCENARIOS,
                        help="tipo de lecturas a generar (por defecto mixed)")
    parser.add_argument("--count", type=int, default=10,
                        help="cuántas muestras enviar (por defecto 10)")
    parser.add_argument("--interval", type=float, default=1.0,
                        help="segundos entre muestras (por defecto 1.0)")
    parser.add_argument("--seed", type=int, default=0,
                        help="semilla del generador (para repetir una demo)")
    parser.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT_SECONDS,
                        help="tiempo máximo de cada petición, en segundos")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)

    print(f"Simulador de nodo -> {args.url}  (escenario: {args.scenario}, "
          f"{args.count} muestras)")

    try:
        token = fetch_token(args.url, args.username, args.password, args.timeout)
    except urllib.error.URLError as error:
        print(f"No se pudo conectar con {args.url}: {error.reason}", file=sys.stderr)
        print("¿Está el backend levantado?", file=sys.stderr)
        return 2
    except RuntimeError as error:
        print(error, file=sys.stderr)
        return 2

    print("Autenticación correcta. Enviando telemetría...\n")

    telemetry_url = endpoint(args.url, "telemetry")
    alerts = 0
    failures = 0

    for index, reading in enumerate(readings(args.scenario, args.count, args.seed),
                                    start=1):
        payload = build_payload(args.node_id, **reading)
        try:
            status, text = _post(telemetry_url, payload, token, args.timeout)
        except urllib.error.URLError as error:
            print(f"[{index:>3}/{args.count}] sin conexión: {error.reason}")
            failures += 1
            continue

        summary = describe_response(text)
        if summary.startswith("ALERTA"):
            alerts += 1
        if status >= 400:
            failures += 1

        print(f"[{index:>3}/{args.count}] pH={reading['ph']:>5.2f}  "
              f"HTTP {status}  {summary}")

        if index < args.count:
            time.sleep(args.interval)

    print(f"\nResumen: {args.count - failures}/{args.count} aceptadas, "
          f"{alerts} con alerta ECA-Agua, {failures} rechazadas.")

    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
