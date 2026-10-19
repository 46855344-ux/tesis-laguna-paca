"""Tests de la construcción de ventanas temporales.

Requisito explícito de la tesis: el tensor de entrada de la ST-GNN es
[4 nodos, T tiempo, 4 variables]. Aquí se verifica la forma exacta y, sobre
todo, que no haya fuga de información: la ventana de predicción debe empezar
justo donde termina la de entrada.
"""

import numpy as np
import pytest

from lagunapaca_gnn.windows import build_windows

N_NODES = 4
N_VARS = 4  # pH, turbidez, temperatura y TDS


def make_series(n_time):
    """Serie sintética identificable: serie[:, t, v] = 100 * t + 10 * n + v."""
    nodes = np.arange(N_NODES).reshape(-1, 1, 1)
    times = np.arange(n_time).reshape(1, -1, 1)
    variables = np.arange(N_VARS).reshape(1, 1, -1)
    return (100 * times + 10 * nodes + variables).astype(float)


class TestBuildWindows:

    def test_la_entrada_tiene_la_forma_de_la_tesis(self):
        series = make_series(10)

        inputs, targets = build_windows(series, input_len=3, horizon=2, target_index=0)

        # [muestras, nodos, tiempo, variables]
        assert inputs.shape == (6, N_NODES, 3, N_VARS)
        assert targets.shape == (6, N_NODES, 2)

    def test_calcula_el_numero_de_muestras(self):
        series = make_series(10)

        inputs, _ = build_windows(series, input_len=5, horizon=1, target_index=0)

        assert inputs.shape[0] == 10 - 5 - 1 + 1

    def test_la_primera_ventana_empieza_en_el_primer_instante(self):
        series = make_series(10)

        inputs, _ = build_windows(series, input_len=3, horizon=2, target_index=0)

        assert np.allclose(inputs[0], series[:, 0:3, :])

    def test_no_hay_fuga_de_informacion(self):
        series = make_series(10)

        inputs, targets = build_windows(series, input_len=3, horizon=2, target_index=0)

        # El objetivo empieza justo despues de la ventana de entrada.
        assert np.allclose(targets[0], series[:, 3:5, 0])
        # Y el ultimo instante de la entrada es anterior al primero del objetivo.
        assert inputs[0][0, -1, 0] < targets[0][0, 0]

    def test_las_ventanas_son_consecutivas(self):
        series = make_series(10)

        inputs, _ = build_windows(series, input_len=3, horizon=2, target_index=0)

        assert np.allclose(inputs[1], series[:, 1:4, :])
        assert np.allclose(inputs[5], series[:, 5:8, :])

    def test_solo_la_variable_objetivo_es_el_objetivo(self):
        series = make_series(10)

        inputs, targets = build_windows(series, input_len=3, horizon=2, target_index=2)

        assert np.allclose(targets[0], series[:, 3:5, 2])
        # La turbidez (indice 1) no debe aparecer en el objetivo.
        assert not np.allclose(targets[0], series[:, 3:5, 1])
        assert inputs.shape[3] == N_VARS

    def test_acepta_un_indice_de_objetivo_negativo(self):
        series = make_series(10)

        _, ultimo = build_windows(series, input_len=3, horizon=2, target_index=-1)

        assert np.allclose(ultimo[0], series[:, 3:5, N_VARS - 1])

    def test_rechaza_series_demasiado_cortas(self):
        series = make_series(4)

        with pytest.raises(ValueError, match="corta"):
            build_windows(series, input_len=3, horizon=2, target_index=0)

    def test_rechaza_series_que_no_sean_tridimensionales(self):
        with pytest.raises(ValueError, match="3 dimensiones"):
            build_windows(np.zeros((4, 10)), input_len=3, horizon=2, target_index=0)

    def test_rechaza_longitudes_no_positivas(self):
        series = make_series(10)

        with pytest.raises(ValueError, match="input_len"):
            build_windows(series, input_len=0, horizon=1, target_index=0)
        with pytest.raises(ValueError, match="horizon"):
            build_windows(series, input_len=3, horizon=0, target_index=0)

    def test_rechaza_un_indice_de_objetivo_fuera_de_rango(self):
        series = make_series(10)

        with pytest.raises(ValueError, match="target_index"):
            build_windows(series, input_len=3, horizon=2, target_index=N_VARS)
