from __future__ import annotations

from datetime import date, timedelta
from pathlib import Path
from typing import Any

import joblib

from app.core.config import DEMAND_MODEL_PATH
from app.ml.demand_features import MIN_HISTORY_DAYS
from app.ml.demand_runtime import recursive_forecast
from app.schemas.demand import (
    DemandForecastPoint,
    DemandForecastRequest,
    DemandForecastResponse,
    DemandForecastSummary,
)


class DemandForecastUnavailableError(RuntimeError):
    pass


class DemandForecastInputError(ValueError):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


class DemandForecastService:
    def __init__(self, model_path: Path = DEMAND_MODEL_PATH) -> None:
        self.model_path = Path(model_path)
        self._cached_bundle: dict[str, Any] | None = None
        self._cached_mtime_ns: int | None = None

    def _load_bundle(self) -> dict[str, Any]:
        if not self.model_path.is_file():
            raise DemandForecastUnavailableError("Demand forecast model artifact is not available")

        mtime_ns = self.model_path.stat().st_mtime_ns
        if self._cached_bundle is None or self._cached_mtime_ns != mtime_ns:
            loaded = joblib.load(self.model_path)
            if not isinstance(loaded, dict):
                raise DemandForecastUnavailableError("Demand forecast model artifact has an invalid format")
            self._cached_bundle = loaded
            self._cached_mtime_ns = mtime_ns
        return self._cached_bundle

    @staticmethod
    def _validate_history(request: DemandForecastRequest, minimum_history_days: int) -> tuple[list[float], date]:
        history = sorted(request.history, key=lambda point: point.date)
        dates = [point.date for point in history]
        if len(set(dates)) != len(dates):
            raise DemandForecastInputError("INVALID_HISTORY", "Demand history contains duplicate dates")

        for previous, current in zip(dates, dates[1:], strict=False):
            if current != previous + timedelta(days=1):
                raise DemandForecastInputError(
                    "INVALID_HISTORY",
                    "Demand history must be contiguous; missing dates are not converted to zero demand",
                )

        if len(history) < minimum_history_days:
            raise DemandForecastInputError(
                "INSUFFICIENT_HISTORY",
                f"At least {minimum_history_days} contiguous history days are required",
            )

        return [point.quantity for point in history], history[-1].date

    def forecast(self, request: DemandForecastRequest) -> DemandForecastResponse:
        bundle = self._load_bundle()
        minimum_history_days = int(bundle.get("minimum_history_days", MIN_HISTORY_DAYS))
        quantities, last_history_date = self._validate_history(request, minimum_history_days)

        forecast_anchor = (
            request.forecastStartDate - timedelta(days=1)
            if request.forecastStartDate is not None
            else last_history_date
        )
        predictions = recursive_forecast(
            bundle=bundle,
            history=quantities,
            last_history_date=forecast_anchor,
            horizon_days=28,
        )
        points = [
            DemandForecastPoint(date=forecast_date, quantity=round(quantity, 4))
            for forecast_date, quantity in predictions
        ]

        def total(days: int) -> float:
            return round(sum(point.quantity for point in points[:days]), 4)

        return DemandForecastResponse(
            historyDays=len(quantities),
            modelVersion=str(bundle.get("model_version", "UNKNOWN")),
            forecast=points,
            summary=DemandForecastSummary(
                next7Days=total(7),
                next14Days=total(14),
                next28Days=total(28),
            ),
        )


demand_forecast_service = DemandForecastService()
