from fastapi import APIRouter

from app.core.model_registry import model_registry
from app.schemas.system import ModelsResponse

router = APIRouter(prefix="/api/v1", tags=["models"])


@router.get("/models", response_model=ModelsResponse)
def models() -> ModelsResponse:
    return ModelsResponse(models=model_registry.model_info())
