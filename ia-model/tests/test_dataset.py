"""Tests del generador de series sintéticas.

El simulador del backend (`backend/tools/simulate_node.py`) genera muestras
**independientes**: cada valor es un sorteo nuevo. Sirve para probar la ingesta,
pero no para entrenar un modelo temporal, porque no hay nada que aprender entre
un instante y el siguiente.

Este generador es distinto a propósito y los tests lo comprueban: hay
autocorrelación temporal, ciclo diario y correlación espacial entre estaciones
vecinas. Sin esas tres propiedades, el TCN y el GCN no tendrían nada que
aprovechar y un R² alto no significaría nada.
"""

import numpy as np
import pytest

from lagunapaca_gnn.dataset import (
    PHYSICAL_LIMITS,
    VARIABLE_NAMES,
    simulate_lagoon,
)
from lagunapaca_gnn.stations import PROVISIONAL_STATIONS, distance_matrix_km

DISTANCES = distance_matrix_km(PROVISIONAL_STATIONS)


class TestForma:

    def test_devuelve_nodos_tiempo_variables(self):
        series = simulate_lagoon(DISTANCES, steps=240, seed=1)

        assert series.shape == (4, 240, 4)

    def test_las_variables_son_las_del_payload_esp32(self):
        # Mismos nombres que manda el nodo y que almacena el backend.
        assert VARIABLE_NAMES == ("ph", "turbidity_ntu", "temp_c", "tds_ppm")

    def test_hay_un_limite_fisico_por_variable(self):
        assert len(PHYSICAL_LIMITS) == len(VARIABLE_NAMES)

    def test_rechaza_cero_pasos(self):
        with pytest.raises(ValueError, match="steps"):
            simulate_lagoon(DISTANCES, steps=0)

    def test_rechaza_distancias_invalidas(self):
        with pytest.raises(ValueError, match="cuadrada"):
            simulate_lagoon(np.ones((3, 2)), steps=10)


class TestRangosFisicos:

    def test_los_valores_son_fisicamente_posibles(self):
        series = simulate_lagoon(DISTANCES, steps=720, seed=2)

        for index, (low, high) in enumerate(PHYSICAL_LIMITS):
            assert series[:, :, index].min() >= low, VARIABLE_NAMES[index]
            assert series[:, :, index].max() <= high, VARIABLE_NAMES[index]

    def test_la_temperatura_es_de_una_laguna_andina(self):
        # La laguna está a 3 363 m: el agua no puede estar a 25 grados.
        series = simulate_lagoon(DISTANCES, steps=720, seed=3)

        temperature = series[:, :, 2]
        assert 5.0 < temperature.mean() < 25.0

    def test_el_ph_no_es_agua_destilada_ni_una_piscina(self):
        series = simulate_lagoon(DISTANCES, steps=720, seed=4)

        ph = series[:, :, 0]
        assert 6.0 < ph.mean() < 9.0


class TestDinamicaTemporal:

    def test_esta_autocorrelacionada(self):
        # Dos instantes consecutivos se parecen mucho más que dos lejanos.
        # Un generador de muestras independientes fallaría este test.
        ph = simulate_lagoon(DISTANCES, steps=720, seed=5)[0, :, 0]

        consecutiva = float(np.corrcoef(ph[:-1], ph[1:])[0, 1])
        lejana = float(np.corrcoef(ph[:-200], ph[200:])[0, 1])

        assert consecutiva > 0.9
        assert consecutiva > lejana

    def test_la_temperatura_tiene_ciclo_diario(self):
        # Con pasos de una hora, el ciclo son 24 muestras. Se promedian varios
        # días para que el ruido no oculte el patrón.
        series = simulate_lagoon(DISTANCES, steps=24 * 10, seed=6)

        por_hora = series[:, :, 2].mean(axis=0).reshape(10, 24).mean(axis=0)

        assert por_hora.max() - por_hora.min() > 1.0

    def test_es_reproducible_con_la_misma_semilla(self):
        first = simulate_lagoon(DISTANCES, steps=120, seed=9)
        second = simulate_lagoon(DISTANCES, steps=120, seed=9)

        assert np.array_equal(first, second)

    def test_semillas_distintas_dan_series_distintas(self):
        first = simulate_lagoon(DISTANCES, steps=120, seed=9)
        second = simulate_lagoon(DISTANCES, steps=120, seed=10)

        assert not np.allclose(first, second)


class TestDinamicaEspacial:
    """Es lo que justifica que el modelo tenga un grafo y no solo un TCN."""

    def test_las_estaciones_vecinas_se_parecen_mas_que_las_lejanas(self):
        # N1-N4 están a 1.165 km y N1-N3 a 3.393 km. Si la distancia no
        # influyera, la correlación sería la misma para ambas parejas.
        ph = simulate_lagoon(DISTANCES, steps=1440, seed=7)[:, :, 0]

        cerca = float(np.corrcoef(ph[0], ph[3])[0, 1])
        lejos = float(np.corrcoef(ph[0], ph[2])[0, 1])

        assert cerca > lejos

    def test_las_estaciones_no_son_copias_una_de_otra(self):
        # Correlación alta no significa series idénticas: cada estación tiene
        # su propio componente y su propio ruido.
        ph = simulate_lagoon(DISTANCES, steps=720, seed=8)[:, :, 0]

        for i in range(4):
            for j in range(i + 1, 4):
                assert not np.allclose(ph[i], ph[j]), (i, j)
