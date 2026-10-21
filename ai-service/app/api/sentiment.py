from fastapi import APIRouter, HTTPException

from app.schemas.sentiment import EntitySentimentRequest, EntitySentimentResponse
from app.services.entity_sentiment_service import EntitySentimentUnavailableError, entity_sentiment_service

router = APIRouter(prefix="/api/v1/sentiment", tags=["entity-sentiment"])


@router.post("/entity", response_model=EntitySentimentResponse)
def analyze_entity_sentiment(request: EntitySentimentRequest) -> EntitySentimentResponse:
    try:
        return entity_sentiment_service.analyze(request)
    except EntitySentimentUnavailableError as exc:
        raise HTTPException(
            status_code=503,
            detail={"code": "MODEL_UNAVAILABLE", "message": str(exc)},
        ) from exc
