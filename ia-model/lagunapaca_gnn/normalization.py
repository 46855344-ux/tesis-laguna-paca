"""Estandarización de variables.

Las cuatro variables de la laguna viven en escalas incomparables: el pH ronda 7
con una desviación de 0.2, la temperatura ronda 18 con desviación de 2 y el TDS
ronda 320 con desviación de 15. Si se entregan así a la red, el TDS domina el
gradiente y el pH —que es justamente lo que hay que predecir— se vuelve
invisible. Medido en este proyecto: sin normalizar el R² era ``-0.02``; con
normalización, ``0.79`` en el mismo experimento.

La regla que no se puede romper: **la media y la desviación se calculan solo con
el conjunto de entrenamiento**. Si se calcularan con toda la serie, el conjunto
de prueba estaría filtrando información al escalado y el resultado dejaría de
ser una evaluación honesta.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np


@dataclass(frozen=True)
class StandardScaler:
    """Estandarización por variable: ``(x - media) / desviación``."""

    mean: np.ndarray
    std: np.ndarray

    @classmethod
    def fit(cls, inputs) -> "StandardScaler":
        """Calcula media y desviación por variable sobre ``inputs``.

        Se promedia sobre todas las dimensiones menos la última, que es la de
        variables, de modo que queda un valor por variable.

        Args:
            inputs: arreglo cuya última dimensión son las variables, por
                ejemplo ``(muestras, estaciones, tiempo, variables)``.

        Raises:
            ValueError: si el arreglo no tiene al menos dos dimensiones o está
                vacío.
        """
        array = np.asarray(inputs, dtype=float)
        if array.ndim < 2 or array.size == 0:
            raise ValueError(
                "se necesita un arreglo con al menos dos dimensiones y datos, "
                f"no de forma {array.shape}")

        axes = tuple(range(array.ndim - 1))
        mean = array.mean(axis=axes)
        std = array.std(axis=axes)
        # Una variable constante no se puede estandarizar: se deja en cero en
        # lugar de dividir por cero y producir NaN.
        std = np.where(std == 0.0, 1.0, std)
        return cls(mean=mean, std=std)

    def transform(self, inputs) -> np.ndarray:
        """Estandariza las variables de ``inputs``."""
        array = np.asarray(inputs, dtype=float)
        if array.shape[-1] != self.mean.shape[0]:
            raise ValueError(
                f"la última dimensión debe tener {self.mean.shape[0]} variables, "
                f"no {array.shape[-1]}")
        return (array - self.mean) / self.std

    def transform_targets(self, targets, target_index: int) -> np.ndarray:
        """Estandariza el objetivo con la media y desviación de su variable."""
        index = self._resolve(target_index)
        return (np.asarray(targets, dtype=float) - self.mean[index]) / self.std[index]

    def inverse_targets(self, values, target_index: int) -> np.ndarray:
        """Devuelve el objetivo a sus unidades originales (pH, NTU, °C, ppm)."""
        index = self._resolve(target_index)
        return np.asarray(values, dtype=float) * self.std[index] + self.mean[index]

    def _resolve(self, target_index: int) -> int:
        index = target_index if target_index >= 0 else target_index + self.mean.shape[0]
        if not 0 <= index < self.mean.shape[0]:
            raise ValueError(
                f"target_index fuera de rango: {target_index} "
                f"(solo hay {self.mean.shape[0]} variables)")
        return index
