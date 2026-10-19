"""Modelo ST-GNN para el monitoreo de la contaminación de la Laguna de Paca.

Estructura del paquete:

- :mod:`lagunapaca_gnn.stations`: las 4 estaciones de muestreo y la matriz de
  distancias en kilómetros entre ellas.
- :mod:`lagunapaca_gnn.graph`: grafo de estaciones (adyacencia gaussiana y poda
  por árbol de expansión mínima + k vecinos más cercanos).
- :mod:`lagunapaca_gnn.windows`: construcción del tensor de entrada
  ``[nodos, tiempo, variables]``.
- :mod:`lagunapaca_gnn.metrics`: métricas de evaluación (R2, RMSE y MAE).
- :mod:`lagunapaca_gnn.model`: el ST-GNN propiamente dicho (TCN + GCN +
  Attention). Se importa **explícitamente** como ``lagunapaca_gnn.model`` y no
  se reexporta aquí a propósito: trae PyTorch, y cargarlo en el `import` del
  paquete haría lentos hasta los tests que solo necesitan numpy.
"""

from lagunapaca_gnn.dataset import VARIABLE_NAMES, simulate_lagoon
from lagunapaca_gnn.graph import (
    adjacency_to_edge_index,
    gaussian_adjacency,
    knn_neighbors,
    minimum_spanning_tree,
    sparsify,
)
from lagunapaca_gnn.metrics import (
    mean_absolute_error,
    r2_score,
    root_mean_squared_error,
)
from lagunapaca_gnn.normalization import StandardScaler
from lagunapaca_gnn.stations import (
    PROVISIONAL_STATIONS,
    Station,
    distance_matrix_km,
    haversine_km,
)
from lagunapaca_gnn.windows import build_windows

__all__ = [
    "PROVISIONAL_STATIONS",
    "StandardScaler",
    "Station",
    "VARIABLE_NAMES",
    "adjacency_to_edge_index",
    "build_windows",
    "distance_matrix_km",
    "gaussian_adjacency",
    "haversine_km",
    "knn_neighbors",
    "mean_absolute_error",
    "minimum_spanning_tree",
    "r2_score",
    "root_mean_squared_error",
    "simulate_lagoon",
    "sparsify",
]
