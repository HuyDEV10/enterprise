from fastapi import FastAPI

from app.api.health import router as health_router
from app.api.models import router as models_router

app = FastAPI(
    title="Enterprise Risk AI Service",
    version="0.1.0",
    description="ML service for demand forecasting and external-event NLP intelligence.",
)

app.include_router(health_router)
app.include_router(models_router)
