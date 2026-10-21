"""Registro de artefactos del ST-GNN.

Un artefacto es un archivo ``.pt`` con **todo** lo necesario para inferir sin
volver a entrenar:

* la configuración exacta del modelo (para reconstruirlo),
* los pesos,
* el escalador ajustado **solo con entrenamiento**,
* la adyacencia del grafo,
* las métricas del experimento y la identificación del origen de los datos.

Sin el escalador guardado, la inferencia sería incorrecta aunque los pesos
fueran buenos: el modelo aprendió sobre datos estandarizados. Sin la
adyacencia, el GCN y la atención perderían el grafo.

El formato está versionado (``FORMAT_VERSION``). Cargar un artefacto con otra
versión falla de forma explícita en lugar de intentar adivinar.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import torch

from lagunapaca_gnn.model import SpatioTemporalGNN
from lagunapaca_gnn.normalization import StandardScaler

FORMAT_VERSION = 1


@dataclass
class HorizonArtifact:
    """Modelo y contexto de un horizonte concreto."""

    horizon: int
    model: SpatioTemporalGNN
    scaler: StandardScaler
    edge_index: np.ndarray
    edge_weight: np.ndarray | None
    config: dict[str, Any]
    metrics: dict[str, float]


@dataclass
class ModelBundle:
    """Artefacto completo: todos los horizontes de una misma versión."""

    version: str
    created_at: str
    data_source: str
    stations: tuple[str, ...]
    variables: tuple[str, ...]
    input_len: int
    target_index: int
    horizons: dict[int, HorizonArtifact]

    def info(self) -> dict[str, Any]:
        """Metadatos públicos (sin pesos ni escaladores)."""
        return {
            "version": self.version,
            "created_at": self.created_at,
            "data_source": self.data_source,
            "stations": list(self.stations),
            "variables": list(self.variables),
            "input_len": self.input_len,
            "target_index": self.target_index,
            "target_variable": self.variables[self.target_index],
            "horizons": sorted(self.horizons),
            "metrics": {
                str(h): dict(artifact.metrics)
                for h, artifact in sorted(self.horizons.items())
            },
        }


def new_version(seed: int, input_len: int, epochs: int,
                data_source: str = "synthetic",
                data_tag: str | None = None) -> str:
    """Versión determinista a partir de la configuración del experimento.

    No incluye la fecha para que dos ejecuciones con la misma semilla y los
    mismos hiperparámetros produzcan el mismo identificador. La fecha sí queda
    en ``created_at``.

    Para datos de campo se añade ``data_tag`` (normalmente los primeros
    caracteres de :attr:`FieldSeries.fingerprint`). Sin esa huella, dos
    campañas distintas con la misma semilla e hiperparámetros producirían la
    misma versión, y el artefacto de una pisaría al de la otra.
    """
    if data_source == "synthetic":
        prefix = "stgnn-synthetic"
    elif data_source == "field":
        prefix = "stgnn-field"
    else:
        raise ValueError(
            f"origen de datos desconocido: {data_source!r}; "
            "se esperaba 'synthetic' o 'field'")

    suffix = f"-d{data_tag}" if data_tag else ""
    return f"{prefix}-s{seed}-i{input_len}-e{epochs}{suffix}"


def save_bundle(path: str | Path, bundle: ModelBundle) -> Path:
    """Guarda el artefacto. Crea el directorio si no existe."""
    destino = Path(path)
    destino.parent.mkdir(parents=True, exist_ok=True)

    payload = {
        "format_version": FORMAT_VERSION,
        "version": bundle.version,
        "created_at": bundle.created_at,
        "data_source": bundle.data_source,
        "stations": list(bundle.stations),
        "variables": list(bundle.variables),
        "input_len": bundle.input_len,
        "target_index": bundle.target_index,
        "horizons": {
            str(horizon): {
                "config": dict(artifact.config),
                "state_dict": artifact.model.state_dict(),
                "scaler_mean": np.asarray(artifact.scaler.mean),
                "scaler_std": np.asarray(artifact.scaler.std),
                "edge_index": np.asarray(artifact.edge_index),
                "edge_weight": None if artifact.edge_weight is None
                else np.asarray(artifact.edge_weight),
                "metrics": dict(artifact.metrics),
            }
            for horizon, artifact in bundle.horizons.items()
        },
    }

    # weights_only=False: el archivo lo produce este mismo proyecto y contiene
    # arreglos de numpy, no solo tensores. No se cargan artefactos de terceros.
    torch.save(payload, destino)
    return destino


def load_bundle(path: str | Path) -> ModelBundle:
    """Carga y valida un artefacto.

    Raises:
        FileNotFoundError: si la ruta no existe.
        ValueError: si el formato no es compatible o está incompleto.
    """
    origen = Path(path)
    if not origen.exists():
        raise FileNotFoundError(f"No existe el artefacto del modelo: {origen}")

    data = torch.load(origen, map_location="cpu", weights_only=False)
    if not isinstance(data, dict):
        raise ValueError("El artefacto no tiene el formato esperado")

    version_formato = data.get("format_version")
    if version_formato != FORMAT_VERSION:
        raise ValueError(
            f"Formato de artefacto incompatible: {version_formato} "
            f"(se esperaba {FORMAT_VERSION})")

    horizons_raw = data.get("horizons") or {}
    if not horizons_raw:
        raise ValueError("El artefacto no contiene ningún horizonte entrenado")

    horizons: dict[int, HorizonArtifact] = {}
    for clave, item in horizons_raw.items():
        config = item["config"]
        model = SpatioTemporalGNN(**config)
        model.load_state_dict(item["state_dict"])
        model.eval()

        scaler = StandardScaler(
            mean=np.asarray(item["scaler_mean"], dtype=float),
            std=np.asarray(item["scaler_std"], dtype=float),
        )
        edge_weight = item.get("edge_weight")

        horizons[int(clave)] = HorizonArtifact(
            horizon=int(clave),
            model=model,
            scaler=scaler,
            edge_index=np.asarray(item["edge_index"], dtype=np.int64),
            edge_weight=None if edge_weight is None else np.asarray(edge_weight, dtype=float),
            config=dict(config),
            metrics=dict(item.get("metrics") or {}),
        )

    return ModelBundle(
        version=str(data["version"]),
        created_at=str(data.get("created_at") or datetime.now(timezone.utc).isoformat()),
        data_source=str(data["data_source"]),
        stations=tuple(data["stations"]),
        variables=tuple(data["variables"]),
        input_len=int(data["input_len"]),
        target_index=int(data["target_index"]),
        horizons=horizons,
    )
