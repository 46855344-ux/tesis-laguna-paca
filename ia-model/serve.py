"""Punto de entrada del servicio de inferencia.

Uso local (desde ``ia-model/``)::

    .venv\\Scripts\\python.exe -m uvicorn serve:app --host 127.0.0.1 --port 8000

Variables:
    ``LAGUNA_MODEL_PATH``  ruta del artefacto (por defecto ``artifacts/stgnn.pt``)
    ``IA_INTERNAL_TOKEN``  token compartido con Spring Boot (opcional en local)
    ``PORT``               puerto de escucha (por defecto 8000)
"""

from __future__ import annotations

import os

import uvicorn

from lagunapaca_gnn.api import create_app

app = create_app()


def main() -> None:
    uvicorn.run(
        "serve:app",
        host="0.0.0.0",
        port=int(os.getenv("PORT", "8000")),
        log_level=os.getenv("LOG_LEVEL", "info"),
    )


if __name__ == "__main__":
    main()
