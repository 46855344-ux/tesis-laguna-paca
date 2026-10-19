"""Métricas de evaluación del modelo.

Criterio de éxito de la tesis: ``R2 > 0.90``. Se implementan aquí, sin depender
de scikit-learn, para que el criterio quede explícito y sea auditable: la forma
en que se calcula ``R2`` decide si un resultado cuenta como éxito o no.

Convenio heredado de scikit-learn para series constantes (varianza cero): si la
predicción es exacta vale ``1.0`` y en caso contrario ``0.0``. Devolver
``infinito`` o ``NaN`` obligaría a un tratamiento especial en el bucle de
entrenamiento.
"""

from __future__ import annotations

import numpy as np


def _as_arrays(y_true, y_pred) -> tuple[np.ndarray, np.ndarray]:
    """Valida que ambas entradas tengan la misma forma y no estén vacías."""
    true = np.asarray(y_true, dtype=float)
    pred = np.asarray(y_pred, dtype=float)

    if true.shape != pred.shape:
        raise ValueError(
            f"las formas deben coincidir, no {true.shape} y {pred.shape}")
    if true.size == 0:
        raise ValueError("los arreglos no pueden estar vacíos")
    return true, pred


def r2_score(y_true, y_pred) -> float:
    """Coeficiente de determinación ``R2 = 1 - SS_res / SS_tot``.

    Se calcula sobre todos los elementos a la vez, de modo que una única
    estación mal predicha no quede oculta por el promedio de las demás.
    """
    true, pred = _as_arrays(y_true, y_pred)

    residual_sum = float(np.sum((true - pred) ** 2))
    total_sum = float(np.sum((true - true.mean()) ** 2))

    if total_sum == 0.0:
        return 1.0 if residual_sum == 0.0 else 0.0
    return 1.0 - residual_sum / total_sum


def root_mean_squared_error(y_true, y_pred) -> float:
    """Raíz del error cuadrático medio. Penaliza los errores grandes aislados."""
    true, pred = _as_arrays(y_true, y_pred)
    return float(np.sqrt(np.mean((true - pred) ** 2)))


def mean_absolute_error(y_true, y_pred) -> float:
    """Error absoluto medio. Más robusto que el RMSE ante valores atípicos."""
    true, pred = _as_arrays(y_true, y_pred)
    return float(np.mean(np.abs(true - pred)))
