from datetime import date

import pandas as pd

from app.ml.demand_features import FEATURE_COLUMNS
from app.ml.demand_runtime import predict_next


class ColumnAwareEstimator:
    def predict(self, frame: pd.DataFrame) -> list[float]:
        assert isinstance(frame, pd.DataFrame)
        assert list(frame.columns) == FEATURE_COLUMNS
        return [1.5]


def test_runtime_preserves_feature_names_for_estimator_contract() -> None:
    # Technical contract test only. No model is trained or evaluated here.
    bundle = {
        "model_name": "TECHNICAL_ESTIMATOR",
        "estimator": ColumnAwareEstimator(),
        "feature_columns": FEATURE_COLUMNS,
    }
    history = [float((index % 7) + 1) for index in range(56)]

    prediction = predict_next(bundle, history, date(2026, 3, 1))

    assert prediction == 1.5
