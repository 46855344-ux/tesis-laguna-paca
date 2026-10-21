"""Experimento reproducible del ST-GNN: ¿se cumple ``R² > 0.90``?

Este script es el punto de entrada de la tesis. Hace, en orden, lo mismo que
comprueba ``tests/test_training.py``, pero imprime los números en lugar de
afirmarlos:

1. genera la serie sintética de las 4 estaciones,
2. construye el grafo (gaussiano + MST ∪ KNN),
3. parte la serie cronológicamente **con hueco**,
4. ajusta el escalado **solo con el entrenamiento**,
5. entrena la ST-GNN,
6. evalúa contra el conjunto de prueba y contra la línea base trivial.

La línea base trivial —"repetir el último pH observado"— es la parte que no se
puede omitir. Un modelo que no la bata no aporta nada, aunque supere el umbral.

Uso (desde ``ia-model/``)::

    .venv\\Scripts\\python.exe train_stgnn.py
    .venv\\Scripts\\python.exe train_stgnn.py --horizons 1 3 6
    .venv\\Scripts\\python.exe train_stgnn.py --horizon 6 --epochs 300

.. warning::

   Los datos son **sintéticos**. Un R² alto aquí valida la arquitectura y el
   procedimiento, no el comportamiento real de la Laguna de Paca.
"""

from __future__ import annotations

import argparse
import sys
import time
import warnings
from datetime import datetime, timezone

import numpy as np

# PyTorch avisa de que ``torch.jit.script`` está en desuso. No lo usamos en
# ninguna parte y el aviso solo ensucia la salida del experimento, pero se emite
# al importar ``torch``: el filtro tiene que ir antes de ese import.
warnings.filterwarnings("ignore", category=FutureWarning)

import torch

from lagunapaca_gnn.dataset import VARIABLE_NAMES, simulate_lagoon
from lagunapaca_gnn.graph import adjacency_to_edge_index, gaussian_adjacency, sparsify
from lagunapaca_gnn.metrics import mean_absolute_error, r2_score, root_mean_squared_error
from lagunapaca_gnn.model import SpatioTemporalGNN
from lagunapaca_gnn.normalization import StandardScaler
from lagunapaca_gnn.registry import HorizonArtifact, ModelBundle, new_version, save_bundle
from lagunapaca_gnn.stations import PROVISIONAL_STATIONS, distance_matrix_km
from lagunapaca_gnn.training import chronological_split, evaluate_model, train_model
from lagunapaca_gnn.windows import build_windows

#: El pH es el objetivo porque es la única variable con regla ECA-Agua en el
#: backend (``pH > 9.0`` o ``pH < 6.5``). Predecir lo que dispara alertas es lo
#: que hace útil al sistema completo.
TARGET_INDEX = 0

#: Criterio de éxito declarado en la tesis.
CRITERION_R2 = 0.90


def parse_args(argv=None) -> argparse.Namespace:
    """Argumentos de línea de comandos del experimento."""
    parser = argparse.ArgumentParser(
        description="Entrena y evalúa la ST-GNN sobre la serie sintética de la laguna.")
    parser.add_argument(
        "--horizons", type=int, nargs="+", default=[6],
        help="horizontes en horas a evaluar (por defecto: 6, el escenario principal)")
    parser.add_argument(
        "--horizon", type=int, default=None,
        help="atajo para evaluar un único horizonte; tiene prioridad sobre --horizons")
    parser.add_argument(
        "--steps", type=int, default=2000,
        help="instantes horarios a simular (por defecto: 2000)")
    parser.add_argument(
        "--input-len", type=int, default=24,
        help="horas de historia que ve el modelo (por defecto: 24)")
    parser.add_argument(
        "--epochs", type=int, default=150,
        help="épocas de entrenamiento (por defecto: 150)")
    parser.add_argument(
        "--hidden", type=int, default=24,
        help="canales ocultos de la ST-GNN (por defecto: 24)")
    parser.add_argument(
        "--seed", type=int, default=42,
        help="semilla de la simulación y de la inicialización (por defecto: 42)")
    parser.add_argument(
        "--train-fraction", type=float, default=0.75,
        help="proporción inicial de la serie para entrenamiento (por defecto: 0.75)")
    parser.add_argument(
        "--quiet", action="store_true",
        help="no imprime la pérdida de cada época")
    parser.add_argument(
        "--save", type=str, default=None,
        help=("guarda un artefacto de inferencia en esta ruta "
              "(por ejemplo artifacts/stgnn.pt); con --horizons 1 3 6 "
              "el artefacto sirve los tres horizontes"))
    return parser.parse_args(argv)


def build_graph(sigma: float = 1.5, k: int = 2):
    """Grafo de la laguna en el formato que espera PyG."""
    distances = distance_matrix_km(PROVISIONAL_STATIONS)
    adjacency = gaussian_adjacency(distances, sigma=sigma)
    sparse = sparsify(adjacency, distances, k=k)
    return adjacency_to_edge_index(sparse)


def persistence_baseline(inputs: np.ndarray, horizon: int,
                         target_index: int = TARGET_INDEX) -> np.ndarray:
    """Línea base trivial: repetir el último valor observado ``horizon`` veces.

    Se calcula en unidades originales. El R² es invariante a la escala, pero el
    RMSE de la línea base solo se puede comparar si ambos están en pH.
    """
    last = inputs[:, :, -1, target_index]
    return np.repeat(last[:, :, None], horizon, axis=2)


def run_experiment(horizon: int, args: argparse.Namespace) -> dict:
    """Entrena y evalúa un horizonte. Devuelve las métricas y los tiempos."""
    started = time.perf_counter()

    distances = distance_matrix_km(PROVISIONAL_STATIONS)
    series = simulate_lagoon(distances, steps=args.steps, seed=args.seed)
    inputs, targets = build_windows(
        series, input_len=args.input_len, horizon=horizon,
        target_index=TARGET_INDEX)

    # El hueco es igual a la ventana de entrada: así ninguna ventana de prueba
    # comparte instantes con una de entrenamiento.
    train_x, train_y, test_x, test_y = chronological_split(
        inputs, targets, train_fraction=args.train_fraction, gap=args.input_len)

    # Media y desviación SOLO con entrenamiento. Si se calcularan con toda la
    # serie, el conjunto de prueba estaría filtrando información.
    scaler = StandardScaler.fit(train_x)

    # La inicialización de los pesos también es aleatoria: hay que fijarla.
    torch.manual_seed(args.seed)
    config = {
        "num_variables": len(VARIABLE_NAMES),
        "horizon": horizon,
        "hidden": args.hidden,
        "tcn_levels": 2,
        "kernel_size": 3,
        "gcn_layers": 1,
        "heads": 4,
        "dropout": 0.1,
        "target_index": TARGET_INDEX,
    }
    model = SpatioTemporalGNN(**config)

    edge_index, edge_weight = build_graph()
    history = train_model(
        model,
        scaler.transform(train_x), scaler.transform_targets(train_y, TARGET_INDEX),
        edge_index, edge_weight,
        epochs=args.epochs, batch_size=64, learning_rate=1e-2, seed=args.seed)
    trained_at = time.perf_counter()

    metrics = evaluate_model(
        model,
        scaler.transform(test_x), scaler.transform_targets(test_y, TARGET_INDEX),
        edge_index, edge_weight, scaler=scaler, target_index=TARGET_INDEX)

    baseline = persistence_baseline(test_x, horizon)
    baseline_metrics = {
        "r2": r2_score(test_y, baseline),
        "rmse": root_mean_squared_error(test_y, baseline),
        "mae": mean_absolute_error(test_y, baseline),
    }

    return {
        "horizon": horizon,
        "metrics": metrics,
        "baseline": baseline_metrics,
        "history": history,
        "train_samples": len(train_x),
        "test_samples": len(test_x),
        "scaler_mean": scaler.mean[TARGET_INDEX],
        "scaler_std": scaler.std[TARGET_INDEX],
        "train_seconds": trained_at - started,
        "total_seconds": time.perf_counter() - started,
        # Contexto necesario para guardar un artefacto de inferencia. No se
        # imprime: solo lo consume --save.
        "model": model,
        "scaler": scaler,
        "edge_index": edge_index,
        "edge_weight": edge_weight,
        "config": config,
    }


def print_report(result: dict, quiet: bool) -> None:
    """Imprime el resultado de un horizonte con su veredicto."""
    horizon = result["horizon"]
    metrics = result["metrics"]
    baseline = result["baseline"]
    history = result["history"]

    print()
    print(f"Horizonte {horizon} h  "
          f"({result['train_samples']} ventanas de entreno, "
          f"{result['test_samples']} de prueba)")
    print(f"  pH estandarizado con media={result['scaler_mean']:.4f} "
          f"y desviación={result['scaler_std']:.4f} (solo entrenamiento)")
    if not quiet:
        print(f"  Pérdida: {history[0]:.6f} (época 1) -> {history[-1]:.6f} "
              f"(época {len(history)})")
    print(f"  ST-GNN       R²={metrics['r2']:.4f}  "
          f"RMSE={metrics['rmse']:.4f}  MAE={metrics['mae']:.4f}")
    print(f"  Persistencia R²={baseline['r2']:.4f}  "
          f"RMSE={baseline['rmse']:.4f}  MAE={baseline['mae']:.4f}")

    meets = metrics["r2"] > CRITERION_R2
    beats = metrics["r2"] > baseline["r2"]
    print(f"  Criterio R² > {CRITERION_R2:.2f}: "
          f"{'CUMPLE' if meets else 'NO CUMPLE'}")
    print(f"  Supera la persistencia: {'SI' if beats else 'NO'}")
    print(f"  Tiempo: {result['total_seconds']:.1f} s "
          f"(entrenamiento: {result['train_seconds']:.1f} s)")


def main(argv=None) -> int:
    """Ejecuta el experimento. Devuelve 0 si el criterio se cumple en todos."""
    args = parse_args(argv)
    horizons = [args.horizon] if args.horizon is not None else args.horizons

    invalid = [h for h in horizons if h <= 0]
    if invalid:
        print(f"error: los horizontes deben ser positivos, no {invalid}",
              file=sys.stderr)
        return 2
    if args.steps <= args.input_len * 2:
        print("error: --steps debe superar el doble de --input-len",
              file=sys.stderr)
        return 2

    print("ST-GNN para la Laguna de Paca (datos SINTÉTICOS: valida la arquitectura)")
    print(f"Objetivo: {VARIABLE_NAMES[TARGET_INDEX]}  |  "
          f"estaciones: {len(PROVISIONAL_STATIONS)}  |  "
          f"pasos: {args.steps}  |  ventana: {args.input_len} h  |  "
          f"épocas: {args.epochs}  |  semilla: {args.seed}")

    results = [run_experiment(h, args) for h in horizons]
    for result in results:
        print_report(result, args.quiet)

    all_meet = all(r["metrics"]["r2"] > CRITERION_R2 for r in results)
    all_beat = all(r["metrics"]["r2"] > r["baseline"]["r2"] for r in results)

    print()
    print("=" * 62)
    print(f"{'Horizonte':>10} {'R² modelo':>11} {'R² persis.':>12} {'RMSE':>9}")
    for result in results:
        print(f"{result['horizon']:>8} h "
              f"{result['metrics']['r2']:>11.4f} "
              f"{result['baseline']['r2']:>12.4f} "
              f"{result['metrics']['rmse']:>9.4f}")
    print("=" * 62)

    if all_meet and all_beat:
        print(f"RESULTADO: el criterio R² > {CRITERION_R2:.2f} se cumple y el "
              f"modelo supera la persistencia.")
    else:
        print(f"RESULTADO: el criterio R² > {CRITERION_R2:.2f} NO se cumple "
              f"en todos los horizontes evaluados.", file=sys.stderr)

    if args.save:
        bundle = ModelBundle(
            version=new_version(args.seed, args.input_len, args.epochs),
            created_at=datetime.now(timezone.utc).isoformat(),
            data_source="synthetic",
            stations=tuple(station.id for station in PROVISIONAL_STATIONS),
            variables=tuple(VARIABLE_NAMES),
            input_len=args.input_len,
            target_index=TARGET_INDEX,
            horizons={
                result["horizon"]: HorizonArtifact(
                    horizon=result["horizon"],
                    model=result["model"],
                    scaler=result["scaler"],
                    edge_index=result["edge_index"],
                    edge_weight=result["edge_weight"],
                    config=result["config"],
                    metrics=result["metrics"],
                )
                for result in results
            },
        )
        destino = save_bundle(args.save, bundle)
        print(f"Artefacto guardado en {destino} (versión {bundle.version}, "
              f"horizontes {sorted(bundle.horizons)})")

    if all_meet and all_beat:
        return 0

    return 1


if __name__ == "__main__":
    raise SystemExit(main())
