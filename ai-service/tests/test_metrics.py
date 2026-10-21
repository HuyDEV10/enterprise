import pytest
import numpy as np

from training.forecasting.metrics import forecast_metrics


def test_forecast_metrics_are_computed_from_given_values() -> None:
    # Technical unit-test fixture only. It is never reported as a model metric.
    metrics = forecast_metrics(np.asarray([1.0, 2.0, 3.0]), np.asarray([1.0, 1.0, 4.0]))

    assert metrics["mae"] == pytest.approx(2.0 / 3.0)
    assert metrics["rmse"] == pytest.approx((2.0 / 3.0) ** 0.5)
    assert metrics["wape"] == pytest.approx(100.0 / 3.0)
