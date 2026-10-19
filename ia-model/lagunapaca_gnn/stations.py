"""Estaciones de muestreo de la Laguna de Paca y distancias entre ellas.

Este módulo es el puente entre la geografía y el grafo: convierte coordenadas en
la matriz de distancias que alimenta a :func:`lagunapaca_gnn.graph.gaussian_adjacency`.

.. warning::

   Las coordenadas de :data:`PROVISIONAL_STATIONS` **no son puntos medidos en
   campo**. Se derivaron del polígono del lago publicado en OpenStreetMap
   (`way 26947746`): se tomaron los cuatro extremos del polígono (norte, este,
   sur y oeste) y se desplazó cada uno un 5 % hacia el centroide, comprobando
   con *point-in-polygon* que el punto cae dentro del agua.

   Sirven para que el grafo tenga una geometría realista y para poder entrenar
   el modelo, pero **deben reemplazarse por las coordenadas reales de los
   puntos de muestreo** antes de reportar resultados de la tesis. Un jurado
   preguntará dónde están las estaciones y con qué criterio se eligieron.

Los identificadores ``N1``…``N4`` coinciden con el campo ``node_id`` que envía
el ESP32 y que ya almacena el backend: el modelo y el sistema hablan del mismo
nodo, sin traducciones intermedias.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np

#: Radio medio terrestre en kilómetros (IUGG).
EARTH_RADIUS_KM = 6371.0088

#: Par ``(latitud, longitud)`` en grados decimales.
Coordinate = tuple[float, float]


@dataclass(frozen=True)
class Station:
    """Una estación de muestreo de la laguna."""

    id: str
    name: str
    latitude: float
    longitude: float

    @property
    def coordinates(self) -> Coordinate:
        """Coordenadas como ``(latitud, longitud)``."""
        return (self.latitude, self.longitude)


#: Cuatro estaciones provisionales, una por cada extremo de la laguna.
#: Ver la advertencia del módulo: son puntos propuestos, no medidos.
PROVISIONAL_STATIONS: tuple[Station, ...] = (
    Station(id="N1", name="Norte", latitude=-11.71703, longitude=-75.506971),
    Station(id="N2", name="Este", latitude=-11.737817, longitude=-75.492503),
    Station(id="N3", name="Sur", latitude=-11.746126, longitude=-75.497581),
    Station(id="N4", name="Oeste", latitude=-11.724006, longitude=-75.514957),
)


def _validate(coordinate: Coordinate) -> Coordinate:
    """Comprueba que las coordenadas estén en grados decimales válidos."""
    latitude, longitude = float(coordinate[0]), float(coordinate[1])

    if not -90.0 <= latitude <= 90.0:
        raise ValueError(f"la latitud debe estar entre -90 y 90 grados, no {latitude}")
    if not -180.0 <= longitude <= 180.0:
        raise ValueError(f"la longitud debe estar entre -180 y 180 grados, no {longitude}")
    return (latitude, longitude)


def haversine_km(origin: Coordinate, destination: Coordinate) -> float:
    """Distancia ortodrómica entre dos coordenadas, en kilómetros.

    Es la distancia sobre la superficie de la esfera, no la distancia euclidiana
    en grados: un grado de longitud no mide lo mismo en el ecuador que a 11° de
    latitud. A la escala de la laguna la diferencia es pequeña, pero usar grados
    como si fueran kilómetros deformaría el grafo.

    Args:
        origin: ``(latitud, longitud)`` en grados decimales.
        destination: ``(latitud, longitud)`` en grados decimales.

    Raises:
        ValueError: si alguna coordenada está fuera de rango.
    """
    lat1, lon1 = _validate(origin)
    lat2, lon2 = _validate(destination)

    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    h = (math.sin(delta_phi / 2.0) ** 2
         + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2)
    return 2.0 * EARTH_RADIUS_KM * math.asin(math.sqrt(h))


def distance_matrix_km(stations) -> np.ndarray:
    """Matriz simétrica de distancias entre estaciones, en kilómetros.

    Args:
        stations: secuencia de :class:`Station`.

    Returns:
        Arreglo ``(n, n)`` con diagonal cero y en kilómetros.

    Raises:
        ValueError: si la secuencia está vacía.
    """
    station_list = list(stations)
    if not station_list:
        raise ValueError("la lista de estaciones no puede estar vacía")

    count = len(station_list)
    matrix = np.zeros((count, count), dtype=float)

    for i in range(count):
        for j in range(i + 1, count):
            distance = haversine_km(
                station_list[i].coordinates, station_list[j].coordinates)
            matrix[i, j] = distance
            matrix[j, i] = distance

    return matrix
