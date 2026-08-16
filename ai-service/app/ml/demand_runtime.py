from __future__ import annotations

from datetime import date, timedelta
from typing import Any, Sequence

import numpy as np

from app.ml.demand_features import FEATURE_COLUMNS, feature_vector_from_history

BASELINE_SEASONAL_NAIVE_7 = "SEASONAL_NAIVE_7"
BASELINE_ROLLING_MEAN_28 = "ROLLING_MEAN_28"


def predict_next(bundle: dict[str, Any], history: Sequence[float], target_date: date) -> float:
    model_name = str(bundle.get("model_name", ""))

    if model_name == BASELINE_SEASONAL_NAIVE_7:
        raw_prediction = float(history[-7])
    elif model_name == BASELINE_ROLLING_MEAN_28:
        raw_prediction = float(np.asarray(history[-28:], dtype="float64").mean())
    else:
        estimator = bundle.get("estimator")
        if estimator is None:
            raise ValueError(f"Model bundle '{model_name}' does not contain an estimator")
        feature_columns = bundle.get("feature_columns")
        if feature_columns != FEATURE_COLUMNS:
            raise ValueError("Model feature contract does not match the current service")
        vector = feature_vector_from_history(history, target_date)
        raw_prediction = float(estimator.predict([vector])[0])

    if not np.isfinite(raw_prediction):
        raise ValueError("Model returned a non-finite forecast")
    return max(0.0, raw_prediction)


def recursive_forecast(
    bundle: dict[str, Any],
    history: Sequence[float],
    last_history_date: date,
    horizon_days: int,
) -> list[tuple[date, float]]:
    if horizon_days < 1 or horizon_days > 28:
        raise ValueError("horizon_days must be between 1 and 28")

    working_history = [float(value) for value in history]
    predictions: list[tuple[date, float]] = []

    for step in range(1, horizon_days + 1):
        forecast_date = last_history_date + timedelta(days=step)
        prediction = predict_next(bundle, working_history, forecast_date)
        working_history.append(prediction)
        predictions.append((forecast_date, prediction))

    return predictions
