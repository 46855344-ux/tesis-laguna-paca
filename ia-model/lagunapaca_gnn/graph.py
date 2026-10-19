"""Grafo de estaciones: adyacencia gaussiana y poda por MST + KNN.

La ST-GNN necesita saber qué estaciones son "vecinas". Aquí se construye esa
relación en dos pasos, tal como especifica la tesis:

1. **Kernel gaussiano**: ``A_ij = exp(-d_ij^2 / (2 * sigma^2))``. Cuanto más
   cerca están dos estaciones (en kilómetros), más peso tiene su arista. Es una
   medida de similitud espacial, no un umbral duro.
2. **Poda (MST ∪ KNN)**: la adyacencia gaussiana es densa y con valores muy
   pequeños que aportan ruido. Se conservan solo las aristas del árbol de
   expansión mínima y las de los ``k`` vecinos más cercanos de cada estación.
   El MST es lo que garantiza que el grafo **nunca quede desconectado**: sin él,
   una estación sin vecinos cercanos quedaría aislada y no recibiría información
   del resto de la laguna.

El módulo es puro (entra una matriz de distancias, sale una matriz de pesos) y
no conoce ni los nombres ni las coordenadas de las estaciones.
"""

from __future__ import annotations

from itertools import combinations

import numpy as np

#: Arista no dirigida, siempre normalizada como ``(nodo_menor, nodo_mayor)``.
Edge = tuple[int, int]


def _as_square(distances) -> np.ndarray:
    """Valida y normaliza la matriz de distancias a un arreglo cuadrado de float."""
    matrix = np.asarray(distances, dtype=float)
    if matrix.ndim != 2 or matrix.shape[0] != matrix.shape[1]:
        raise ValueError(
            f"la matriz de distancias debe ser cuadrada, no de forma {matrix.shape}")
    if matrix.size == 0:
        raise ValueError("la matriz de distancias no puede estar vacía")
    if np.any(matrix < 0):
        raise ValueError("la distancia entre dos estaciones no puede ser negativa")
    return matrix


def gaussian_adjacency(distances, sigma: float) -> np.ndarray:
    """Pesos de similitud entre estaciones, con kernel gaussiano.

    Args:
        distances: matriz ``(n, n)`` de distancias en kilómetros.
        sigma: ancho del kernel, en kilómetros. Valores pequeños hacen que solo
            las estaciones muy próximas influyan entre sí; valores grandes
            acercan el grafo a uno completamente conectado.

    Returns:
        Matriz ``(n, n)`` simétrica, con la diagonal a cero (una estación no es
        vecina de sí misma).

    Raises:
        ValueError: si la matriz no es cuadrada, hay distancias negativas o
            ``sigma`` no es positivo.
    """
    matrix = _as_square(distances)
    if sigma <= 0:
        raise ValueError(f"sigma debe ser positivo, no {sigma}")

    weights = np.exp(-(matrix ** 2) / (2.0 * sigma ** 2))
    np.fill_diagonal(weights, 0.0)
    return weights


def minimum_spanning_tree(distances) -> list[Edge]:
    """Árbol de expansión mínima por el algoritmo de Kruskal.

    Es la garantía de conectividad del grafo: conecta las ``n`` estaciones con
    exactamente ``n - 1`` aristas, eligiendo siempre la de menor distancia que
    no cierre un ciclo.

    Returns:
        Aristas ``(i, j)`` con ``i < j``, ordenadas. El orden es determinista
        para que dos ejecuciones produzcan el mismo grafo.
    """
    matrix = _as_square(distances)
    node_count = matrix.shape[0]

    parent = list(range(node_count))

    def find(node: int) -> int:
        # Compresión de caminos para no degenerar en una lista enlazada.
        while parent[node] != node:
            parent[node] = parent[parent[node]]
            node = parent[node]
        return node

    candidates = sorted(
        ((matrix[i, j], i, j) for i, j in combinations(range(node_count), 2)),
        key=lambda edge: (edge[0], edge[1], edge[2]),
    )

    tree: list[Edge] = []
    for _, i, j in candidates:
        root_i, root_j = find(i), find(j)
        if root_i != root_j:
            parent[root_i] = root_j
            tree.append((i, j))
            if len(tree) == node_count - 1:
                break
    return sorted(tree)


def knn_neighbors(distances, k: int) -> set[Edge]:
    """Aristas de los ``k`` vecinos más cercanos de cada estación.

    A diferencia del MST, aquí una arista se conserva si **cualquiera** de sus
    dos extremos la considera cercana (unión simétrica). Así una estación con
    muchos vecinos no pierde la relación que otra estación sí valora.

    Raises:
        ValueError: si ``k`` es menor que 1 o mayor o igual que el número de
            estaciones (con ``k = n`` el grafo sería trivialmente completo).
    """
    matrix = _as_square(distances)
    node_count = matrix.shape[0]

    if k < 1:
        raise ValueError(f"k debe ser al menos 1, no {k}")
    if k >= node_count:
        raise ValueError(
            f"k debe ser menor que el número de estaciones ({node_count}), no {k}")

    edges: set[Edge] = set()
    for node in range(node_count):
        others = [other for other in range(node_count) if other != node]
        others.sort(key=lambda other: (matrix[node, other], other))
        for other in others[:k]:
            edges.add((min(node, other), max(node, other)))
    return edges


def sparsify(adjacency, distances, k: int) -> np.ndarray:
    """Poda la adyacencia gaussiana conservando el MST y los KNN.

    Args:
        adjacency: matriz de pesos ``(n, n)``, típicamente el resultado de
            :func:`gaussian_adjacency`.
        distances: matriz de distancias ``(n, n)`` en kilómetros.
        k: número de vecinos más cercanos a conservar por estación.

    Returns:
        La misma adyacencia, con las aristas descartadas puestas a cero. Se
        mantienen los pesos gaussianos originales (no se binariza), porque la
        intensidad de la relación espacial también es información útil.

    Raises:
        ValueError: si las formas de las dos matrices no coinciden.
    """
    weights = np.asarray(adjacency, dtype=float)
    matrix = _as_square(distances)

    if weights.shape != matrix.shape:
        raise ValueError(
            f"la forma de la adyacencia {weights.shape} no coincide con la de "
            f"las distancias {matrix.shape}")

    kept = set(minimum_spanning_tree(matrix)) | knn_neighbors(matrix, k)

    sparse = np.zeros_like(weights)
    for i, j in kept:
        sparse[i, j] = weights[i, j]
        sparse[j, i] = weights[j, i]
    return sparse
