from fastapi import APIRouter, HTTPException

from app.schemas.demand import DemandForecastRequest, DemandForecastResponse
from app.services.demand_forecast_service import (
    DemandForecastInputError,
    DemandForecastUnavailableError,
    demand_forecast_service,
)

router = APIRouter(prefix="/api/v1/forecast", tags=["demand-forecast"])


@router.post("/demand", response_model=DemandForecastResponse)
def forecast_demand(request: DemandForecastRequest) -> DemandForecastResponse:
    try:
        return demand_forecast_service.forecast(request)
    except DemandForecastInputError as exc:
        raise HTTPException(status_code=422, detail={"code": exc.code, "message": exc.message}) from exc
    except DemandForecastUnavailableError as exc:
        raise HTTPException(
            status_code=503,
            detail={"code": "MODEL_UNAVAILABLE", "message": str(exc)},
        ) from exc
