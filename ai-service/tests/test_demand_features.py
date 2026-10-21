from datetime import date, timedelta

import pandas as pd

from app.ml.demand_features import FEATURE_COLUMNS, add_training_features, feature_vector_from_history


def test_training_features_do_not_use_same_day_target() -> None:
    # Technical unit-test fixture only. It is never used for model training/evaluation.
    start = date(2026, 1, 1)
    frame = pd.DataFrame(
        {
            "series_id": ["TECHNICAL_FIXTURE"] * 60,
            "date": [start + timedelta(days=index) for index in range(60)],
            "quantity": [float(index % 9) for index in range(60)],
        }
    )
    changed = frame.copy()
    changed.loc[59, "quantity"] = 9999.0

    original_features = add_training_features(frame).iloc[59][FEATURE_COLUMNS]
    changed_features = add_training_features(changed).iloc[59][FEATURE_COLUMNS]

    pd.testing.assert_series_equal(original_features, changed_features)


def test_runtime_feature_contract_matches_expected_order() -> None:
    # Technical unit-test fixture only. It is never used for model training/evaluation.
    history = [float(index) for index in range(1, 57)]
    vector = feature_vector_from_history(history, date(2026, 3, 1))

    assert len(vector) == len(FEATURE_COLUMNS)
    assert vector[0] == 56.0
    assert vector[1] == 50.0
    assert vector[3] == 29.0
