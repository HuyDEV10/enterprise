from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.linear_model import Ridge
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from app.ml.demand_features import FEATURE_COLUMNS, MIN_HISTORY_DAYS, add_training_features, feature_vector_from_history
from app.ml.demand_runtime import BASELINE_ROLLING_MEAN_28, BASELINE_SEASONAL_NAIVE_7
from training.forecasting.m5_dataset import load_m5_subset, validate_m5_dataset
from training.forecasting.metrics import forecast_metrics

TRAIN_END_DAY = 1885
VALIDATION_START_DAY = 1886
VALIDATION_END_DAY = 1913
TEST_START_DAY = 1914
TEST_END_DAY = 1941
SELECTION_METRIC = "wape"

MODEL_BUILDERS = {
    "RIDGE": lambda: Pipeline([("scale", StandardScaler()), ("model", Ridge(alpha=1.0))]),
    "HIST_GRADIENT_BOOSTING": lambda: HistGradientBoostingRegressor(
        learning_rate=0.08,
        max_iter=180,
        max_leaf_nodes=31,
        l2_regularization=1.0,
        random_state=42,
    ),
}


def _bundle(model_name: str, estimator: Any | None = None) -> dict[str, Any]:
    return {
        "artifact_version": 1,
        "model_name": model_name,
        "estimator": estimator,
        "feature_columns": FEATURE_COLUMNS,
        "minimum_history_days": MIN_HISTORY_DAYS,
        "forecast_strategy": "recursive_one_step",
    }


def _predict_candidate(bundle: dict[str, Any], history_frame: pd.DataFrame, evaluation_frame: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
    histories = {
        series_id: group.sort_values("date")["quantity"].astype(float).tolist()
        for series_id, group in history_frame.groupby("series_id", sort=False)
    }
    actual_lookup = {
        (row.series_id, row.date.date()): float(row.quantity)
        for row in evaluation_frame.itertuples(index=False)
    }
    series_ids = sorted(histories)
    forecast_dates = sorted(evaluation_frame["date"].dt.date.unique())

    actual_values: list[float] = []
    predicted_values: list[float] = []

    for forecast_date in forecast_dates:
        if bundle["model_name"] == BASELINE_SEASONAL_NAIVE_7:
            day_predictions = np.asarray([histories[series_id][-7] for series_id in series_ids], dtype="float64")
        elif bundle["model_name"] == BASELINE_ROLLING_MEAN_28:
            day_predictions = np.asarray([float(np.mean(histories[series_id][-28:])) for series_id in series_ids], dtype="float64")
        else:
            matrix = pd.DataFrame(
                [
                    feature_vector_from_history(histories[series_id], forecast_date)
                    for series_id in series_ids
                ],
                columns=FEATURE_COLUMNS,
            )
            day_predictions = np.asarray(bundle["estimator"].predict(matrix), dtype="float64")

        day_predictions = np.maximum(day_predictions, 0.0)
        for series_id, prediction in zip(series_ids, day_predictions, strict=True):
            key = (series_id, forecast_date)
            if key not in actual_lookup:
                raise ValueError(f"Missing actual value for {series_id} on {forecast_date}")
            actual_values.append(actual_lookup[key])
            predicted_values.append(float(prediction))
            histories[series_id].append(float(prediction))

    return np.asarray(actual_values), np.asarray(predicted_values)


def _fit_estimator(name: str, feature_frame: pd.DataFrame) -> Any:
    estimator = MODEL_BUILDERS[name]()
    clean = feature_frame.dropna(subset=FEATURE_COLUMNS + ["quantity"])
    estimator.fit(clean[FEATURE_COLUMNS], clean["quantity"].astype(float))
    return estimator


def _score_for_selection(metrics: dict[str, float | None]) -> float:
    value = metrics.get(SELECTION_METRIC)
    return float("inf") if value is None else float(value)


def train(args: argparse.Namespace) -> dict[str, Any]:
    validation = validate_m5_dataset(args.data_dir, verify_md5=args.verify_md5)
    raw = load_m5_subset(args.data_dir, args.store_id, args.dept_id, args.max_series)
    featured = add_training_features(raw)

    train_raw = raw[raw["day_number"] <= TRAIN_END_DAY].copy()
    validation_raw = raw[(raw["day_number"] >= VALIDATION_START_DAY) & (raw["day_number"] <= VALIDATION_END_DAY)].copy()
    test_raw = raw[(raw["day_number"] >= TEST_START_DAY) & (raw["day_number"] <= TEST_END_DAY)].copy()
    train_features = featured[featured["day_number"] <= TRAIN_END_DAY].copy()

    validation_results: dict[str, dict[str, float | None]] = {}
    candidate_bundles: dict[str, dict[str, Any]] = {
        BASELINE_SEASONAL_NAIVE_7: _bundle(BASELINE_SEASONAL_NAIVE_7),
        BASELINE_ROLLING_MEAN_28: _bundle(BASELINE_ROLLING_MEAN_28),
    }
    for model_name in MODEL_BUILDERS:
        estimator = _fit_estimator(model_name, train_features)
        candidate_bundles[model_name] = _bundle(model_name, estimator)

    for model_name, bundle in candidate_bundles.items():
        actual, predicted = _predict_candidate(bundle, train_raw, validation_raw)
        validation_results[model_name] = forecast_metrics(actual, predicted)
        print(f"Validation {model_name}: {validation_results[model_name]}")

    selected_name = min(validation_results, key=lambda name: _score_for_selection(validation_results[name]))
    if selected_name in MODEL_BUILDERS:
        train_validation_features = featured[featured["day_number"] <= VALIDATION_END_DAY].copy()
        final_estimator = _fit_estimator(selected_name, train_validation_features)
    else:
        final_estimator = None

    final_bundle = _bundle(selected_name, final_estimator)
    train_validation_raw = raw[raw["day_number"] <= VALIDATION_END_DAY].copy()
    test_actual, test_predicted = _predict_candidate(final_bundle, train_validation_raw, test_raw)
    test_metrics = forecast_metrics(test_actual, test_predicted)

    trained_at = datetime.now(timezone.utc)
    model_version = f"m5-{args.store_id.lower()}-{args.dept_id.lower()}-{trained_at:%Y%m%dT%H%M%SZ}"
    final_bundle["model_version"] = model_version
    final_bundle["trained_at"] = trained_at.isoformat()
    args.model_path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(final_bundle, args.model_path)

    metadata = {
        "modelName": selected_name,
        "modelVersion": model_version,
        "trainedAt": trained_at.isoformat(),
        "dataset": {
            "name": "M5 Forecasting - Accuracy",
            "source": validation["source"],
            "files": validation["files"],
            "integrityVerified": validation["integrityVerified"],
            "checksums": validation["checksums"],
            "storeId": args.store_id,
            "departmentId": args.dept_id,
            "seriesCount": int(raw["series_id"].nunique()),
            "observationCount": int(len(raw)),
            "dateStart": raw["date"].min().date().isoformat(),
            "dateEnd": raw["date"].max().date().isoformat(),
        },
        "split": {
            "train": f"d_1..d_{TRAIN_END_DAY}",
            "validation": f"d_{VALIDATION_START_DAY}..d_{VALIDATION_END_DAY}",
            "test": f"d_{TEST_START_DAY}..d_{TEST_END_DAY}",
            "strategy": "chronological_recursive_28_day",
        },
        "features": FEATURE_COLUMNS,
        "minimumHistoryDays": MIN_HISTORY_DAYS,
        "priceFeatureUsed": False,
        "snapFeatureUsed": False,
        "eventFeatureUsed": False,
        "selectionMetric": SELECTION_METRIC,
        "validationMetrics": validation_results,
        "testMetrics": test_metrics,
        "limitations": [
            "M5 item/store identities remain anonymized and are mapped to enterprise products separately.",
            "Model v1 excludes sell price because future enterprise price values are not guaranteed at serving time.",
            "Model v1 excludes Walmart SNAP/event features to avoid transferring retailer-specific signals into enterprise demand.",
            "The final test window is evaluated once after model selection on the validation window.",
        ],
    }
    args.metadata_path.parent.mkdir(parents=True, exist_ok=True)
    args.metadata_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    print(json.dumps({"selectedModel": selected_name, "testMetrics": test_metrics}, indent=2))
    return metadata


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train the Stage 6B demand forecast model on real M5 data")
    parser.add_argument("--data-dir", type=Path, default=Path("data/raw/m5"))
    parser.add_argument("--store-id", default="CA_1")
    parser.add_argument("--dept-id", default="FOODS_3")
    parser.add_argument("--max-series", type=int, default=None, help="Optional deterministic item limit for a laptop smoke training run; data remains real M5 data.")
    parser.add_argument("--verify-md5", action="store_true")
    parser.add_argument("--model-path", type=Path, default=Path("models/demand_forecast_model.joblib"))
    parser.add_argument("--metadata-path", type=Path, default=Path("models/demand_forecast_metadata.json"))
    return parser.parse_args()


if __name__ == "__main__":
    train(parse_args())
