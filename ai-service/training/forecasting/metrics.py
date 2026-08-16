from __future__ import annotations

import math

import numpy as np


def forecast_metrics(actual: np.ndarray, predicted: np.ndarray) -> dict[str, float | None]:
    actual_values = np.asarray(actual, dtype="float64")
    predicted_values = np.asarray(predicted, dtype="float64")
    if actual_values.shape != predicted_values.shape:
        raise ValueError("actual and predicted must have the same shape")
    if actual_values.size == 0:
        raise ValueError("Cannot evaluate an empty forecast")

    errors = actual_values - predicted_values
    mae = float(np.mean(np.abs(errors)))
    rmse = float(math.sqrt(np.mean(np.square(errors))))
    denominator = float(np.sum(np.abs(actual_values)))
    wape = None if denominator == 0.0 else float(np.sum(np.abs(errors)) / denominator * 100.0)
    return {"mae": mae, "rmse": rmse, "wape": wape}
