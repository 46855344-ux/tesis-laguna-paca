"""API interna de inferencia del ST-GNN (FastAPI).

El servicio es **interno**: en Docker Compose vive en una red privada y no se
publica a internet. No entrena ni descarga modelos: carga un artefacto existente
y, si no lo encuentra, queda en estado degradado y responde 503 en ``/predict``.
Así el backend puede seguir registrando mediciones y alertas aunque la IA no
esté disponible.

La regla de negocio (qué es una alerta ECA-Agua) **no** vive aquí. Este servicio
solo predice pH.
"""

from __future__ import annotations

import os
import math
import threading
from datetime import datetime, timedelta, timezone
from pathlib import Path

import numpy as np
import torch
from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, field_validator

from lagunapaca_gnn.registry import ModelBundle, load_bundle

DEFAULT_MODEL_PATH = "artifacts/stgnn.pt"


class PredictInput(BaseModel):
    """Ventana lista para inferir."""

    horizon_hours: int = Field(gt=0, le=24)
    base_time: datetime
    input: list[list[list[float]]]

    @field_validator("base_time")
    @classmethod
    def base_time_con_zona(cls, value: datetime) -> datetime:
        if value.tzinfo is None:
            raise ValueError("base_time debe incluir zona horaria (por ejemplo, Z)")
        return value

    @field_validator("input")
    @classmethod
    def forma_tridimensional(cls, value):
        array = np.asarray(value, dtype=float)
        if array.ndim != 3:
            raise ValueError("input debe ser [estaciones][tiempo][variables]")
        if array.shape[0] != 4 or array.shape[2] != 4:
            raise ValueError(
                "input debe tener 4 estaciones y 4 variables; "
                f"se recibió {array.shape}")
        if not np.isfinite(array).all():
            raise ValueError("input no puede contener NaN ni infinitos")
        return value


class StationPrediction(BaseModel):
    station: str
    ph: float


class PredictResponse(BaseModel):
    model_version: str
    data_source: str
    horizon_hours: int
    generated_at: datetime
    target_at: datetime
    predictions: list[StationPrediction]


class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
    model_version: str | None = None
    horizons: list[int] = Field(default_factory=list)
    input_len: int | None = None
    data_source: str | None = None
    detail: str | None = None


class ModelInfoResponse(BaseModel):
    version: str
    created_at: str
    data_source: str
    stations: list[str]
    variables: list[str]
    input_len: int
    target_index: int
    target_variable: str
    horizons: list[int]
    metrics: dict[str, dict[str, float]]


def create_app(model_path: str | Path | None = None,
               internal_token: str | None = None,
               load_model: bool = True) -> FastAPI:
    """Crea la aplicación. ``load_model=False`` sirve para probar el modo degradado."""
    app = FastAPI(
        title="Laguna Paca · ST-GNN",
        version="1.0.0",
        description="Servicio interno de inferencia. No aplica reglas ECA-Agua.",
    )

    ruta = Path(model_path or os.getenv("LAGUNA_MODEL_PATH", DEFAULT_MODEL_PATH))
    estado: dict[str, object] = {"bundle": None, "error": None}

    if load_model:
        try:
            estado["bundle"] = load_bundle(ruta)
        except FileNotFoundError:
            estado["error"] = f"No existe el artefacto del modelo: {ruta}"
        except Exception as ex:  # artefacto corrupto o incompatible
            estado["error"] = f"No se pudo cargar el modelo: {ex}"

    token = internal_token if internal_token is not None else os.getenv("IA_INTERNAL_TOKEN", "")
    lock = threading.Lock()

    def _serializable(value):
        """Convierte a JSON también los errores: NaN no es JSON válido.

        Sin esto, un cuerpo con ``NaN`` provoca un 500 al serializar la propia
        respuesta de validación, que es justo lo contrario de lo que se quiere.
        """
        if isinstance(value, float) and not math.isfinite(value):
            return str(value)
        if isinstance(value, dict):
            return {str(k): _serializable(v) for k, v in value.items()}
        if isinstance(value, (list, tuple)):
            return [_serializable(v) for v in value]
        if value is None or isinstance(value, (str, int, bool)):
            return value
        return str(value)

    @app.exception_handler(RequestValidationError)
    async def validation_handler(_request, exc: RequestValidationError):
        return JSONResponse(status_code=422, content={"detail": _serializable(exc.errors())})

    def require_token(x_internal_token: str | None = Header(default=None)) -> None:
        if token and x_internal_token != token:
            raise HTTPException(status_code=401, detail="Token interno inválido")

    def bundle_actual() -> ModelBundle:
        bundle = estado["bundle"]
        if bundle is None:
            raise HTTPException(
                status_code=503,
                detail=str(estado["error"] or "El modelo no está cargado"))
        return bundle  # type: ignore[return-value]

    @app.get("/health", response_model=HealthResponse)
    def health() -> HealthResponse:
        bundle = estado["bundle"]
        if bundle is None:
            return HealthResponse(
                status="degraded",
                model_loaded=False,
                detail=str(estado["error"] or "Modelo no cargado"),
            )
        return HealthResponse(
            status="ok",
            model_loaded=True,
            model_version=bundle.version,
            horizons=sorted(bundle.horizons),
            input_len=bundle.input_len,
            data_source=bundle.data_source,
        )

    @app.get("/model", response_model=ModelInfoResponse,
             dependencies=[Depends(require_token)])
    def model_info() -> ModelInfoResponse:
        return ModelInfoResponse(**bundle_actual().info())

    @app.post("/predict", response_model=PredictResponse,
              dependencies=[Depends(require_token)])
    def predict(body: PredictInput) -> PredictResponse:
        bundle = bundle_actual()
        if body.horizon_hours not in bundle.horizons:
            raise HTTPException(
                status_code=422,
                detail=(f"Horizonte no soportado: {body.horizon_hours}. "
                        f"Disponibles: {sorted(bundle.horizons)}"))

        artifact = bundle.horizons[body.horizon_hours]
        x = np.asarray(body.input, dtype=float)
        esperado = (len(bundle.stations), bundle.input_len, len(bundle.variables))
        if x.shape != esperado:
            raise HTTPException(
                status_code=422,
                detail=f"La ventana debe ser {esperado}, no {x.shape}")
        if not np.isfinite(x).all():
            raise HTTPException(status_code=422, detail="La ventana contiene valores no finitos")

        # El modelo se entrenó estandarizado: sin este paso la inferencia sería
        # silenciosamente incorrecta.
        estandarizado = artifact.scaler.transform(x)
        tensor = torch.as_tensor(estandarizado, dtype=torch.float32).unsqueeze(0)
        edge_index = torch.as_tensor(artifact.edge_index, dtype=torch.long)
        edge_weight = (None if artifact.edge_weight is None
                       else torch.as_tensor(artifact.edge_weight, dtype=torch.float32))

        # Un único modelo compartido: se serializa la inferencia para que dos
        # peticiones no se pisen el modo eval/train.
        with lock, torch.no_grad():
            artifact.model.eval()
            salida = artifact.model(tensor, edge_index, edge_weight)
            # (1, estaciones, horizonte) en unidades estandarizadas; el último
            # paso es el horizonte pedido.
            valores = artifact.scaler.inverse_targets(
                salida[:, :, -1].cpu().numpy(), bundle.target_index)[0]

        if not np.isfinite(valores).all():
            raise HTTPException(status_code=500, detail="La inferencia produjo valores no finitos")

        return PredictResponse(
            model_version=bundle.version,
            data_source=bundle.data_source,
            horizon_hours=body.horizon_hours,
            generated_at=datetime.now(timezone.utc),
            target_at=body.base_time + timedelta(hours=body.horizon_hours),
            predictions=[
                StationPrediction(station=estacion, ph=float(valor))
                for estacion, valor in zip(bundle.stations, valores, strict=True)
            ],
        )

    return app
