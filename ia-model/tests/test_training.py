"""Tests del bucle de entrenamiento y de la evaluación.

Entrenar es donde el proyecto se juega su criterio: ``R² > 0.90``. Estos tests no
comprueban que el modelo sea "bueno" en abstracto, sino que el procedimiento sea
correcto: que la partición respete el orden temporal, que la pérdida baje, que la
evaluación use exactamente las mismas métricas que ya están probadas en
:mod:`tests.test_metrics`, y que el criterio se alcance de verdad.
"""

import numpy as np
import pytest
import torch

from lagunapaca_gnn.dataset import simulate_lagoon
from lagunapaca_gnn.graph import adjacency_to_edge_index, gaussian_adjacency, sparsify
from lagunapaca_gnn.metrics import r2_score
from lagunapaca_gnn.model import SpatioTemporalGNN
from lagunapaca_gnn.normalization import StandardScaler
from lagunapaca_gnn.stations import PROVISIONAL_STATIONS, distance_matrix_km
from lagunapaca_gnn.training import (
    chronological_split,
    evaluate_model,
    predict,
    train_model,
)
from lagunapaca_gnn.windows import build_windows

DISTANCES = distance_matrix_km(PROVISIONAL_STATIONS)


def build_graph(k=2, sigma=1.5):
    """Grafo real de la laguna en el formato de PyG."""
    adjacency = gaussian_adjacency(DISTANCES, sigma=sigma)
    sparse = sparsify(adjacency, DISTANCES, k=k)
    return adjacency_to_edge_index(sparse)


def build_dataset(steps=720, input_len=24, horizon=1, seed=42):
    series = simulate_lagoon(DISTANCES, steps=steps, seed=seed)
    return build_windows(series, input_len=input_len, horizon=horizon, target_index=0)


class TestChronologicalSplit:
    """Una serie temporal no se baraja: el futuro nunca puede ir al entrenamiento."""

    def test_respeta_el_orden_temporal(self):
        inputs = np.arange(20, dtype=float).reshape(20, 1)

        train_x, _, test_x, _ = chronological_split(
            inputs, inputs, train_fraction=0.8)

        assert len(train_x) == 16
        assert len(test_x) == 4
        assert train_x[-1, 0] < test_x[0, 0]

    def test_el_hueco_deja_aire_entre_entrenamiento_y_prueba(self):
        # Las ventanas se solapan; sin hueco, el último ejemplo de entrenamiento
        # compartiría instantes con el primero de prueba.
        inputs = np.arange(20, dtype=float).reshape(20, 1)

        train_x, _, test_x, _ = chronological_split(
            inputs, inputs, train_fraction=0.8, gap=3)

        assert len(test_x) == 1
        assert test_x[0, 0] - train_x[-1, 0] == 4

    def test_devuelve_entradas_y_objetivos_alineados(self):
        inputs = np.arange(20, dtype=float).reshape(20, 1)
        targets = inputs * 10

        train_x, train_y, test_x, test_y = chronological_split(
            inputs, targets, train_fraction=0.8)

        assert np.array_equal(train_y, train_x * 10)
        assert np.array_equal(test_y, test_x * 10)

    def test_rechaza_una_fraccion_invalida(self):
        inputs = np.zeros((10, 1))

        with pytest.raises(ValueError, match="train_fraction"):
            chronological_split(inputs, inputs, train_fraction=1.5)

    def test_rechaza_un_hueco_que_se_come_la_prueba(self):
        inputs = np.zeros((10, 1))

        with pytest.raises(ValueError, match="hueco"):
            chronological_split(inputs, inputs, train_fraction=0.9, gap=5)


class TestTrainModel:

    def test_la_perdida_disminuye(self):
        edge_index, edge_weight = build_graph()
        inputs, targets = build_dataset(steps=240, input_len=12, seed=11)
        model = SpatioTemporalGNN(
            num_variables=4, horizon=1, hidden=8, tcn_levels=2, heads=2, dropout=0.0)

        history = train_model(
            model, inputs, targets, edge_index, edge_weight,
            epochs=15, batch_size=32, learning_rate=0.01, seed=0)

        assert len(history) == 15
        assert history[-1] < history[0]

    def test_es_reproducible_con_la_misma_semilla(self):
        edge_index, edge_weight = build_graph()
        inputs, targets = build_dataset(steps=240, input_len=12, seed=12)

        losses = []
        for _ in range(2):
            # La inicialización de los pesos también es aleatoria: se fija, o
            # los dos entrenamientos partirían de modelos distintos.
            torch.manual_seed(123)
            model = SpatioTemporalGNN(
                num_variables=4, horizon=1, hidden=8, heads=2, dropout=0.0)
            losses.append(train_model(
                model, inputs, targets, edge_index, edge_weight,
                epochs=3, batch_size=32, learning_rate=0.01, seed=5))

        assert losses[0] == pytest.approx(losses[1], rel=1e-6)


class TestPredictAndEvaluate:

    def test_las_predicciones_tienen_la_forma_del_objetivo(self):
        edge_index, edge_weight = build_graph()
        inputs, targets = build_dataset(steps=240, input_len=12, horizon=2, seed=13)
        model = SpatioTemporalGNN(
            num_variables=4, horizon=2, hidden=8, heads=2, dropout=0.0)

        predictions = predict(model, inputs, edge_index, edge_weight)

        assert predictions.shape == targets.shape

    def test_la_evaluacion_usa_las_metricas_ya_probadas(self):
        edge_index, edge_weight = build_graph()
        inputs, targets = build_dataset(steps=240, input_len=12, seed=14)
        model = SpatioTemporalGNN(num_variables=4, horizon=1, hidden=8, heads=2, dropout=0.0)

        metrics = evaluate_model(model, inputs, targets, edge_index, edge_weight)
        predictions = predict(model, inputs, edge_index, edge_weight)

        assert set(metrics) == {"r2", "rmse", "mae"}
        assert metrics["r2"] == pytest.approx(r2_score(targets, predictions))
        assert np.isfinite(list(metrics.values())).all()

    def test_rechaza_un_conjunto_vacio(self):
        edge_index, _ = build_graph()
        model = SpatioTemporalGNN(num_variables=4, horizon=1, hidden=8, heads=2, dropout=0.0)

        with pytest.raises(ValueError, match="vac"):
            predict(model, np.zeros((0, 4, 12, 4)), edge_index)


class TestCriterioDeLaTesis:
    """Lo que la tesis exige: R² > 0.90. Con datos sintéticos: valida la arquitectura."""

    def test_alcanza_r2_mayor_a_090_y_supera_la_persistencia(self):
        edge_index, edge_weight = build_graph()
        inputs, targets = build_dataset(steps=2000, input_len=24, horizon=6, seed=42)
        train_x, train_y, test_x, test_y = chronological_split(
            inputs, targets, train_fraction=0.75, gap=24)

        # La media y la desviacion se calculan SOLO con entrenamiento.
        scaler = StandardScaler.fit(train_x)

        model = SpatioTemporalGNN(
            num_variables=4, horizon=6, hidden=24, tcn_levels=2,
            gcn_layers=1, heads=4, dropout=0.1, target_index=0)
        train_model(
            model,
            scaler.transform(train_x), scaler.transform_targets(train_y, 0),
            edge_index, edge_weight,
            epochs=150, batch_size=64, learning_rate=0.01, seed=0)

        metrics = evaluate_model(
            model,
            scaler.transform(test_x), scaler.transform_targets(test_y, 0),
            edge_index, edge_weight, scaler=scaler, target_index=0)

        assert metrics["r2"] > 0.90, metrics

        # Y no basta con superar el umbral: tiene que batir la linea base
        # trivial. Si no, el modelo no aporta nada que no aporte "copiar el
        # ultimo valor observado".
        persistencia = np.repeat(test_x[:, :, -1, 0][:, :, None], 6, axis=2)
        assert metrics["r2"] > r2_score(test_y, persistencia), metrics
