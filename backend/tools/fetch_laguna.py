"""Genera el contorno de la Laguna de Paca que dibuja el panel web.

Descarga el poligono del lago desde OpenStreetMap, lo simplifica y escribe
``backend/src/main/resources/geo/laguna-paca.json`` con dos cosas:

* el anillo del lago, en pares ``[longitud, latitud]``;
* las cuatro estaciones, **importadas de** ``ia-model/lagunapaca_gnn/stations.py``.

Lo segundo es lo importante: las coordenadas no se copian a mano. Python sigue
siendo la fuente de verdad del grafo y el panel consume un artefacto generado,
asi que el mapa y el modelo no pueden discrepar. Cuando se reemplacen las
coordenadas provisionales por las medidas en campo, basta con editar
``stations.py`` y volver a ejecutar este script.

Uso (desde la raiz del repositorio, con el entorno de ia-model):

    ia-model\\.venv\\Scripts\\python.exe backend\\tools\\fetch_laguna.py

Requiere internet: es una herramienta de mantenimiento, no parte de la demo.
El JSON resultante se versiona, de modo que el panel funciona sin conexion.
"""

from __future__ import annotations

import json
import sys
import urllib.request
import xml.etree.ElementTree as ET
from datetime import date
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
DESTINO = RAIZ / "backend" / "src" / "main" / "resources" / "geo" / "laguna-paca.json"

#: Way de OpenStreetMap con el poligono de la laguna.
WAY_ID = 26947746
OSM_URL = f"https://api.openstreetmap.org/api/0.6/way/{WAY_ID}/full"

#: Tolerancia de simplificacion en grados (~7 m a esta latitud). Conserva la
#: forma y evita que el SVG cargue 194 puntos que no se distinguen a simple vista.
EPSILON = 0.00006


def descargar_anillo() -> list[tuple[float, float]]:
    """Devuelve el anillo del lago como ``(lat, lon)``, en orden y abierto."""
    peticion = urllib.request.Request(
        OSM_URL, headers={"User-Agent": "tesis-laguna-paca/1.0 (mapa del panel)"})
    with urllib.request.urlopen(peticion, timeout=60) as respuesta:
        arbol = ET.fromstring(respuesta.read())

    nodos = {
        nodo.get("id"): (float(nodo.get("lat")), float(nodo.get("lon")))
        for nodo in arbol.iter("node")
    }

    way = arbol.find("way")
    if way is None:
        raise SystemExit("la respuesta de OSM no trae el way esperado")

    anillo = [nodos[nd.get("ref")] for nd in way.findall("nd") if nd.get("ref") in nodos]
    if len(anillo) < 4:
        raise SystemExit("el anillo tiene muy pocos puntos")

    # OSM entrega el anillo cerrado (ultimo == primero). Para simplificar se abre
    # y se vuelve a cerrar al escribir, que es lo que exige GeoJSON.
    if anillo[0] == anillo[-1]:
        anillo = anillo[:-1]
    return anillo


def simplificar(puntos: list[tuple[float, float]], epsilon: float) -> list[tuple[float, float]]:
    """Douglas-Peucker: deja los vertices que definen la forma."""
    if len(puntos) < 3:
        return puntos

    inicio, fin = puntos[0], puntos[-1]
    indice, maxima = -1, 0.0
    for i in range(1, len(puntos) - 1):
        lat, lon = puntos[i]
        dy, dx = fin[0] - inicio[0], fin[1] - inicio[1]
        norma = (dx * dx + dy * dy) ** 0.5 or 1e-12
        distancia = abs(dx * (lat - inicio[0]) - dy * (lon - inicio[1])) / norma
        if distancia > maxima:
            maxima, indice = distancia, i

    if maxima <= epsilon:
        return [inicio, fin]

    izquierda = simplificar(puntos[: indice + 1], epsilon)
    derecha = simplificar(puntos[indice:], epsilon)
    return izquierda[:-1] + derecha


def estaciones() -> list[dict]:
    """Lee las estaciones de ia-model, que es la fuente de verdad del grafo."""
    sys.path.insert(0, str(RAIZ / "ia-model"))
    from lagunapaca_gnn.stations import PROVISIONAL_STATIONS  # noqa: PLC0415

    return [
        {"id": e.id, "nombre": e.name, "lat": e.latitude, "lon": e.longitude}
        for e in PROVISIONAL_STATIONS
    ]


def main() -> None:
    anillo = descargar_anillo()
    simplificado = simplificar(anillo, EPSILON)

    # GeoJSON: el anillo se cierra repitiendo el primer punto y las coordenadas
    # van en orden [longitud, latitud].
    cerrado = simplificado + [simplificado[0]]

    documento = {
        "fuente": f"OpenStreetMap, way {WAY_ID}",
        "url": f"https://www.openstreetmap.org/way/{WAY_ID}",
        "nombre": "Laguna de Paca",
        "elevacion_m": 3418,
        "capturado": date.today().isoformat(),
        "aviso": (
            "El contorno es real. Las coordenadas de las estaciones son "
            "provisionales y deben reemplazarse por los puntos medidos en campo."
        ),
        "laguna": [[round(lon, 6), round(lat, 6)] for lat, lon in cerrado],
        "estaciones": estaciones(),
    }

    DESTINO.parent.mkdir(parents=True, exist_ok=True)
    DESTINO.write_text(json.dumps(documento, ensure_ascii=False, indent=1) + "\n",
                       encoding="utf-8")

    print(f"Escrito {DESTINO.relative_to(RAIZ)}")
    print(f"  puntos del contorno: {len(anillo)} -> {len(simplificado)}")
    print(f"  estaciones: {', '.join(e['id'] for e in documento['estaciones'])}")


if __name__ == "__main__":
    main()
