from datetime import date, timedelta
from pathlib import Path

import joblib
import pytest

from app.ml.demand_runtime import BASELINE_SEASONAL_NAIVE_7
from app.schemas.demand import DemandForecastRequest, DemandHistoryPoint
from app.services.demand_forecast_service import DemandForecastInputError, DemandForecastService


def _history(days: int) -> list[DemandHistoryPoint]:
    # Technical unit-test fixture only. It is never used for model training/evaluation.
    start = date(2026, 1, 1)
    return [
        DemandHistoryPoint(date=start + timedelta(days=index), quantity=float((index % 7) + 1))
        for index in range(days)
    ]


def _write_baseline_bundle(path: Path) -> None:
    joblib.dump(
        {
            "artifact_version": 1,
            "model_name": BASELINE_SEASONAL_NAIVE_7,
            "estimator": None,
            "feature_columns": [],
            "minimum_history_days": 56,
            "forecast_strategy": "recursive_one_step",
            "model_version": "technical-unit-test",
        },
        path,
    )


def test_service_returns_insufficient_history_code(tmp_path: Path) -> None:
    artifact = tmp_path / "model.joblib"
    _write_baseline_bundle(artifact)
    service = DemandForecastService(artifact)

    with pytest.raises(DemandForecastInputError) as exc_info:
        service.forecast(DemandForecastRequest(history=_history(55)))

    assert exc_info.value.code == "INSUFFICIENT_HISTORY"


def test_service_rejects_history_gaps_instead_of_inventing_zero_demand(tmp_path: Path) -> None:
    artifact = tmp_path / "model.joblib"
    _write_baseline_bundle(artifact)
    service = DemandForecastService(artifact)
    history = _history(56)
    history.pop(20)

    with pytest.raises(DemandForecastInputError) as exc_info:
        service.forecast(DemandForecastRequest(history=history))

    assert exc_info.value.code == "INVALID_HISTORY"


def test_service_returns_7_14_28_aggregates(tmp_path: Path) -> None:
    artifact = tmp_path / "model.joblib"
    _write_baseline_bundle(artifact)
    service = DemandForecastService(artifact)

    response = service.forecast(DemandForecastRequest(history=_history(56)))

    assert response.historyDays == 56
    assert response.modelVersion == "technical-unit-test"
    assert len(response.forecast) == 28
    assert response.summary.next7Days > 0
    assert response.summary.next14Days >= response.summary.next7Days
    assert response.summary.next28Days >= response.summary.next14Days


def test_service_can_anchor_forecast_to_enterprise_calendar(tmp_path: Path) -> None:
    artifact = tmp_path / "model.joblib"
    _write_baseline_bundle(artifact)
    service = DemandForecastService(artifact)
    forecast_start = date(2030, 5, 10)

    response = service.forecast(
        DemandForecastRequest(history=_history(56), forecastStartDate=forecast_start)
    )

    assert response.forecast[0].date == forecast_start
    assert response.forecast[-1].date == forecast_start + timedelta(days=27)
