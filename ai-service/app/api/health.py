from fastapi import APIRouter

from app.core.model_registry import model_registry
from app.schemas.system import HealthResponse

router = APIRouter(tags=["system"])


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="UP",
        forecastModelLoaded=model_registry.demand_model_loaded(),
        sentimentModelLoaded=model_registry.sentiment_model_loaded(),
    )
