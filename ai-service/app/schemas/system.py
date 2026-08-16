from typing import Any

from pydantic import BaseModel


class HealthResponse(BaseModel):
    status: str
    forecastModelLoaded: bool
    sentimentModelLoaded: bool


class ModelsResponse(BaseModel):
    models: list[dict[str, Any]]
