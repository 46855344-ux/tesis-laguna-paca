"""Tests de las estaciones de muestreo y de la matriz de distancias.

El grafo de la ST-GNN se construye a partir de estas distancias, así que un
error aquí no se ve como un test rojo: se ve como un modelo que "aprende" una
laguna que no existe. Por eso se prueba por separado el cálculo de la distancia
sobre la esfera (con un valor conocido e independiente del código) y la matriz
que alimenta al grafo.
"""

import numpy as np
import pytest

from lagunapaca_gnn.stations import (
    PROVISIONAL_STATIONS,
    Station,
    distance_matrix_km,
    haversine_km,
)


class TestHaversine:
    """La distancia sobre la esfera se comprueba contra un valor conocido."""

    def test_un_grado_de_latitud_son_unos_111_km(self):
        # 2 * pi * 6371 / 360 = 111.19 km. Es un dato de la Tierra, no del codigo.
        assert haversine_km((0.0, 0.0), (1.0, 0.0)) == pytest.approx(111.19, abs=0.02)

    def test_un_grado_de_longitud_en_el_ecuador_son_unos_111_km(self):
        assert haversine_km((0.0, 0.0), (0.0, 1.0)) == pytest.approx(111.19, abs=0.02)

    def test_la_distancia_al_mismo_punto_es_cero(self):
        assert haversine_km((-11.73, -75.50), (-11.73, -75.50)) == 0.0

    def test_es_simetrica(self):
        norte, sur = (-11.717, -75.507), (-11.746, -75.498)

        assert haversine_km(norte, sur) == pytest.approx(haversine_km(sur, norte))

    def test_los_grados_de_longitud_se_acortan_en_latitudes_altas(self):
        # A 60 grados de latitud un grado de longitud mide la mitad que en el
        # ecuador. Si el calculo ignorara la latitud, este test fallaria.
        en_ecuador = haversine_km((0.0, 0.0), (0.0, 1.0))
        a_sesenta = haversine_km((60.0, 0.0), (60.0, 1.0))

        assert a_sesenta == pytest.approx(en_ecuador * 0.5, rel=1e-3)

    def test_rechaza_una_latitud_fuera_de_rango(self):
        with pytest.raises(ValueError, match="latitud"):
            haversine_km((91.0, 0.0), (0.0, 0.0))

    def test_rechaza_una_longitud_fuera_de_rango(self):
        with pytest.raises(ValueError, match="longitud"):
            haversine_km((0.0, 181.0), (0.0, 0.0))


class TestStations:
    """Las cuatro estaciones de la laguna."""

    def test_hay_cuatro_estaciones(self):
        assert len(PROVISIONAL_STATIONS) == 4

    def test_los_identificadores_son_los_del_nodo_esp32(self):
        # Coinciden con el campo node_id del payload del firmware y con lo que
        # ya almacena el backend: el modelo y el sistema hablan del mismo nodo.
        assert [station.id for station in PROVISIONAL_STATIONS] == ["N1", "N2", "N3", "N4"]

    def test_cada_estacion_tiene_nombre(self):
        assert all(station.name for station in PROVISIONAL_STATIONS)

    def test_las_coordenadas_caen_dentro_de_la_laguna(self):
        # Caja del poligono del lago segun OpenStreetMap (way 26947746).
        lat_min, lat_max = -11.746874, -11.716247
        lon_min, lon_max = -75.5155074, -75.4918721

        for station in PROVISIONAL_STATIONS:
            assert lat_min <= station.latitude <= lat_max, station.id
            assert lon_min <= station.longitude <= lon_max, station.id

    def test_las_estaciones_son_puntos_distintos(self):
        coordinates = {(station.latitude, station.longitude) for station in PROVISIONAL_STATIONS}

        assert len(coordinates) == 4

    def test_expone_sus_coordenadas(self):
        station = Station(id="N1", name="Norte", latitude=-11.7, longitude=-75.5)

        assert station.coordinates == (-11.7, -75.5)


class TestDistanceMatrix:
    """La matriz que consume el grafo gaussiano."""

    def test_tiene_la_forma_del_numero_de_estaciones(self):
        matrix = distance_matrix_km(PROVISIONAL_STATIONS)

        assert matrix.shape == (4, 4)

    def test_la_diagonal_es_cero(self):
        matrix = distance_matrix_km(PROVISIONAL_STATIONS)

        assert np.allclose(np.diag(matrix), 0.0)

    def test_es_simetrica(self):
        matrix = distance_matrix_km(PROVISIONAL_STATIONS)

        assert np.allclose(matrix, matrix.T)

    def test_las_distancias_son_coherentes_con_el_tamano_de_la_laguna(self):
        # La laguna mide ~3.4 km de norte a sur y ~2.6 km de este a oeste.
        matrix = distance_matrix_km(PROVISIONAL_STATIONS)

        assert matrix.max() < 5.0
        assert matrix[matrix > 0].min() > 0.5

    def test_calcula_las_distancias_conocidas(self):
        # Valores de regresion: si alguien mueve una estacion sin querer, salta.
        matrix = distance_matrix_km(PROVISIONAL_STATIONS)

        assert matrix[0, 3] == pytest.approx(1.165, abs=0.01)  # norte - oeste
        assert matrix[1, 2] == pytest.approx(1.077, abs=0.01)  # este - sur
        assert matrix[0, 2] == pytest.approx(3.393, abs=0.01)  # norte - sur

    def test_rechaza_una_lista_vacia(self):
        with pytest.raises(ValueError, match="vac"):
            distance_matrix_km([])


class TestIntegracionConElGrafo:
    """El grafo real de la laguna, de la coordenada a la adyacencia podada."""

    def test_el_grafo_de_la_laguna_queda_conectado(self):
        from lagunapaca_gnn.graph import gaussian_adjacency, sparsify

        distances = distance_matrix_km(PROVISIONAL_STATIONS)
        adjacency = gaussian_adjacency(distances, sigma=1.5)

        sparse = sparsify(adjacency, distances, k=1)

        edges = {(i, j) for i in range(4) for j in range(i + 1, 4) if sparse[i, j] > 0}
        assert len(edges) >= 3

        # Recorrido en anchura: las cuatro estaciones deben alcanzarse entre si.
        neighbours = {node: set() for node in range(4)}
        for i, j in edges:
            neighbours[i].add(j)
            neighbours[j].add(i)
        visited, pending = set(), [0]
        while pending:
            node = pending.pop()
            if node in visited:
                continue
            visited.add(node)
            pending.extend(neighbours[node] - visited)
        assert visited == {0, 1, 2, 3}
