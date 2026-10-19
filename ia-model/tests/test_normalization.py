"""Tests de la estandarización de variables.

Se prueban dos cosas distintas: que la aritmética sea correcta y que **el
escalado se ajuste solo con el conjunto de entrenamiento**. Lo segundo no es
estético: si la media se calculara con toda la serie, el conjunto de prueba
estaría aportando información al preprocesamiento y el R² dejaría de ser una
medida honesta.
"""

import numpy as np
import pytest

from lagunapaca_gnn.normalization import StandardScaler

# (muestras, estaciones, tiempo, variables)
INPUTS = np.array([
    [[[1.0, 10.0, 100.0],
      [3.0, 20.0, 300.0]]],
    [[[5.0, 30.0, 500.0],
      [7.0, 40.0, 700.0]]],
])


class TestFit:

    def test_calcula_una_media_por_variable(self):
        scaler = StandardScaler.fit(INPUTS)

        assert np.allclose(scaler.mean, [4.0, 25.0, 400.0])

    def test_calcula_una_desviacion_por_variable(self):
        scaler = StandardScaler.fit(INPUTS)

        assert scaler.std.shape == (3,)
        assert np.allclose(scaler.std, INPUTS.std(axis=(0, 1, 2)))

    def test_una_variable_constante_no_divide_por_cero(self):
        constantes = np.ones((2, 2, 2, 3))

        scaler = StandardScaler.fit(constantes)

        assert np.allclose(scaler.std, 1.0)
        assert np.allclose(scaler.transform(constantes), 0.0)

    def test_rechaza_un_arreglo_unidimensional(self):
        with pytest.raises(ValueError, match="dimensiones"):
            StandardScaler.fit(np.array([1.0, 2.0, 3.0]))

    def test_rechaza_un_arreglo_vacio(self):
        with pytest.raises(ValueError, match="dimensiones|datos"):
            StandardScaler.fit(np.zeros((0, 4)))


class TestTransform:

    def test_deja_cada_variable_con_media_cero_y_desviacion_uno(self):
        scaler = StandardScaler.fit(INPUTS)

        escalado = scaler.transform(INPUTS)

        assert np.allclose(escalado.mean(axis=(0, 1, 2)), 0.0)
        assert np.allclose(escalado.std(axis=(0, 1, 2)), 1.0)

    def test_no_se_reajusta_con_los_datos_nuevos(self):
        # El conjunto de prueba puede tener otra media; el escalado debe
        # conservar esa diferencia, no "corregirla" con estadisticos ajenos.
        scaler = StandardScaler.fit(INPUTS)
        desplazado = INPUTS + np.array([0.0, 0.0, 10.0])

        escalado = scaler.transform(desplazado)

        assert np.allclose(escalado[:, :, :, 2].mean(), 10.0 / scaler.std[2])

    def test_rechaza_un_numero_de_variables_distinto(self):
        scaler = StandardScaler.fit(INPUTS)

        with pytest.raises(ValueError, match="variables"):
            scaler.transform(np.zeros((2, 2, 2, 5)))


class TestObjetivo:

    def test_ida_y_vuelta_devuelve_los_valores_originales(self):
        scaler = StandardScaler.fit(INPUTS)

        escalado = scaler.transform_targets(INPUTS[:, :, :, 1], target_index=1)
        recuperado = scaler.inverse_targets(escalado, target_index=1)

        assert np.allclose(recuperado, INPUTS[:, :, :, 1])

    def test_el_objetivo_escalado_tiene_media_cero(self):
        scaler = StandardScaler.fit(INPUTS)

        escalado = scaler.transform_targets(INPUTS[:, :, :, 0], target_index=0)

        assert escalado.mean() == pytest.approx(0.0)

    def test_acepta_un_indice_negativo(self):
        scaler = StandardScaler.fit(INPUTS)

        ultimo = scaler.transform_targets(INPUTS[:, :, :, -1], target_index=-1)
        explicito = scaler.transform_targets(INPUTS[:, :, :, 2], target_index=2)

        assert np.allclose(ultimo, explicito)

    def test_rechaza_un_indice_fuera_de_rango(self):
        scaler = StandardScaler.fit(INPUTS)

        with pytest.raises(ValueError, match="target_index"):
            scaler.transform_targets(INPUTS[:, :, :, 0], target_index=7)
