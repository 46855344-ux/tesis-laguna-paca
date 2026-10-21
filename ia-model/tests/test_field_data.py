"""Pruebas de la importación de datos de campo.

Los CSV de estas pruebas son *fixtures*: no son mediciones de la Laguna de Paca.
Lo que se fija aquí es el contrato de carga, no un resultado científico.
"""

from __future__ import annotations

import csv
from datetime import datetime, timedelta, timezone
from pathlib import Path

import numpy as np
import pytest

from lagunapaca_gnn.dataset import VARIABLE_NAMES
from lagunapaca_gnn.field_data import FieldDataError, load_field_csv

BASE = datetime(2026, 1, 1, 0, 0, tzinfo=timezone.utc)
STATIONS = ("N1", "N2", "N3", "N4")
BASES = (7.0, 15.0, 18.0, 320.0)
STEPS = (0.01, 0.1, 0.02, 0.5)
HEADER = ("timestamp", "node_id", *VARIABLE_NAMES)


def _stamp(hour: float, minute: int = 0, tz=timezone.utc) -> str:
    return (BASE + timedelta(hours=hour, minutes=minute)).astimezone(tz).isoformat()


def _value(station: str, variable: int, hour: float) -> float:
    station_index = STATIONS.index(station)
    return BASES[variable] + station_index * 0.1 + hour * STEPS[variable]


def _row(station: str, hour: float, values=None, minute: int = 0,
         timestamp: str | None = None) -> list:
    if values is None:
        values = [_value(station, variable, hour)
                  for variable in range(len(VARIABLE_NAMES))]
    return [timestamp or _stamp(hour, minute), station, *values]


def _write(path: Path, rows, header=HEADER) -> Path:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(header)
        writer.writerows(rows)
    return path


def _series_rows(hours: int, stations=STATIONS, start: int = 0):
    return [_row(station, hour)
            for hour in range(start, start + hours)
            for station in stations]


def _csv(tmp_path: Path, rows, name: str = "campo.csv", header=HEADER) -> Path:
    return _write(tmp_path / name, rows, header)


def test_carga_csv_valido_devuelve_serie_y_metadatos(tmp_path):
    series = load_field_csv(_csv(tmp_path, _series_rows(24)))

    assert series.values.shape == (4, 24, len(VARIABLE_NAMES))
    assert series.station_ids == STATIONS
    assert series.variables == VARIABLE_NAMES
    assert series.hours == 24
    assert series.timestamps[0] == BASE
    assert series.timestamps[-1] == BASE + timedelta(hours=23)
    assert series.rows_read == 96
    assert series.rows_used == 96
    assert series.aggregated_buckets == 0
    assert series.interpolated_slots == 0
    assert series.max_interpolated_gap == 0
    assert series.data_source == "field"
    assert series.source_path.endswith("campo.csv")

    for station_index, station in enumerate(STATIONS):
        np.testing.assert_allclose(
            series.values[station_index, 7, :],
            [_value(station, variable, 7) for variable in range(4)])

    assert not np.isnan(series.values).any()
    assert series.fingerprint == load_field_csv(
        tmp_path / "campo.csv").fingerprint


def test_promedia_las_muestras_de_la_misma_hora(tmp_path):
    rows = []
    for hour in range(3):
        for station in STATIONS:
            rows.append(_row(station, hour, [7.0, 15.0, 18.0, 320.0],
                             minute=0))
            rows.append(_row(station, hour, [7.2, 16.0, 19.0, 322.0],
                             minute=30))

    series = load_field_csv(_csv(tmp_path, rows))

    assert series.hours == 3
    assert series.rows_read == 24
    assert series.rows_used == 24
    assert series.aggregated_buckets == 12
    assert series.values[0, 0, 0] == pytest.approx(7.1)
    assert series.values[0, 0, 1] == pytest.approx(15.5)
    assert series.values[0, 0, 3] == pytest.approx(321.0)


def test_timestamp_sin_zona_horaria_falla(tmp_path):
    rows = [_row("N1", 0, timestamp="2026-01-01T00:00:00")]

    with pytest.raises(FieldDataError, match="zona horaria"):
        load_field_csv(_csv(tmp_path, rows))


def test_timestamp_con_offset_se_normaliza_a_utc(tmp_path):
    lima = timezone(timedelta(hours=-5))
    local_start = datetime(2026, 1, 1, 0, 0, tzinfo=lima)
    rows = []
    for hour in range(6):
        stamp = (local_start + timedelta(hours=hour)).isoformat()
        for station in STATIONS:
            rows.append(_row(station, hour, timestamp=stamp))

    series = load_field_csv(_csv(tmp_path, rows))

    assert series.timestamps[0] == datetime(2026, 1, 1, 5, tzinfo=timezone.utc)
    assert series.hours == 6


def test_nodo_desconocido_falla(tmp_path):
    rows = [_row("N9", 0, [7.0, 15.0, 18.0, 320.0])]

    with pytest.raises(FieldDataError, match="N9.*desconocido"):
        load_field_csv(_csv(tmp_path, rows))


def test_columna_obligatoria_faltante_falla(tmp_path):
    header = ("timestamp", "node_id", "ph", "turbidity_ntu", "temp_c")
    path = _csv(tmp_path, [], header=header)

    with pytest.raises(FieldDataError, match="tds_ppm"):
        load_field_csv(path)


def test_cabecera_duplicada_falla(tmp_path):
    header = ("timestamp", "timestamp", "node_id",
              "ph", "turbidity_ntu", "temp_c", "tds_ppm")
    path = _csv(tmp_path, [], header=header)

    with pytest.raises(FieldDataError, match="columnas duplicadas"):
        load_field_csv(path)


def test_valor_no_numerico_falla(tmp_path):
    rows = [_row("N1", 0, ["abc", 15.0, 18.0, 320.0])]

    with pytest.raises(FieldDataError, match="no es un número"):
        load_field_csv(_csv(tmp_path, rows))


def test_valor_no_finito_falla(tmp_path):
    rows = [_row("N1", 0, ["nan", 15.0, 18.0, 320.0])]

    with pytest.raises(FieldDataError, match="no es finito"):
        load_field_csv(_csv(tmp_path, rows))


def test_duplicado_exacto_falla(tmp_path):
    rows = [_row("N1", 0), _row("N1", 0)]

    with pytest.raises(FieldDataError, match="duplicado"):
        load_field_csv(_csv(tmp_path, rows))


def test_hueco_corto_se_interpola_solo_si_se_permite(tmp_path):
    rows = [row for row in _series_rows(10) if row[0] != _stamp(5)]
    path = _csv(tmp_path, rows)

    with pytest.raises(FieldDataError, match="1 h seguidas"):
        load_field_csv(path)

    series = load_field_csv(path, max_gap_hours=1)

    assert series.interpolated_slots == 4
    assert series.max_interpolated_gap == 1
    assert series.values[0, 5, 0] == pytest.approx(_value("N1", 0, 5))
    assert series.values[3, 5, 3] == pytest.approx(_value("N4", 3, 5))


def test_hueco_mayor_al_maximo_falla(tmp_path):
    rows = [row for row in _series_rows(10)
            if row[0] not in {_stamp(4), _stamp(5), _stamp(6)}]

    with pytest.raises(FieldDataError, match="3 h seguidas"):
        load_field_csv(_csv(tmp_path, rows), max_gap_hours=2)


def test_recorta_al_rango_comun_sin_extrapolar(tmp_path):
    ranges = {"N1": (0, 9), "N2": (0, 9), "N3": (2, 9), "N4": (0, 7)}
    rows = []
    for station, (start, end) in ranges.items():
        rows.extend(_row(station, hour) for hour in range(start, end + 1))

    series = load_field_csv(_csv(tmp_path, rows))

    assert series.hours == 6
    assert series.timestamps[0] == BASE + timedelta(hours=2)
    assert series.timestamps[-1] == BASE + timedelta(hours=7)
    assert series.rows_read == 36
    assert series.rows_used == 24
    assert series.values[2, 0, 0] == pytest.approx(_value("N3", 0, 2))


def test_sin_solapamiento_temporal_falla(tmp_path):
    rows = []
    for station in ("N1", "N3", "N4"):
        rows.extend(_row(station, hour) for hour in range(0, 3))
    rows.extend(_row("N2", hour) for hour in range(5, 8))

    with pytest.raises(FieldDataError, match="no comparten"):
        load_field_csv(_csv(tmp_path, rows))


def test_estacion_sin_datos_falla(tmp_path):
    rows = [row for row in _series_rows(5) if row[1] != "N4"]

    with pytest.raises(FieldDataError, match="N4"):
        load_field_csv(_csv(tmp_path, rows))


def test_columnas_extra_se_ignoran(tmp_path):
    header = (*HEADER, "sample_id")
    rows = [(*_row(station, hour), f"id-{station}-{hour}")
            for hour in range(3) for station in STATIONS]

    series = load_field_csv(_csv(tmp_path, rows, header=header))

    assert series.hours == 3
    assert series.rows_read == 12


def test_archivo_inexistente_falla(tmp_path):
    with pytest.raises(FileNotFoundError):
        load_field_csv(tmp_path / "no-existe.csv")


def test_archivo_vacio_falla(tmp_path):
    path = tmp_path / "vacio.csv"
    path.write_text("", encoding="utf-8")

    with pytest.raises(FieldDataError, match="vacío"):
        load_field_csv(path)


def test_max_gap_negativo_falla(tmp_path):
    with pytest.raises(ValueError, match="no puede ser negativo"):
        load_field_csv(_csv(tmp_path, _series_rows(3)), max_gap_hours=-1)
