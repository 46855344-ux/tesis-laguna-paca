"""Ventanas temporales: la entrada de la ST-GNN.

Requisito explícito de la tesis: el tensor de entrada tiene la forma
``[4 nodos, T tiempo, 4 variables]`` (pH, turbidez, temperatura y TDS).

La función clave es :func:`build_windows`, que corta una serie temporal en
ventanas deslizantes de entrada y su correspondiente ventana de predicción. La
ventana de predicción empieza **justo después** de la de entrada: esa
propiedad es lo que evita la fuga de información (el modelo no puede ver el
futuro que debe predecir).
"""

from __future__ import annotations

import numpy as np


def build_windows(series, input_len: int, horizon: int,
                  target_index: int) -> tuple[np.ndarray, np.ndarray]:
    """Corta la serie en ventanas de entrada y objetivos desplazados.

    Args:
        series: arreglo ``(nodos, tiempo, variables)``.
        input_len: instantes de pasado que ve el modelo (la ``T`` de la tesis).
        horizon: instantes futuros a predecir.
        target_index: índice de la variable objetivo *dentro* del eje de
            variables. Admite índices negativos, como numpy.

    Returns:
        ``(entradas, objetivos)`` donde las entradas tienen forma
        ``(muestras, nodos, input_len, variables)`` y los objetivos
        ``(muestras, nodos, horizon)``, es decir una predicción por estación.

    Raises:
        ValueError: si la serie no es tridimensional, si ``input_len`` o
            ``horizon`` no son positivos, si la serie es más corta que la
            ventana, o si ``target_index`` está fuera de rango.
    """
    array = np.asarray(series, dtype=float)
    if array.ndim != 3:
        raise ValueError(
            "la serie debe tener 3 dimensiones [nodos, tiempo, variables], "
            f"no {array.ndim}")
    if input_len <= 0:
        raise ValueError(f"input_len debe ser positivo, no {input_len}")
    if horizon <= 0:
        raise ValueError(f"horizon debe ser positivo, no {horizon}")

    node_count, time_steps, variable_count = array.shape

    if target_index < 0:
        target_index += variable_count
    if not 0 <= target_index < variable_count:
        raise ValueError(
            f"target_index fuera de rango: {target_index} "
            f"(solo hay {variable_count} variables)")

    needed = input_len + horizon
    if time_steps < needed:
        raise ValueError(
            f"la serie es demasiado corta: {time_steps} instantes para una "
            f"ventana de {input_len} + un horizonte de {horizon} ({needed})")

    sample_count = time_steps - needed + 1
    inputs = np.empty((sample_count, node_count, input_len, variable_count))
    targets = np.empty((sample_count, node_count, horizon))

    for sample in range(sample_count):
        start = sample
        end = sample + input_len
        inputs[sample] = array[:, start:end, :]
        targets[sample] = array[:, end:end + horizon, target_index]

    return inputs, targets
