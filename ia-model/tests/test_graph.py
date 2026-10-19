"""Tests de la construcción del grafo de estaciones.

El grafo es el corazón del modelo: la ST-GNN solo puede aprender relaciones
espaciales entre estaciones si la matriz de adyacencia las representa bien.
Aquí se verifica la matemática, no el modelo: kernel gaussiano, árbol de
expansión mínima (que garantiza conectividad) y k vecinos más cercanos.
"""

import numpy as np
import pytest

from lagunapaca_gnn.graph import (
    gaussian_adjacency,
    knn_neighbors,
    minimum_spanning_tree,
    sparsify,
)

# Matriz de distancias en kilómetros de 4 estaciones (ejemplo pequeño y
# verificable a mano). Se usa para comprobar los resultados exactos.
#
#       0    1    2    3
#  0 [  0    1    5    6 ]
#  1 [  1    0    4    5 ]
#  2 [  5    4    0    2 ]
#  3 [  6    5    2    0 ]
DISTANCES = np.array([
    [0.0, 1.0, 5.0, 6.0],
    [1.0, 0.0, 4.0, 5.0],
    [5.0, 4.0, 0.0, 2.0],
    [6.0, 5.0, 2.0, 0.0],
])


def is_connected(edges, node_count):
    """Comprueba conectividad por recorrido en anchura sobre la lista de aristas."""
    neighbours = {n: set() for n in range(node_count)}
    for a, b in edges:
        neighbours[a].add(b)
        neighbours[b].add(a)

    visited = set()
    pending = [0]
    while pending:
        node = pending.pop()
        if node in visited:
            continue
        visited.add(node)
        pending.extend(neighbours[node] - visited)
    return visited == set(range(node_count))


class TestGaussianAdjacency:
    """A_ij = exp(-d_ij^2 / (2 * sigma^2)) con diagonal cero."""

    def test_usa_el_kernel_gaussiano(self):
        adjacency = gaussian_adjacency(DISTANCES, sigma=1.0)

        # d = 1 km, sigma = 1 km: exp(-1^2 / (2 * 1)) = exp(-0.5) = 0.6065...
        assert adjacency[0, 1] == pytest.approx(np.exp(-0.5))
        # d = 4 km, sigma = 1 km: exp(-4^2 / (2 * 1)) = exp(-8) = 3.35e-4
        assert adjacency[1, 2] == pytest.approx(np.exp(-8.0))

    def test_la_diagonal_es_cero(self):
        adjacency = gaussian_adjacency(DISTANCES, sigma=2.0)

        assert np.allclose(np.diag(adjacency), 0.0)

    def test_es_simetrica(self):
        adjacency = gaussian_adjacency(DISTANCES, sigma=2.0)

        assert np.allclose(adjacency, adjacency.T)

    def test_decae_con_la_distancia(self):
        adjacency = gaussian_adjacency(DISTANCES, sigma=2.0)

        # A mayor distancia, menor peso: 1 < 4 < 5 < 6 km desde el nodo 0.
        assert adjacency[0, 1] > adjacency[0, 2] > adjacency[0, 3]

    def test_sigma_controla_el_alcance(self):
        cerca = gaussian_adjacency(DISTANCES, sigma=1.0)
        lejos = gaussian_adjacency(DISTANCES, sigma=10.0)

        # Con sigma mayor, los vecinos lejanos pesan más.
        assert lejos[0, 3] > cerca[0, 3]

    def test_acepta_listas_de_listas(self):
        adjacency = gaussian_adjacency(DISTANCES.tolist(), sigma=1.0)

        assert adjacency[0, 1] == pytest.approx(np.exp(-0.5))

    def test_rechaza_una_matriz_no_cuadrada(self):
        with pytest.raises(ValueError, match="cuadrada"):
            gaussian_adjacency(np.ones((3, 2)), sigma=1.0)

    def test_rechaza_distancias_negativas(self):
        negativas = DISTANCES.copy()
        negativas[0, 1] = -1.0

        with pytest.raises(ValueError, match="negativa"):
            gaussian_adjacency(negativas, sigma=1.0)

    def test_rechaza_sigma_no_positivo(self):
        with pytest.raises(ValueError, match="sigma"):
            gaussian_adjacency(DISTANCES, sigma=0.0)


class TestMinimumSpanningTree:
    """El MST garantiza que el grafo quede conectado."""

    def test_elige_las_aristas_de_menor_peso(self):
        # Aristas ordenadas: 0-1 (1), 2-3 (2), 1-2 (4), 1-3 (5), 0-2 (5), 0-3 (6).
        # Kruskal toma 0-1, 2-3 y 1-2: total 7.
        edges = minimum_spanning_tree(DISTANCES)

        assert edges == [(0, 1), (1, 2), (2, 3)]

    def test_siempre_devuelve_n_menos_1_aristas(self):
        edges = minimum_spanning_tree(DISTANCES)

        assert len(edges) == DISTANCES.shape[0] - 1

    def test_el_grafo_queda_conectado(self):
        edges = minimum_spanning_tree(DISTANCES)

        assert is_connected(edges, node_count=4)

    def test_rechaza_una_matriz_no_cuadrada(self):
        with pytest.raises(ValueError, match="cuadrada"):
            minimum_spanning_tree(np.ones((2, 3)))


class TestKnnNeighbors:
    """Los k vecinos más cercanos añaden aristas que el MST no conserva."""

    def test_k_igual_a_1_toma_el_vecino_mas_cercano(self):
        # 0 -> 1, 1 -> 0, 2 -> 3, 3 -> 2
        assert knn_neighbors(DISTANCES, k=1) == {(0, 1), (2, 3)}

    def test_k_igual_a_2_incluye_los_segundos_vecinos(self):
        edges = knn_neighbors(DISTANCES, k=2)

        assert (0, 2) in edges  # segundo vecino del nodo 0
        assert (1, 3) in edges  # segundo vecino del nodo 1
        assert (1, 2) in edges

    def test_las_aristas_vienen_normalizadas_como_i_menor_que_j(self):
        for a, b in knn_neighbors(DISTANCES, k=2):
            assert a < b

    def test_rechaza_k_menor_que_uno(self):
        with pytest.raises(ValueError, match="k"):
            knn_neighbors(DISTANCES, k=0)

    def test_rechaza_k_mayor_o_igual_al_numero_de_nodos(self):
        with pytest.raises(ValueError, match="k"):
            knn_neighbors(DISTANCES, k=4)


class TestSparsify:
    """Adyacencia final = gaussiana, limitada a MST unión KNN."""

    def test_conserva_los_pesos_gaussianos_en_las_aristas_elegidas(self):
        adjacency = gaussian_adjacency(DISTANCES, sigma=1.0)

        sparse = sparsify(adjacency, DISTANCES, k=1)

        assert sparse[0, 1] == pytest.approx(adjacency[0, 1])
        assert sparse[2, 3] == pytest.approx(adjacency[2, 3])

    def test_pone_a_cero_las_aristas_descartadas(self):
        adjacency = gaussian_adjacency(DISTANCES, sigma=1.0)

        # Con k=1, la arista 0-3 (6 km) no interesa a nadie.
        sparse = sparsify(adjacency, DISTANCES, k=1)

        assert sparse[0, 3] == 0.0
        assert sparse[3, 0] == 0.0

    def test_el_resultado_sigue_siendo_simetrico_y_sin_autolazos(self):
        adjacency = gaussian_adjacency(DISTANCES, sigma=1.0)

        sparse = sparsify(adjacency, DISTANCES, k=2)

        assert np.allclose(sparse, sparse.T)
        assert np.allclose(np.diag(sparse), 0.0)

    def test_siempre_queda_conectado_gracias_al_mst(self):
        adjacency = gaussian_adjacency(DISTANCES, sigma=1.0)

        sparse = sparsify(adjacency, DISTANCES, k=1)

        edges = [(i, j) for i in range(4) for j in range(i + 1, 4) if sparse[i, j] > 0]
        assert is_connected(edges, node_count=4)

    def test_con_k_suficiente_es_la_adyacencia_completa(self):
        adjacency = gaussian_adjacency(DISTANCES, sigma=1.0)

        sparse = sparsify(adjacency, DISTANCES, k=3)

        assert np.allclose(sparse, adjacency)

    def test_rechaza_formas_incompatibles(self):
        adjacency = gaussian_adjacency(DISTANCES, sigma=1.0)

        with pytest.raises(ValueError, match="forma"):
            sparsify(adjacency, DISTANCES[:3, :3], k=1)
