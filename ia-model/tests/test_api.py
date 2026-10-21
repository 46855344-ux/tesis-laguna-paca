"""Pruebas de la API FastAPI de inferencia.

No descargan nada ni entrenan en cada arranque: construyen un artefacto mínimo
en un directorio temporal y comprueban el contrato HTTP, la validación y el
modo degradado.
"""

from __future__ import annotations

import numpy as np
import pytest
import torch
from fastapi.testclient import TestClient

from lagunapaca_gnn.api import create_app
from lagunapaca_gnn.model import SpatioTemporalGNN
from lagunapaca_gnn.normalization import StandardScaler
from lagunapaca_gnn.registry import (
    HorizonArtifact,
    ModelBundle,
    save_bundle,
)

STATIONS = ("N1", "N2", "N3", "N4")
VARIABLES = ("ph", "turbidity_ntu", "temp_c", "tds_ppm")
MEAN = np.array([7.2, 15.0, 18.0, 320.0])
STD = np.array([0.3, 2.0, 1.0, 20.0])
EDGE_INDEX = np.array(
    [[0, 1, 1, 2, 2, 3, 3, 0], [1, 0, 2, 1, 3, 2, 0, 3]], dtype=np.int64)

BASE = "2026-10-20T10:00:00Z"


def _artefacto(tmp_path, horizons=(1, 3, 6)):
    artifacts = {}
    for horizon in horizons:
        config = {
            "num_variables": 4,
            "horizon": horizon,
            "hidden": 4,
            "tcn_levels": 1,
            "kernel_size": 3,
            "gcn_layers": 1,
            "heads": 1,
            "dropout": 0.0,
            "target_index": 0,
        }
        torch.manual_seed(11)
        artifacts[horizon] = HorizonArtifact(
            horizon=horizon,
            model=SpatioTemporalGNN(**config),
            scaler=StandardScaler(mean=MEAN, std=STD),
            edge_index=EDGE_INDEX,
            edge_weight=np.full(8, 0.5),
            config=config,
            metrics={"r2": 0.93},
        )
    bundle = ModelBundle(
        version="stgnn-test",
        created_at="2026-10-20T00:00:00+00:00",
        data_source="synthetic",
        stations=STATIONS,
        variables=VARIABLES,
        input_len=24,
        target_index=0,
        horizons=artifacts,
    )
    return save_bundle(tmp_path / "modelo.pt", bundle)


def _ventana(seed: int = 3):
    rng = np.random.default_rng(seed)
    return rng.normal(MEAN, STD, size=(4, 24, 4)).tolist()


def _cliente(tmp_path, **kwargs) -> TestClient:
    return TestClient(create_app(model_path=_artefacto(tmp_path), **kwargs))


def test_health_informaElModeloCargado(tmp_path):
    with _cliente(tmp_path) as client:
        respuesta = client.get("/health")

    assert respuesta.status_code == 200
    cuerpo = respuesta.json()
    assert cuerpo["status"] == "ok"
    assert cuerpo["model_loaded"] is True
    assert cuerpo["model_version"] == "stgnn-test"
    assert cuerpo["horizons"] == [1, 3, 6]
    assert cuerpo["input_len"] == 24
    assert cuerpo["data_source"] == "synthetic"


def test_model_informaMetadatosSinPesos(tmp_path):
    with _cliente(tmp_path) as client:
        respuesta = client.get("/model")

    assert respuesta.status_code == 200
    cuerpo = respuesta.json()
    assert cuerpo["target_variable"] == "ph"
    assert cuerpo["stations"] == list(STATIONS)
    assert "state_dict" not in cuerpo


def test_predict_devuelveUnaPrediccionPorEstacion(tmp_path):
    with _cliente(tmp_path) as client:
        respuesta = client.post("/predict", json={
            "horizon_hours": 6,
            "base_time": BASE,
            "input": _ventana(),
        })

    assert respuesta.status_code == 200, respuesta.text
    cuerpo = respuesta.json()
    assert cuerpo["model_version"] == "stgnn-test"
    assert cuerpo["data_source"] == "synthetic"
    assert cuerpo["horizon_hours"] == 6
    assert cuerpo["target_at"] == "2026-10-20T16:00:00Z"
    assert [p["station"] for p in cuerpo["predictions"]] == list(STATIONS)
    assert all(np.isfinite(p["ph"]) for p in cuerpo["predictions"])


def test_predict_esReproducible(tmp_path):
    peticion = {"horizon_hours": 6, "base_time": BASE, "input": _ventana()}
    with _cliente(tmp_path) as client:
        primera = client.post("/predict", json=peticion).json()
        segunda = client.post("/predict", json=peticion).json()

    assert primera["predictions"] == segunda["predictions"]


def test_predict_rechazaVentanaDeTamanoIncorrecto(tmp_path):
    with _cliente(tmp_path) as client:
        respuesta = client.post("/predict", json={
            "horizon_hours": 6, "base_time": BASE, "input": _ventana()[:3]})

    assert respuesta.status_code == 422


def test_predict_rechazaHorizonteNoEntrenado(tmp_path):
    with _cliente(tmp_path) as client:
        respuesta = client.post("/predict", json={
            "horizon_hours": 2, "base_time": BASE, "input": _ventana()})

    assert respuesta.status_code == 422
    assert "Horizonte no soportado" in respuesta.json()["detail"]


def test_predict_rechazaBaseSinZonaHoraria(tmp_path):
    with _cliente(tmp_path) as client:
        respuesta = client.post("/predict", json={
            "horizon_hours": 6, "base_time": "2026-10-20T10:00:00", "input": _ventana()})

    assert respuesta.status_code == 422


def test_predict_rechazaValoresNoFinitos(tmp_path):
    import json

    ventana = _ventana()
    ventana[0][0][0] = float("nan")
    cuerpo = json.dumps({
        "horizon_hours": 6, "base_time": BASE, "input": ventana}, allow_nan=True)

    with _cliente(tmp_path) as client:
        # httpx rechaza NaN en json=, así que se envía el cuerpo crudo.
        respuesta = client.post(
            "/predict", content=cuerpo,
            headers={"Content-Type": "application/json"})

    assert respuesta.status_code == 422


def test_sinArtefacto_quedaDegradadoYNoInventaPredicciones(tmp_path):
    app = create_app(model_path=tmp_path / "no-existe.pt")
    with TestClient(app) as client:
        salud = client.get("/health")
        prediccion = client.post("/predict", json={
            "horizon_hours": 6, "base_time": BASE, "input": _ventana()})

    assert salud.status_code == 200
    assert salud.json()["status"] == "degraded"
    assert salud.json()["model_loaded"] is False
    assert prediccion.status_code == 503


def test_tokenInterno_esObligatorioCuandoSeConfigura(tmp_path):
    with _cliente(tmp_path, internal_token="secreto") as client:
        sinToken = client.get("/model")
        conToken = client.get("/model", headers={"X-Internal-Token": "secreto"})
        # /health queda abierto para el healthcheck del contenedor.
        salud = client.get("/health")

    assert sinToken.status_code == 401
    assert conToken.status_code == 200
    assert salud.status_code == 200


def test_artefactoCorrupto_quedaDegradado(tmp_path):
    ruta = tmp_path / "corrupto.pt"
    ruta.write_bytes(b"esto no es un artefacto")

    app = create_app(model_path=ruta)
    with TestClient(app) as client:
        salud = client.get("/health")

    assert salud.json()["model_loaded"] is False
    assert "No se pudo cargar" in salud.json()["detail"]
