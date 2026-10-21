"""Carga de mediciones de campo en el tensor que consume la ST-GNN.

El modelo no entrena directamente con el CSV: necesita una serie regular
``[estaciones, tiempo, variables]``. Este módulo hace esa conversión sin
inventar datos y falla con un mensaje claro cuando el archivo no cumple el
contrato.

Formato esperado (una fila por muestra, en formato largo)::

    timestamp,node_id,ph,turbidity_ntu,temp_c,tds_ppm
    2026-10-20T08:00:00-05:00,N1,7.21,15.4,18.5,320
    2026-10-20T08:05:00-05:00,N1,7.22,15.6,18.6,321
    2026-10-20T08:00:00-05:00,N2,7.18,14.9,18.4,318

Decisiones que conviene no deshacer:

* **La zona horaria es obligatoria.** Un ``timestamp`` sin offset no se puede
  alinear con los demás ni convertir a UTC sin adivinar. Se acepta ISO 8601 con
  ``Z`` o con offset (por ejemplo ``-05:00`` para Lima).
* **Se remuestrea a una hora.** Si hay varias muestras dentro de la misma hora
  se promedian; así una serie de 5 minutos no se convierte en ventanas de 5
  minutos cuando el modelo y los horizontes están definidos en horas.
* **El rango común se recorta, no se extrapola.** Se usa desde la primera hora
  en que **todas** las estaciones tienen datos hasta la última en que todas los
  tienen. Fuera de ese rango no se rellena nada.
* **Los huecos solo se interpolan si se pide explícitamente.** Por defecto
  ``max_gap_hours=0``: cualquier hora faltante detiene la carga. Si el usuario
  acepta interpolación, se informa cuántos instantes se rellenaron y el hueco
  máximo. No se trata de "limpiar" el dato: es una decisión que debe quedar
  visible en el reporte.
* **Aquí no se evalúa el ECA-Agua.** El modelo puede ver pH fuera de rango; la
  alerta la decide el backend con :class:`EcaWaterQualityRule`. Duplicar esa
  regla en el preprocesamiento crearía dos verdades distintas.

.. warning::

   Un CSV etiquetado como "campo" no convierte por sí solo los datos en
   mediciones reales. El archivo debe provenir de los sensores instalados; los
   *fixtures* de los tests son solo eso, fixtures.
"""

from __future__ import annotations

import csv
import hashlib
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Sequence

import numpy as np

from lagunapaca_gnn.dataset import VARIABLE_NAMES
from lagunapaca_gnn.stations import PROVISIONAL_STATIONS

#: Una hora, la unidad temporal del modelo y de los horizontes de predicción.
HOUR = timedelta(hours=1)

#: Estaciones que se esperan si no se pasa una lista explícita.
DEFAULT_STATION_IDS: tuple[str, ...] = tuple(
    station.id for station in PROVISIONAL_STATIONS)

#: Columnas mínimas del CSV, en minúsculas.
REQUIRED_COLUMNS: tuple[str, ...] = ("timestamp", "node_id", *VARIABLE_NAMES)


class FieldDataError(ValueError):
    """El CSV de campo no cumple el contrato o no se puede convertir en serie."""


@dataclass(frozen=True)
class FieldSeries:
    """Serie regular de campo lista para :func:`build_windows`.

    ``values`` tiene forma ``(estaciones, horas, variables)``. Los
    ``timestamps`` están en UTC, uno por hora, y son la referencia para saber
    qué instante predice cada ventana. ``rows_read`` y ``rows_used`` permiten
    auditar cuántas filas del CSV quedaron fuera del rango común.
    """

    values: np.ndarray
    timestamps: tuple[datetime, ...]
    station_ids: tuple[str, ...]
    variables: tuple[str, ...]
    rows_read: int
    rows_used: int
    aggregated_buckets: int
    interpolated_slots: int
    max_interpolated_gap: int
    source_path: str = ""

    @property
    def data_source(self) -> str:
        """Origen que se guarda en el artefacto: siempre ``field``."""
        return "field"

    @property
    def hours(self) -> int:
        """Número de instantes horarios de la serie."""
        return len(self.timestamps)

    @property
    def fingerprint(self) -> str:
        """Huella SHA-256 del contenido, para versionar el artefacto.

        Dos CSV con los mismos valores, estaciones y horas producen la misma
        huella aunque cambie el nombre del archivo. El versionado de un modelo
        de campo no puede depender solo de la semilla: dos campañas distintas
        con los mismos hiperparámetros no son el mismo experimento.
        """
        digest = hashlib.sha256()
        digest.update(str(self.values.shape).encode("ascii"))
        digest.update(np.ascontiguousarray(self.values, dtype="<f8").tobytes())
        digest.update(
            "|".join(timestamp.isoformat() for timestamp in self.timestamps)
            .encode("utf-8"))
        digest.update("|".join(self.station_ids).encode("utf-8"))
        digest.update("|".join(self.variables).encode("utf-8"))
        return digest.hexdigest()


def _parse_timestamp(raw: str, line: int) -> datetime:
    """Convierte un timestamp ISO 8601 a UTC, exigiendo zona horaria."""
    text = raw.strip()
    if not text:
        raise FieldDataError(f"línea {line}: el timestamp está vacío")

    if text.endswith(("Z", "z")):
        text = text[:-1] + "+00:00"

    try:
        value = datetime.fromisoformat(text)
    except ValueError as error:
        raise FieldDataError(
            f"línea {line}: timestamp inválido «{raw.strip()}»; usa ISO 8601 "
            "con zona horaria, por ejemplo 2026-10-20T08:00:00-05:00"
        ) from error

    if value.tzinfo is None or value.utcoffset() is None:
        raise FieldDataError(
            f"línea {line}: el timestamp «{raw.strip()}» no incluye zona "
            "horaria; sin ella no se puede alinear la serie"
        )

    return value.astimezone(timezone.utc)


def _missing_runs(mask: np.ndarray) -> list[np.ndarray]:
    """Devuelve los índices de cada racha consecutiva de ``True``."""
    indices = np.flatnonzero(mask)
    if indices.size == 0:
        return []

    breaks = np.flatnonzero(np.diff(indices) > 1)
    starts = np.r_[0, breaks + 1]
    ends = np.r_[breaks + 1, indices.size]
    return [indices[start:end] for start, end in zip(starts, ends)]


def load_field_csv(path: str | Path, station_ids: Sequence[str] | None = None,
                   max_gap_hours: int = 0) -> FieldSeries:
    """Carga un CSV de campo y lo convierte en una serie horaria regular.

    Args:
        path: ruta del CSV en formato largo.
        station_ids: estaciones esperadas, en el orden del eje de nodos. Por
            defecto, ``N1``…``N4`` de :data:`PROVISIONAL_STATIONS`.
        max_gap_hours: máximo de horas consecutivas que se pueden interpolar.
            ``0`` (por defecto) prohíbe inventar cualquier instante.

    Returns:
        Una :class:`FieldSeries` con la serie, sus timestamps UTC y metadatos
        de auditoría (filas leídas/usadas, promedios por hora y huecos
        interpolados).

    Raises:
        FileNotFoundError: si la ruta no existe.
        FieldDataError: si falta una columna, hay un nodo desconocido, un
            timestamp sin zona, un valor no finito, un duplicado exacto, una
            estación sin datos, un hueco mayor al permitido o no hay
            solapamiento temporal entre estaciones.
        ValueError: si ``max_gap_hours`` es negativo o la lista de estaciones
            está vacía o tiene duplicados.
    """
    if max_gap_hours < 0:
        raise ValueError(
            f"max_gap_hours no puede ser negativo, no {max_gap_hours}")

    stations = tuple(station_ids) if station_ids is not None else DEFAULT_STATION_IDS
    if not stations:
        raise ValueError("la lista de estaciones no puede estar vacía")
    if len(set(stations)) != len(stations):
        raise ValueError("la lista de estaciones no puede tener duplicados")

    origen = Path(path).expanduser()
    if not origen.is_file():
        raise FileNotFoundError(f"No existe el CSV de campo: {origen}")

    allowed = set(stations)
    buckets: dict[tuple[str, datetime], list[list[float]]] = {}
    seen: dict[tuple[str, datetime], int] = {}
    rows_read = 0

    with origen.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.reader(handle)
        try:
            raw_header = next(reader)
        except StopIteration as error:
            raise FieldDataError(
                f"El CSV está vacío: {origen}") from error

        header = [name.strip().lower() for name in raw_header]
        if not header or any(not name for name in header):
            raise FieldDataError("La cabecera del CSV tiene una columna sin nombre")
        if len(set(header)) != len(header):
            raise FieldDataError("La cabecera del CSV tiene columnas duplicadas")

        missing_columns = [name for name in REQUIRED_COLUMNS if name not in header]
        if missing_columns:
            raise FieldDataError(
                "Faltan columnas obligatorias en el CSV: "
                + ", ".join(missing_columns)
                + ". Se esperan: " + ", ".join(REQUIRED_COLUMNS))

        positions = {name: header.index(name) for name in REQUIRED_COLUMNS}

        for line, row in enumerate(reader, start=2):
            if not row:
                continue
            if len(row) != len(header):
                raise FieldDataError(
                    f"línea {line}: tiene {len(row)} columnas y la cabecera "
                    f"tiene {len(header)}")

            timestamp = _parse_timestamp(row[positions["timestamp"]], line)
            node = row[positions["node_id"]].strip()
            if node not in allowed:
                raise FieldDataError(
                    f"línea {line}: nodo «{node}» desconocido; se esperan "
                    + ", ".join(stations))

            key = (node, timestamp)
            if key in seen:
                raise FieldDataError(
                    f"línea {line}: timestamp duplicado para {node} "
                    f"({timestamp.isoformat()}); ya aparecía en la línea "
                    f"{seen[key]}")
            seen[key] = line

            values: list[float] = []
            for variable in VARIABLE_NAMES:
                raw_value = row[positions[variable]].strip()
                if not raw_value:
                    raise FieldDataError(
                        f"línea {line}: la variable {variable} está vacía")
                try:
                    value = float(raw_value)
                except ValueError as error:
                    raise FieldDataError(
                        f"línea {line}: {variable}=«{raw_value}» no es un "
                        "número; usa el punto como separador decimal"
                    ) from error
                if not np.isfinite(value):
                    raise FieldDataError(
                        f"línea {line}: {variable}=«{raw_value}» no es finito")
                values.append(value)

            bucket = timestamp.replace(minute=0, second=0, microsecond=0)
            buckets.setdefault((node, bucket), []).append(values)
            rows_read += 1

    if rows_read == 0:
        raise FieldDataError(f"El CSV no contiene filas de datos: {origen}")

    first_by_station: dict[str, datetime] = {}
    last_by_station: dict[str, datetime] = {}
    for node, bucket in buckets:
        first_by_station[node] = min(
            first_by_station.get(node, bucket), bucket)
        last_by_station[node] = max(
            last_by_station.get(node, bucket), bucket)

    without_data = [station for station in stations if station not in first_by_station]
    if without_data:
        raise FieldDataError(
            "No hay filas para las estaciones: " + ", ".join(without_data))

    start = max(first_by_station[station] for station in stations)
    end = min(last_by_station[station] for station in stations)
    if start > end:
        ranges = ", ".join(
            f"{station} "
            f"{first_by_station[station]:%Y-%m-%d %H:%M}–"
            f"{last_by_station[station]:%Y-%m-%d %H:%M} UTC"
            for station in stations)
        raise FieldDataError(
            "Las estaciones no comparten ningún intervalo horario: " + ranges)

    hours = int((end - start) // HOUR) + 1
    timestamps = tuple(start + index * HOUR for index in range(hours))
    values = np.full((len(stations), hours, len(VARIABLE_NAMES)), np.nan)
    station_index = {station: index for index, station in enumerate(stations)}

    aggregated_buckets = 0
    rows_used = 0
    for (node, bucket), samples in buckets.items():
        if bucket < start or bucket > end:
            continue
        hour_index = int((bucket - start) // HOUR)
        values[station_index[node], hour_index, :] = np.mean(
            np.asarray(samples, dtype=float), axis=0)
        if len(samples) > 1:
            aggregated_buckets += 1
        rows_used += len(samples)

    interpolated_slots = 0
    max_interpolated_gap = 0
    for station in stations:
        index = station_index[station]
        mask = np.isnan(values[index, :, 0])
        runs = _missing_runs(mask)
        if not runs:
            continue

        for run in runs:
            if len(run) > max_gap_hours:
                first = timestamps[int(run[0])]
                last = timestamps[int(run[-1])]
                raise FieldDataError(
                    f"{station}: faltan {len(run)} h seguidas entre "
                    f"{first.isoformat()} y {last.isoformat()}; el máximo "
                    f"permitido es {max_gap_hours}. Completa el CSV o sube "
                    "--max-gap-hours de forma consciente.")
            interpolated_slots += len(run)
            max_interpolated_gap = max(max_interpolated_gap, len(run))

        valid = np.flatnonzero(~mask)
        for variable_index in range(len(VARIABLE_NAMES)):
            values[index, :, variable_index] = np.interp(
                np.arange(hours), valid,
                values[index, valid, variable_index])

    if not np.isfinite(values).all():
        raise FieldDataError(
            "La serie quedó con valores no finitos después del remuestreo; "
            "revisa el CSV")

    return FieldSeries(
        values=values,
        timestamps=timestamps,
        station_ids=stations,
        variables=tuple(VARIABLE_NAMES),
        rows_read=rows_read,
        rows_used=rows_used,
        aggregated_buckets=aggregated_buckets,
        interpolated_slots=interpolated_slots,
        max_interpolated_gap=max_interpolated_gap,
        source_path=str(origen),
    )
