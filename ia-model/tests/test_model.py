"""Tests del modelo ST-GNN (TCN + GCN + Attention).

Una red neuronal no se puede probar comparando valores exactos: los pesos se
inicializan al azar. Lo que sí se puede —y se debe— probar son sus
**propiedades**: la forma de la salida, que el grafo realmente importe, que no
haya fuga de información hacia el futuro, que los gradientes lleguen a todas
las capas y que una estación aislada no rompa la atención.
"""

import pytest
import torch

from lagunapaca_gnn.model import (
    SpatioTemporalGNN,
    TemporalConvNet,
    batch_edge_index,
)

NODES = 4
VARIABLES = 4  # pH, turbidez, temperatura y TDS
TIME_STEPS = 6


def ring_edge_index(node_count):
    """Grafo en anillo: cada estación conectada con la siguiente."""
    edges = [(node, (node + 1) % node_count) for node in range(node_count)]
    return torch.tensor(edges, dtype=torch.long).t().contiguous()


def build_model(**overrides):
    """Modelo pequeño y determinista, en modo evaluación (sin dropout)."""
    settings = {
        "num_variables": VARIABLES,
        "horizon": 1,
        "hidden": 8,
        "tcn_levels": 2,
        "kernel_size": 3,
        "gcn_layers": 1,
        "heads": 2,
        "dropout": 0.0,
        "target_index": 0,
    }
    settings.update(overrides)
    return SpatioTemporalGNN(**settings).eval()


class TestBatchEdgeIndex:
    """El grafo se replica para cada muestra de la lote (diagonal por bloques)."""

    def test_repite_el_grafo_desplazado_por_muestra(self):
        edge_index = torch.tensor([[0, 2], [1, 3]], dtype=torch.long)

        batched = batch_edge_index(edge_index, batch_size=2, node_count=4)

        assert batched.tolist() == [[0, 2, 4, 6], [1, 3, 5, 7]]

    def test_conserva_la_forma_dos_por_n_aristas(self):
        edge_index = ring_edge_index(4)

        batched = batch_edge_index(edge_index, batch_size=3, node_count=4)

        assert batched.shape == (2, 4 * 3)


class TestSpatioTemporalGNN:

    def test_devuelve_una_prediccion_por_estacion_y_paso(self):
        model = build_model(horizon=2)
        x = torch.randn(3, NODES, TIME_STEPS, VARIABLES)

        predictions = model(x, ring_edge_index(NODES))

        assert predictions.shape == (3, NODES, 2)

    def test_el_horizonte_de_uno_tambien_funciona(self):
        model = build_model(horizon=1)
        x = torch.randn(2, NODES, TIME_STEPS, VARIABLES)

        assert model(x, ring_edge_index(NODES)).shape == (2, NODES, 1)

    @pytest.mark.parametrize("node_count", [2, 4, 7])
    def test_se_adapta_al_numero_de_estaciones(self, node_count):
        model = build_model()
        x = torch.randn(2, node_count, TIME_STEPS, VARIABLES)

        predictions = model(x, ring_edge_index(node_count))

        assert predictions.shape == (2, node_count, 1)

    def test_se_adapta_a_la_longitud_de_la_ventana(self):
        model = build_model()

        for time_steps in (3, 12):
            x = torch.randn(2, NODES, time_steps, VARIABLES)
            assert model(x, ring_edge_index(NODES)).shape == (2, NODES, 1)

    def test_es_reproducible_con_la_misma_semilla(self):
        x = torch.randn(2, NODES, TIME_STEPS, VARIABLES)
        edges = ring_edge_index(NODES)

        torch.manual_seed(13)
        first = build_model()(x, edges)
        torch.manual_seed(13)
        second = build_model()(x, edges)

        assert torch.allclose(first, second)

    def test_el_grafo_importa(self):
        # Con la misma entrada, dos topologias distintas deben dar salidas
        # distintas: si no, el modelo estaria ignorando la adyacencia.
        model = build_model()
        x = torch.randn(2, NODES, TIME_STEPS, VARIABLES)

        anillo = ring_edge_index(NODES)
        estrella = torch.tensor([[0, 0, 0], [1, 2, 3]], dtype=torch.long)

        assert not torch.allclose(model(x, anillo), model(x, estrella))

    def test_los_gradientes_llegan_a_todas_las_capas(self):
        model = build_model(horizon=1)
        x = torch.randn(2, NODES, TIME_STEPS, VARIABLES)

        model(x, ring_edge_index(NODES)).sum().backward()

        for name, parameter in model.named_parameters():
            assert parameter.grad is not None, f"sin gradiente: {name}"
            assert torch.isfinite(parameter.grad).all(), f"gradiente no finito: {name}"
        assert any(parameter.grad.abs().sum() > 0 for parameter in model.parameters())

    def test_una_estacion_aislada_no_produce_nan(self):
        # El nodo 3 no tiene aristas. Sin la diagonal en la mascara de atencion,
        # su fila quedaria completamente enmascarada y el softmax daria NaN.
        model = build_model()
        x = torch.randn(2, NODES, TIME_STEPS, VARIABLES)

        predictions = model(x, torch.tensor([[0, 1], [1, 0]], dtype=torch.long))

        assert torch.isfinite(predictions).all()

    def test_una_estacion_no_se_contamina_de_una_comunidad_ajena(self):
        # Dos comunidades desconectadas: {0,1} y {2,3}.
        model = build_model()
        edges = torch.tensor([[0, 2], [1, 3]], dtype=torch.long)
        x = torch.randn(2, NODES, TIME_STEPS, VARIABLES)

        original = model(x, edges)
        perturbado = x.clone()
        perturbado[:, :2, :, :] += 10.0  # solo se toca la comunidad {0,1}

        cambiado = model(perturbado, edges)

        # Los nodos 2 y 3 no pueden haber visto el cambio...
        assert torch.allclose(original[:, 2:, :], cambiado[:, 2:, :], atol=1e-6)
        # ...y los nodos 0 y 1 sí deben notarlo.
        assert not torch.allclose(original[:, :2, :], cambiado[:, :2, :])

    def test_la_atencion_es_una_distribucion_de_probabilidad(self):
        model = build_model()
        x = torch.randn(2, NODES, TIME_STEPS, VARIABLES)

        _, weights = model(x, ring_edge_index(NODES), need_weights=True)

        assert weights.shape == (2, NODES, NODES)
        assert torch.allclose(weights.sum(dim=-1), torch.ones(2, NODES), atol=1e-6)

    def test_la_atencion_respeta_la_adyacencia(self):
        model = build_model()
        x = torch.randn(1, NODES, TIME_STEPS, VARIABLES)
        # Solo la arista 0-1; el resto son autoatenciones.
        edges = torch.tensor([[0], [1]], dtype=torch.long)

        _, weights = model(x, edges, need_weights=True)

        assert weights[0, 0, 1] > 0
        assert weights[0, 0, 2] == 0.0
        assert weights[0, 2, 0] == 0.0

    def test_rechaza_una_cantidad_de_cabezas_incompatible(self):
        with pytest.raises(ValueError, match="cabezas"):
            build_model(hidden=9, heads=2)


class TestAnclajeALaPersistencia:
    """El modelo predice la corrección sobre el último valor, no el valor absoluto.

    Es una decisión de diseño, no un detalle: en una serie temporal suave, "el
    valor de la próxima hora será como el de esta" es una línea base durísima de
    batir. Si el modelo parte de ahí y solo aprende la corrección, nunca puede
    quedar peor que la persistencia, y el entrenamiento tiene un punto de partida
    razonable en vez de tener que descubrir el valor absoluto desde el azar.
    """

    def test_con_la_cabeza_a_cero_reproduce_el_ultimo_valor(self):
        model = build_model(target_index=2)
        with torch.no_grad():
            model.head.weight.zero_()
            model.head.bias.zero_()

        x = torch.randn(2, NODES, TIME_STEPS, VARIABLES)
        predictions = model(x, ring_edge_index(NODES))

        assert torch.allclose(predictions[:, :, 0], x[:, :, -1, 2])

    def test_el_ancla_es_la_variable_objetivo(self):
        model = build_model(target_index=1)
        with torch.no_grad():
            model.head.weight.zero_()
            model.head.bias.zero_()

        x = torch.randn(2, NODES, TIME_STEPS, VARIABLES)
        predictions = model(x, ring_edge_index(NODES))

        assert torch.allclose(predictions[:, :, 0], x[:, :, -1, 1])

    def test_el_ancla_se_repite_en_todo_el_horizonte(self):
        model = build_model(target_index=0, horizon=3)
        with torch.no_grad():
            model.head.weight.zero_()
            model.head.bias.zero_()

        x = torch.randn(2, NODES, TIME_STEPS, VARIABLES)
        predictions = model(x, ring_edge_index(NODES))

        for step in range(3):
            assert torch.allclose(predictions[:, :, step], x[:, :, -1, 0])

    def test_rechaza_un_objetivo_fuera_de_rango(self):
        with pytest.raises(ValueError, match="target_index"):
            build_model(target_index=VARIABLES)


class TestTemporalConvNet:
    """La red temporal no puede ver el futuro: es la base de la predicción."""

    def test_no_filtra_informacion_del_futuro(self):
        torch.manual_seed(0)
        tcn = TemporalConvNet(in_channels=VARIABLES, hidden=8, levels=2, kernel_size=3).eval()
        x = torch.randn(3, VARIABLES, 7)

        original = tcn(x)
        modificado = x.clone()
        modificado[:, :, -1] += 5.0  # solo se toca el último instante

        cambiado = tcn(modificado)

        # Los instantes anteriores no pueden depender del último.
        assert torch.allclose(original[:, :, :-1], cambiado[:, :, :-1], atol=1e-6)
        assert not torch.allclose(original[:, :, -1], cambiado[:, :, -1])

    def test_conserva_la_longitud_de_la_secuencia(self):
        tcn = TemporalConvNet(in_channels=VARIABLES, hidden=8, levels=3, kernel_size=3).eval()

        assert tcn(torch.randn(2, VARIABLES, 9)).shape == (2, 8, 9)
