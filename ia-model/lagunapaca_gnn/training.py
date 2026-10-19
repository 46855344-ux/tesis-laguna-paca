"""Entrenamiento y evaluación del ST-GNN.

Aquí se juntan todas las piezas: el grafo de estaciones, las ventanas temporales,
el modelo y las métricas que definen el criterio de la tesis (``R² > 0.90``).

La decisión más importante del módulo es **cómo se parte la serie**. Una serie
temporal no se baraja: si el conjunto de prueba mezclara instantes del pasado y
del futuro, el modelo estaría viendo el futuro durante el entrenamiento y el R²
saldría alto por la razón equivocada. Por eso la partición es cronológica y,
además, deja un hueco entre ambos conjuntos: las ventanas se solapan, así que sin
hueco el último ejemplo de entrenamiento compartiría instantes con el primero de
prueba.
"""

from __future__ import annotations

import numpy as np
import torch
from torch import nn

from lagunapaca_gnn.metrics import (
    mean_absolute_error,
    r2_score,
    root_mean_squared_error,
)


def chronological_split(inputs, targets, train_fraction: float = 0.8,
                        gap: int = 0):
    """Parte la serie respetando el orden temporal.

    Args:
        inputs: ventanas de entrada, ``(muestras, ...)``.
        targets: objetivos alineados con las entradas.
        train_fraction: proporción inicial para entrenamiento.
        gap: muestras a descartar entre el final del entrenamiento y el inicio
            de la prueba. Conviene pasar el tamaño de la ventana de entrada
            para que ninguna ventana de prueba se solape con una de
            entrenamiento.

    Returns:
        ``(entradas_entreno, objetivos_entreno, entradas_prueba, objetivos_prueba)``.

    Raises:
        ValueError: si la fracción es inválida, si entradas y objetivos no
            coinciden en tamaño, o si el hueco deja la prueba vacía.
    """
    inputs = np.asarray(inputs)
    targets = np.asarray(targets)

    if not 0.0 < train_fraction < 1.0:
        raise ValueError(
            f"train_fraction debe estar entre 0 y 1, no {train_fraction}")
    if inputs.shape[0] != targets.shape[0]:
        raise ValueError(
            f"entradas y objetivos deben tener el mismo número de muestras, "
            f"no {inputs.shape[0]} y {targets.shape[0]}")

    total = inputs.shape[0]
    cut = int(total * train_fraction)
    if gap < 0:
        raise ValueError(f"el hueco no puede ser negativo, no {gap}")
    if cut <= 0 or cut + gap >= total:
        raise ValueError(
            f"el hueco de {gap} muestras deja el conjunto de prueba vacío "
            f"({total} muestras en total)")

    return (inputs[:cut], targets[:cut], inputs[cut + gap:], targets[cut + gap:])


def train_model(model: nn.Module, inputs, targets, edge_index,
                edge_weight: torch.Tensor | None = None, epochs: int = 100,
                batch_size: int = 64, learning_rate: float = 1e-2,
                seed: int = 0) -> list[float]:
    """Entrena el modelo con Adam y devuelve la pérdida media de cada época.

    Args:
        model: instancia de ``SpatioTemporalGNN``.
        inputs: ventanas de entrada ``(muestras, estaciones, tiempo, variables)``.
        targets: objetivos ``(muestras, estaciones, horizonte)``.
        edge_index: grafo en formato PyG.
        edge_weight: pesos de las aristas (opcional).
        epochs: número de pasadas completas por el conjunto.
        batch_size: muestras por paso de optimización.
        learning_rate: tasa de aprendizaje de Adam.
        seed: semilla para el orden de los lotes.

    Returns:
        Lista con la pérdida media de cada época. Sirve para comprobar que el
        modelo efectivamente aprende y no solo se mueve.

    Raises:
        ValueError: si el conjunto está vacío o entradas y objetivos no
            coinciden en tamaño.
    """
    x = torch.as_tensor(np.asarray(inputs), dtype=torch.float32)
    y = torch.as_tensor(np.asarray(targets), dtype=torch.float32)

    if x.shape[0] == 0:
        raise ValueError("no se puede entrenar con un conjunto vacío")
    if x.shape[0] != y.shape[0]:
        raise ValueError(
            f"entradas y objetivos deben tener el mismo número de muestras, "
            f"no {x.shape[0]} y {y.shape[0]}")

    torch.manual_seed(seed)
    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
    loss_function = nn.MSELoss()

    history: list[float] = []
    model.train()

    for _ in range(epochs):
        permutation = torch.randperm(x.shape[0])
        accumulated = 0.0

        for start in range(0, x.shape[0], batch_size):
            indices = permutation[start:start + batch_size]

            optimizer.zero_grad()
            predictions = model(x[indices], edge_index, edge_weight)
            loss = loss_function(predictions, y[indices])
            loss.backward()
            optimizer.step()

            accumulated += loss.item() * len(indices)

        history.append(accumulated / x.shape[0])

    return history


@torch.no_grad()
def predict(model: nn.Module, inputs, edge_index,
            edge_weight: torch.Tensor | None = None,
            batch_size: int = 128) -> np.ndarray:
    """Predice por lotes sin calcular gradientes.

    Deja el modelo en modo evaluación (sin dropout) y restaura el modo en que
    estaba al terminar.

    Raises:
        ValueError: si el conjunto está vacío.
    """
    x = torch.as_tensor(np.asarray(inputs), dtype=torch.float32)
    if x.shape[0] == 0:
        raise ValueError("no se puede predecir sobre un conjunto vacío")

    was_training = model.training
    model.eval()

    try:
        outputs = [
            model(x[start:start + batch_size], edge_index, edge_weight).cpu().numpy()
            for start in range(0, x.shape[0], batch_size)
        ]
    finally:
        model.train(was_training)

    return np.concatenate(outputs, axis=0)


def evaluate_model(model: nn.Module, inputs, targets, edge_index,
                   edge_weight: torch.Tensor | None = None,
                   batch_size: int = 128, scaler=None,
                   target_index: int = 0) -> dict[str, float]:
    """Evalúa el modelo con las tres métricas de la tesis.

    Args:
        scaler: si se pasó, las predicciones y los objetivos se devuelven a sus
            unidades originales **antes** de calcular las métricas. Así el RMSE
            se lee en unidades de pH y no en desviaciones típicas. El R² es
            invariante a la escala, pero un RMSE de "0.31 desviaciones" no le
            dice nada a nadie.
        target_index: variable objetivo dentro del escalador.

    Returns:
        ``{"r2": ..., "rmse": ..., "mae": ...}``. El R² se calcula con
        :func:`lagunapaca_gnn.metrics.r2_score`, la misma implementación que ya
        está probada por separado: el criterio de éxito no tiene dos versiones.
    """
    predictions = predict(model, inputs, edge_index, edge_weight, batch_size)
    truth = np.asarray(targets, dtype=float)

    if scaler is not None:
        predictions = scaler.inverse_targets(predictions, target_index)
        truth = scaler.inverse_targets(truth, target_index)

    return {
        "r2": r2_score(truth, predictions),
        "rmse": root_mean_squared_error(truth, predictions),
        "mae": mean_absolute_error(truth, predictions),
    }
