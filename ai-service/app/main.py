from fastapi import FastAPI
from contextlib import asynccontextmanager
import logging
from starlette.concurrency import run_in_threadpool

from app.api.demand import router as demand_router
from app.api.health import router as health_router
from app.api.m5_dataset import router as m5_dataset_router
from app.api.models import router as models_router
from app.api.sentiment import router as sentiment_router
from app.services.entity_sentiment_service import entity_sentiment_service, EntitySentimentUnavailableError


@asynccontextmanager
async def lifespan(application):
    # Verify/load the local release before accepting the first backend request.
    if entity_sentiment_service.manifest_path.is_file():
        try:
            await run_in_threadpool(entity_sentiment_service._load_bundle)
        except EntitySentimentUnavailableError:
            logging.getLogger(__name__).warning("Active sentiment artifact is unavailable; sentiment requests will return 503")
    yield

app = FastAPI(
    title="Enterprise Risk AI Service",
    version="0.5.0",
    description="ML service for demand forecasting and external-event NLP intelligence.",
    lifespan=lifespan,
)

app.include_router(health_router)
app.include_router(models_router)
app.include_router(demand_router)
app.include_router(m5_dataset_router)
app.include_router(sentiment_router)
