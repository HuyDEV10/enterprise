from fastapi import FastAPI

from app.api.demand import router as demand_router
from app.api.health import router as health_router
from app.api.models import router as models_router

app = FastAPI(
    title="Enterprise Risk AI Service",
    version="0.2.0",
    description="ML service for demand forecasting and external-event NLP intelligence.",
)

app.include_router(health_router)
app.include_router(models_router)
app.include_router(demand_router)
