"""Tests de las métricas de evaluación.

Criterio de éxito de la tesis: R2 > 0.90. Ese número decide si el modelo sirve,
así que la métrica debe estar bien definida y probada, incluidos los casos
límite que suelen colarse silenciosamente.
"""

import numpy as np
import pytest

from lagunapaca_gnn.metrics import mean_absolute_error, r2_score, root_mean_squared_error


class TestR2Score:

    def test_una_prediccion_perfecta_vale_uno(self):
        y = np.array([1.0, 2.0, 3.0, 4.0])

        assert r2_score(y, y) == pytest.approx(1.0)

    def test_predecir_la_media_vale_cero(self):
        y = np.array([1.0, 2.0, 3.0, 4.0])
        media = np.full_like(y, y.mean())

        assert r2_score(y, media) == pytest.approx(0.0)

    def test_peor_que_la_media_da_negativo(self):
        y = np.array([1.0, 2.0, 3.0, 4.0])
        peor = np.array([4.0, 4.0, 4.0, 4.0])

        assert r2_score(y, peor) < 0.0

    def test_calcula_el_valor_conocido(self):
        # SS_res = 0^2 + 1^2 = 1 ; SS_tot = 1 + 0 + 1 = 2 -> R2 = 0.5
        y = np.array([1.0, 2.0, 3.0])
        pred = np.array([1.0, 3.0, 3.0])

        assert r2_score(y, pred) == pytest.approx(0.5)

    def test_acepta_arreglos_multidimensionales(self):
        y = np.array([[1.0, 2.0], [3.0, 4.0]])

        assert r2_score(y, y) == pytest.approx(1.0)

    def test_serie_constante_bien_predicha_vale_uno(self):
        # Misma convencion que scikit-learn: no se devuelve infinito.
        y = np.array([5.0, 5.0, 5.0])

        assert r2_score(y, y) == pytest.approx(1.0)

    def test_serie_constante_mal_predicha_vale_cero(self):
        y = np.array([5.0, 5.0, 5.0])
        mal = np.array([5.0, 6.0, 5.0])

        assert r2_score(y, mal) == pytest.approx(0.0)

    def test_rechaza_formas_distintas(self):
        with pytest.raises(ValueError, match="forma"):
            r2_score(np.array([1.0, 2.0]), np.array([1.0, 2.0, 3.0]))

    def test_rechaza_arreglos_vacios(self):
        with pytest.raises(ValueError, match="vac"):
            r2_score(np.array([]), np.array([]))


class TestRmse:

    def test_calcula_la_raiz_del_error_cuadratico_medio(self):
        y = np.array([1.0, 2.0, 3.0])
        pred = np.array([1.0, 2.0, 5.0])  # error 0, 0, 2 -> raiz(4/3)

        assert root_mean_squared_error(y, pred) == pytest.approx(np.sqrt(4.0 / 3.0))

    def test_una_prediccion_perfecta_vale_cero(self):
        y = np.array([1.0, 2.0, 3.0])

        assert root_mean_squared_error(y, y) == 0.0

    def test_penaliza_mas_los_errores_grandes_aislados(self):
        y = np.zeros(4)
        repartido = np.array([1.0, 0.0, -1.0, 0.0])   # dos errores de 1
        concentrado = np.array([2.0, 0.0, 0.0, 0.0])  # un unico error de 2

        # Mismo error absoluto medio (0.5)...
        assert mean_absolute_error(y, repartido) == pytest.approx(
            mean_absolute_error(y, concentrado))
        # ...pero el RMSE castiga mas el error concentrado.
        assert root_mean_squared_error(y, concentrado) > root_mean_squared_error(y, repartido)


class TestMae:

    def test_calcula_el_error_absoluto_medio(self):
        y = np.array([1.0, 2.0, 3.0])
        pred = np.array([2.0, 2.0, 5.0])  # errores 1, 0, 2 -> 1.0

        assert mean_absolute_error(y, pred) == pytest.approx(1.0)

    def test_una_prediccion_perfecta_vale_cero(self):
        y = np.array([1.0, 2.0, 3.0])

        assert mean_absolute_error(y, y) == 0.0

    def test_es_robusta_a_un_valor_atipico(self):
        y = np.zeros(4)
        con_atipico = np.array([10.0, 0.0, 0.0, 0.0])

        assert mean_absolute_error(y, con_atipico) == pytest.approx(2.5)
