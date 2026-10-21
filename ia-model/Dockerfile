# syntax=docker/dockerfile:1

# La rueda de PyTorch para CPU no está en PyPI con el sufijo "+cpu": se instala
# desde el índice propio de PyTorch antes de las demás dependencias.
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

RUN useradd --system --uid 1001 laguna

COPY requirements.txt .
RUN pip install --index-url https://download.pytorch.org/whl/cpu torch==2.14.1+cpu \
    && pip install -r requirements.txt

COPY lagunapaca_gnn ./lagunapaca_gnn
COPY serve.py .
# Artefacto generado con train_stgnn.py --save artifacts/stgnn.pt.
# Es sintético: el propio /model lo declara y el panel lo muestra.
COPY artifacts ./artifacts

RUN chown -R laguna:laguna /app

USER laguna
EXPOSE 8000

# El servicio es interno: no se publica al host en compose.yaml.
CMD ["python", "-m", "uvicorn", "serve:app", "--host", "0.0.0.0", "--port", "8000"]
