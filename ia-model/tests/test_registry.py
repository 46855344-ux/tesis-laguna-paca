"""Pruebas del registro de artefactos: guardar y cargar debe ser fiel."""

from __future__ import annotations

import numpy as np
import pytest
import torch

from lagunapaca_gnn.model import SpatioTemporalGNN
from lagunapaca_gnn.normalization import StandardScaler
from lagunapaca_gnn.registry import (
    FORMAT_VERSION,
    HorizonArtifact,
    ModelBundle,
    load_bundle,
    new_version,
    save_bundle,
)

CONFIG = {
    "num_variables": 4,
    "horizon": 6,
    "hidden": 4,
    "tcn_levels": 1,
    "kernel_size": 3,
    "gcn_layers": 1,
    "heads": 1,
    "dropout": 0.0,
    "target_index": 0,
}

STATIONS = ("N1", "N2", "N3", "N4")
VARIABLES = ("ph", "turbidity_ntu", "temp_c", "tds_ppm")
EDGE_INDEX = np.array(
    [[0, 1, 1, 2, 2, 3, 3, 0], [1, 0, 2, 1, 3, 2, 0, 3]], dtype=np.int64)
EDGE_WEIGHT = np.full(8, 0.5, dtype=float)


def _bundle(horizons=(6,)) -> ModelBundle:
    artifacts = {}
    for horizon in horizons:
        config = {**CONFIG, "horizon": horizon}
        torch.manual_seed(7)
        artifacts[horizon] = HorizonArtifact(
            horizon=horizon,
            model=SpatioTemporalGNN(**config),
            scaler=StandardScaler(
                mean=np.array([7.2, 15.0, 18.0, 320.0]),
                std=np.array([0.3, 2.0, 1.0, 20.0]),
            ),
            edge_index=EDGE_INDEX,
            edge_weight=EDGE_WEIGHT,
            config=config,
            metrics={"r2": 0.93, "rmse": 0.05, "mae": 0.04},
        )
    return ModelBundle(
        version="stgnn-test",
        created_at="2026-10-20T00:00:00+00:00",
        data_source="synthetic",
        stations=STATIONS,
        variables=VARIABLES,
        input_len=24,
        target_index=0,
        horizons=artifacts,
    )


def _entrada(seed: int = 1) -> np.ndarray:
    rng = np.random.default_rng(seed)
    return rng.normal(
        loc=[7.2, 15.0, 18.0, 320.0],
        scale=[0.3, 2.0, 1.0, 20.0],
        size=(4, 24, 4),
    )


def test_guardarYCargar_conservaMetadatosYEscalador(tmp_path):
    destino = save_bundle(tmp_path / "modelo.pt", _bundle((1, 3, 6)))

    cargado = load_bundle(destino)

    assert cargado.version == "stgnn-test"
    assert cargado.data_source == "synthetic"
    assert cargado.stations == STATIONS
    assert cargado.variables == VARIABLES
    assert cargado.input_len == 24
    assert sorted(cargado.horizons) == [1, 3, 6]
    assert cargado.horizons[6].metrics["r2"] == pytest.approx(0.93)
    np.testing.assert_allclose(cargado.horizons[6].scaler.mean,
                               [7.2, 15.0, 18.0, 320.0])


def test_losPesosCargadosProducenLaMismaSalida(tmp_path):
    bundle = _bundle((6,))
    entrada = _entrada()
    estandarizado = bundle.horizons[6].scaler.transform(entrada)
    tensor = torch.as_tensor(estandarizado, dtype=torch.float32).unsqueeze(0)
    edge = torch.as_tensor(EDGE_INDEX)
    peso = torch.as_tensor(EDGE_WEIGHT)

    with torch.no_grad():
        esperado = bundle.horizons[6].model(tensor, edge, peso).numpy()

    cargado = load_bundle(save_bundle(tmp_path / "modelo.pt", bundle))
    with torch.no_grad():
        obtenido = cargado.horizons[6].model(tensor, edge, peso).numpy()

    np.testing.assert_allclose(obtenido, esperado, rtol=0, atol=1e-6)


def test_versionEsDeterminista():
    assert new_version(42, 24, 150) == "stgnn-synthetic-s42-i24-e150"
    assert new_version(42, 24, 150) == new_version(42, 24, 150)
    assert new_version(42, 24, 150) != new_version(43, 24, 150)


def test_versionDeCampoIncluyeOrigenYHuella():
    version = new_version(42, 24, 150, data_source="field",
                          data_tag="a1b2c3d4")

    assert version == "stgnn-field-s42-i24-e150-da1b2c3d4"
    assert version != new_version(42, 24, 150, data_source="field",
                                  data_tag="ffffffff")


def test_origenDeDatosDesconocido_falla():
    with pytest.raises(ValueError, match="origen de datos desconocido"):
        new_version(42, 24, 150, data_source="otro")


def test_archivoInexistente_fallaConMensajeClaro(tmp_path):
    with pytest.raises(FileNotFoundError):
        load_bundle(tmp_path / "no-existe.pt")


def test_formatoIncompatible_falla(tmp_path):
    destino = tmp_path / "viejo.pt"
    torch.save({"format_version": FORMAT_VERSION + 1}, destino)

    with pytest.raises(ValueError, match="Formato de artefacto incompatible"):
        load_bundle(destino)


def test_artefactoSinHorizontes_falla(tmp_path):
    destino = tmp_path / "vacio.pt"
    torch.save({"format_version": FORMAT_VERSION, "horizons": {}}, destino)

    with pytest.raises(ValueError, match="ningún horizonte"):
        load_bundle(destino)
