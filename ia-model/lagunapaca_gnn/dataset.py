"""Generador de series sintéticas con dinámica temporal y espacial.

¿Por qué no reutilizar el simulador del backend? Porque
``backend/tools/simulate_node.py`` genera **muestras independientes**: cada valor
es un sorteo nuevo. Sirve para probar la ingesta y las alertas, pero no para
entrenar un modelo temporal, porque entre un instante y el siguiente no hay nada
que aprender.

Aquí se genera lo contrario, con las tres propiedades que la ST-GNN necesita
para tener algo que modelar:

1. **Memoria temporal** — cada variable sigue un proceso AR(1), de modo que dos
   instantes consecutivos se parecen mucho. Es lo que el TCN puede explotar.
2. **Ciclo diario** — la temperatura (y en menor medida el resto) sube y baja
   con el día.
3. **Correlación espacial** — cada estación mezcla su propio proceso con el de
   sus vecinas, ponderando por la adyacencia gaussiana. Es lo que el GCN puede
   explotar: sin esta propiedad, el grafo sería decorativo.

.. warning::

   Estos datos **no son mediciones de la laguna**. Son una simulación cuya
   dinámica fue elegida por nosotros. Un ``R² > 0.90`` obtenido aquí demuestra
   que la arquitectura aprende una dinámica espacio-temporal, **no** que prediga
   la laguna real. Para eso harán falta datos de campo.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from lagunapaca_gnn.graph import gaussian_adjacency

#: Nombres de las variables, idénticos a las claves del payload del ESP32.
VARIABLE_NAMES: tuple[str, ...] = ("ph", "turbidity_ntu", "temp_c", "tds_ppm")

#: Rango físico admisible por variable ``(mínimo, máximo)``.
#: El pH admite valores fuera del ECA-Agua a propósito: la laguna puede tener
#: episodios reales de acidez o alcalinidad, y el modelo debe verlos.
PHYSICAL_LIMITS = np.array([
    [6.0, 9.5],      # pH
    [0.0, 60.0],     # turbidez (NTU)
    [0.0, 30.0],     # temperatura (°C)
    [0.0, 1000.0],   # sólidos disueltos totales (ppm)
])

#: Persistencia del componente regional (común a toda la laguna).
#: Elegida corta a propósito: con una memoria más larga (0.998) el nivel de la
#: laguna deriva y el último cuarto de la serie cae en estados que el modelo
#: nunca vio, lo que arruina la generalización de una partición cronológica.
PHI_REGIONAL = 0.99

#: Persistencia del componente propio de cada estación.
PHI_LOCAL = 0.99


@dataclass(frozen=True)
class VariableProfile:
    """Parámetros de simulación de una variable.

    Las desviaciones son las del proceso **estacionario** (no las del
    incremento), para que los valores sean interpretables a simple vista.
    """

    base: float
    regional_std: float
    local_std: float
    diurnal: float
    noise: float


#: Perfiles alineados con el payload de ejemplo del ESP32
#: (``ph 7.2, turbidity_ntu 15.4, temp_c 18.5, tds_ppm 320``).
#:
#: El pH lleva **ciclo diario marcado** (0.15) porque en un lago eutrófico el
#: pH sube de día por la fotosíntesis del fitoplancton y baja de noche por la
#: respiración. No es un adorno: es lo que hace que la predicción a varias horas
#: sea un problema real y no una simple extrapolación del último valor.
PROFILES: tuple[VariableProfile, ...] = (
    VariableProfile(base=7.2, regional_std=0.08, local_std=0.25, diurnal=0.15, noise=0.01),
    VariableProfile(base=15.4, regional_std=1.0, local_std=3.0, diurnal=0.6, noise=0.35),
    VariableProfile(base=18.5, regional_std=0.8, local_std=0.5, diurnal=3.0, noise=0.15),
    VariableProfile(base=320.0, regional_std=8.0, local_std=20.0, diurnal=2.0, noise=3.0),
)


def _ar1_process(rng: np.random.Generator, steps: int, phi: float, std: float,
                 node_count: int | None = None) -> np.ndarray:
    """Proceso AR(1) estacionario de desviación típica ``std``.

    Con ``node_count`` genera un proceso independiente por estación; sin él,
    un único proceso compartido por toda la laguna.
    """
    innovation = std * np.sqrt(1.0 - phi ** 2)

    if node_count is None:
        values = np.empty(steps)
        values[0] = rng.normal(0.0, std)
        for step in range(1, steps):
            values[step] = phi * values[step - 1] + rng.normal(0.0, innovation)
        return values

    values = np.empty((node_count, steps))
    values[:, 0] = rng.normal(0.0, std, node_count)
    for step in range(1, steps):
        values[:, step] = phi * values[:, step - 1] + rng.normal(0.0, innovation, node_count)
    return values


def simulate_lagoon(distances, steps: int = 720, seed: int = 0, sigma: float = 1.5,
                    step_hours: float = 1.0, mixing: float = 0.45) -> np.ndarray:
    """Genera una serie sintética ``(estaciones, tiempo, variables)``.

    Args:
        distances: matriz ``(n, n)`` de distancias en km entre estaciones.
        steps: número de instantes a generar.
        seed: semilla; la misma semilla produce exactamente la misma serie.
        sigma: ancho del kernel gaussiano que pondera a las vecinas.
        step_hours: horas entre instantes (1.0 = muestreo horario).
        mixing: cuánto pesa el promedio de las vecinas frente al proceso propio
            de cada estación. Con ``0`` no hay correlación espacial.

    Raises:
        ValueError: si la matriz de distancias no es cuadrada o ``steps`` no es
            positivo.
    """
    matrix = np.asarray(distances, dtype=float)
    if matrix.ndim != 2 or matrix.shape[0] != matrix.shape[1]:
        raise ValueError(f"la matriz de distancias debe ser cuadrada, no de forma {matrix.shape}")
    if steps <= 0:
        raise ValueError(f"steps debe ser positivo, no {steps}")

    adjacency = gaussian_adjacency(matrix, sigma)
    node_count = adjacency.shape[0]

    row_sums = adjacency.sum(axis=1, keepdims=True)
    # Una estación aislada tiene fila nula: se queda con su propio proceso en
    # vez de dividir por cero.
    weights = np.divide(adjacency, row_sums,
                        out=np.zeros_like(adjacency), where=row_sums > 0)

    hours = np.arange(steps) * step_hours
    # Máximo a las 15:00, mínimo a las 03:00: el desfase importa.
    diurnal = np.cos(2.0 * np.pi * (hours - 15.0) / 24.0)

    rng = np.random.default_rng(seed)
    series = np.empty((node_count, steps, len(VARIABLE_NAMES)))

    for index, profile in enumerate(PROFILES):
        regional = _ar1_process(rng, steps, PHI_REGIONAL, profile.regional_std)
        own = _ar1_process(rng, steps, PHI_LOCAL, profile.local_std, node_count)
        local = (1.0 - mixing) * own + mixing * (weights @ own)

        values = (profile.base
                  + regional[None, :]
                  + local
                  + profile.diurnal * diurnal[None, :])
        values = values + rng.normal(0.0, profile.noise, values.shape)

        low, high = PHYSICAL_LIMITS[index]
        series[:, :, index] = np.clip(values, low, high)

    return series
