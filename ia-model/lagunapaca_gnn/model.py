"""Modelo ST-GNN de la Laguna de Paca: TCN + GCN + Attention.

La arquitectura combina las tres piezas que especifica la tesis, en este orden:

1. **TCN** (red convolucional temporal) — aprende el patrón temporal de cada
   estación por separado. Las convoluciones son **causales**: solo miran el
   pasado, nunca el futuro. Esa propiedad está probada en `test_model.py`.
2. **GCN** (red convolucional de grafos) — propaga la información entre
   estaciones vecinas según la adyacencia gaussiana + MST/KNN construida en
   :mod:`lagunapaca_gnn.graph`. Aquí es donde el modelo deja de mirar cada
   estación aislada y aprovecha que la laguna es un sistema conectado.
3. **Attention** (atención multi-cabeza) — pondera qué estaciones son más
   relevantes para cada predicción, **enmascarada por la adyacencia**: una
   estación no puede "mirar" a otra con la que no está conectada. Sin esa
   máscara, la atención sería completamente densa y echaría por tierra la
   estructura del grafo.

La salida es una predicción de la variable objetivo (el pH) para **cada
estación** y para cada paso del horizonte: forma `(lote, estaciones, horizonte)`.
"""

from __future__ import annotations

import torch
from torch import nn
from torch_geometric.nn import GCNConv


def batch_edge_index(edge_index, batch_size: int, node_count: int) -> torch.Tensor:
    """Replica el grafo de la laguna para cada muestra del lote.

    PyG espera un único grafo por adelanto, así que las ``batch_size`` copias se
    disponen en diagonal por bloques desplazando los índices de nodo.

    Returns:
        Tensor ``(2, batch_size * aristas)``.
    """
    edges = torch.as_tensor(edge_index, dtype=torch.long)
    offsets = torch.arange(batch_size, device=edges.device).view(-1, 1, 1) * node_count
    stacked = edges.unsqueeze(0) + offsets
    return stacked.permute(1, 0, 2).reshape(2, -1)


class TemporalBlock(nn.Module):
    """Bloque de convolución dilatada causal con conexión residual."""

    def __init__(self, channels: int, kernel_size: int, dilation: int,
                 dropout: float = 0.0) -> None:
        super().__init__()
        # El relleno va solo a la izquierda: es lo que hace la convolución causal.
        self.left_padding = (kernel_size - 1) * dilation
        self.conv = nn.Conv1d(channels, channels, kernel_size, dilation=dilation)
        self.activation = nn.ReLU()
        self.dropout = nn.Dropout(dropout)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        padded = nn.functional.pad(x, (self.left_padding, 0))
        out = self.dropout(self.activation(self.conv(padded)))
        return x + out


class TemporalConvNet(nn.Module):
    """Pila de bloques TCN con dilatación exponencial (1, 2, 4, ...)."""

    def __init__(self, in_channels: int, hidden: int, levels: int = 2,
                 kernel_size: int = 3, dropout: float = 0.0) -> None:
        super().__init__()
        self.input_projection = nn.Conv1d(in_channels, hidden, kernel_size=1)
        self.blocks = nn.ModuleList(
            TemporalBlock(hidden, kernel_size, dilation=2 ** level, dropout=dropout)
            for level in range(levels)
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """Entrada ``(nodos, variables, tiempo)``; salida ``(nodos, oculto, tiempo)``."""
        out = self.input_projection(x)
        for block in self.blocks:
            out = block(out)
        return out


class MaskedGraphAttention(nn.Module):
    """Atención multi-cabeza sobre las estaciones, restringida al grafo.

    Se implementa a mano en lugar de usar `nn.MultiheadAttention` por una razón
    concreta: aquí la máscara **no admite excepciones**. Una estación que no es
    vecina debe recibir peso exactamente cero, y eso hay que poder verificarlo.
    """

    def __init__(self, hidden: int, heads: int = 2, dropout: float = 0.0) -> None:
        super().__init__()
        if hidden % heads != 0:
            raise ValueError(
                f"el número de cabezas ({heads}) debe dividir al tamaño oculto ({hidden})")

        self.heads = heads
        self.head_dim = hidden // heads
        self.query = nn.Linear(hidden, hidden)
        self.key = nn.Linear(hidden, hidden)
        self.value = nn.Linear(hidden, hidden)
        self.output = nn.Linear(hidden, hidden)
        self.norm = nn.LayerNorm(hidden)
        self.dropout = nn.Dropout(dropout)

    def forward(self, x: torch.Tensor, mask: torch.Tensor | None = None,
                need_weights: bool = False):
        """``x``: ``(lote, estaciones, oculto)``; ``mask``: ``(estaciones, estaciones)``."""
        batch_size, node_count, hidden = x.shape

        def project(layer: nn.Linear) -> torch.Tensor:
            return layer(x).view(batch_size, node_count, self.heads, self.head_dim).transpose(1, 2)

        query, key, value = project(self.query), project(self.key), project(self.value)

        scores = query @ key.transpose(-2, -1) / (self.head_dim ** 0.5)
        if mask is not None:
            # Se prohibe lo no permitido antes del softmax: peso exactamente cero.
            scores = scores.masked_fill(~mask.unsqueeze(0).unsqueeze(0), float("-inf"))

        weights = torch.softmax(scores, dim=-1)
        context = (self.dropout(weights) @ value).transpose(1, 2).reshape(batch_size, node_count, hidden)
        out = self.norm(x + self.output(context))

        if need_weights:
            return out, weights.mean(dim=1)
        return out, None


class SpatioTemporalGNN(nn.Module):
    """Modelo completo: TCN sobre el tiempo, GCN sobre el grafo y atención final."""

    def __init__(self, num_variables: int, horizon: int = 1, hidden: int = 32,
                 tcn_levels: int = 2, kernel_size: int = 3, gcn_layers: int = 1,
                 heads: int = 2, dropout: float = 0.0) -> None:
        super().__init__()
        self.horizon = horizon
        self.tcn = TemporalConvNet(num_variables, hidden, tcn_levels, kernel_size, dropout)
        self.graph_layers = nn.ModuleList(
            GCNConv(hidden, hidden) for _ in range(gcn_layers)
        )
        self.attention = MaskedGraphAttention(hidden, heads, dropout)
        self.head = nn.Linear(hidden, horizon)

    @staticmethod
    def _attention_mask(edge_index, node_count: int, device) -> torch.Tensor:
        """Máscara de atención derivada de la adyacencia.

        La diagonal siempre se permite: una estación se atiende a sí misma. Sin
        ella, una estación aislada tendría una fila con todos los pesos
        prohibidos y el ``softmax`` de una fila de ``-infinitos`` daría ``NaN``.
        """
        mask = torch.zeros(node_count, node_count, dtype=torch.bool, device=device)
        mask[edge_index[0], edge_index[1]] = True
        mask.fill_diagonal_(True)
        return mask

    def forward(self, x: torch.Tensor, edge_index, edge_weight: torch.Tensor | None = None,
                need_weights: bool = False):
        """``x``: ``(lote, estaciones, tiempo, variables)``."""
        batch_size, node_count, time_steps, variables = x.shape

        # (lote, estaciones, tiempo, variables) -> (lote*estaciones, variables, tiempo)
        temporal_input = x.permute(0, 1, 3, 2).reshape(
            batch_size * node_count, variables, time_steps)
        encoded = self.tcn(temporal_input)

        # Se resume la ventana con su último instante (el TCN es causal).
        latent = encoded[:, :, -1]

        batched_edges = batch_edge_index(edge_index, batch_size, node_count)
        batched_weights = None
        if edge_weight is not None:
            batched_weights = torch.as_tensor(
                edge_weight, dtype=latent.dtype, device=latent.device).repeat(batch_size)

        mixed = latent
        for layer in self.graph_layers:
            mixed = torch.relu(layer(mixed, batched_edges, batched_weights))

        per_sample = mixed.view(batch_size, node_count, -1)
        mask = self._attention_mask(
            torch.as_tensor(edge_index, dtype=torch.long, device=x.device),
            node_count, x.device)
        attended, weights = self.attention(per_sample, mask, need_weights=need_weights)

        predictions = self.head(attended)
        if need_weights:
            return predictions, weights
        return predictions
