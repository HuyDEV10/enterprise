from __future__ import annotations

from datetime import date
from typing import Sequence

import numpy as np
import pandas as pd

LAGS = (1, 7, 14, 28)
ROLLING_MEAN_WINDOWS = (7, 14, 28)
ROLLING_STD_WINDOWS = (7, 28)
MIN_HISTORY_DAYS = 56

FEATURE_COLUMNS = [
    "lag_1",
    "lag_7",
    "lag_14",
    "lag_28",
    "rolling_mean_7",
    "rolling_mean_14",
    "rolling_mean_28",
    "rolling_std_7",
    "rolling_std_28",
    "day_of_week",
    "month",
    "day_of_month",
]


def add_training_features(frame: pd.DataFrame) -> pd.DataFrame:
    required = {"series_id", "date", "quantity"}
    missing = required.difference(frame.columns)
    if missing:
        raise ValueError(f"Missing required columns: {sorted(missing)}")

    result = frame.copy()
    result["date"] = pd.to_datetime(result["date"])
    result = result.sort_values(["series_id", "date"]).reset_index(drop=True)

    grouped = result.groupby("series_id", sort=False)["quantity"]
    for lag in LAGS:
        result[f"lag_{lag}"] = grouped.shift(lag)

    for window in ROLLING_MEAN_WINDOWS:
        result[f"rolling_mean_{window}"] = result.groupby("series_id", sort=False)["quantity"].transform(
            lambda series: series.shift(1).rolling(window=window, min_periods=window).mean()
        )

    for window in ROLLING_STD_WINDOWS:
        result[f"rolling_std_{window}"] = result.groupby("series_id", sort=False)["quantity"].transform(
            lambda series: series.shift(1).rolling(window=window, min_periods=window).std(ddof=0)
        )

    result["day_of_week"] = result["date"].dt.dayofweek.astype("int8")
    result["month"] = result["date"].dt.month.astype("int8")
    result["day_of_month"] = result["date"].dt.day.astype("int8")
    return result


def feature_vector_from_history(history: Sequence[float], target_date: date) -> list[float]:
    if len(history) < MIN_HISTORY_DAYS:
        raise ValueError(f"At least {MIN_HISTORY_DAYS} days of history are required")

    values = np.asarray(history, dtype="float64")
    if np.isnan(values).any() or np.isinf(values).any():
        raise ValueError("History contains non-finite quantities")
    if (values < 0).any():
        raise ValueError("History contains negative quantities")

    return [
        float(values[-1]),
        float(values[-7]),
        float(values[-14]),
        float(values[-28]),
        float(values[-7:].mean()),
        float(values[-14:].mean()),
        float(values[-28:].mean()),
        float(values[-7:].std(ddof=0)),
        float(values[-28:].std(ddof=0)),
        float(target_date.weekday()),
        float(target_date.month),
        float(target_date.day),
    ]
