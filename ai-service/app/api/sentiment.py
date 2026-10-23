from fastapi import APIRouter, HTTPException

from app.schemas.sentiment import EntitySentimentRequest, EntitySentimentResponse, EntitySentimentBatchRequest, EntitySentimentBatchResponse
from app.ml.sentiment_text import SentimentInputError
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
    except SentimentInputError as exc:
        raise HTTPException(status_code=422, detail={"code": exc.code, "message": str(exc)}) from exc


@router.post("/entities", response_model=EntitySentimentBatchResponse)
def analyze_entity_batch(request: EntitySentimentBatchRequest) -> EntitySentimentBatchResponse:
    try:
        results = entity_sentiment_service.analyze_many([EntitySentimentRequest(entity=entity, text=request.text, language=request.language, domain=request.domain) for entity in request.entities])
        return EntitySentimentBatchResponse(results=results)
    except EntitySentimentUnavailableError as exc:
        raise HTTPException(status_code=503, detail={"code": "MODEL_UNAVAILABLE", "message": str(exc)}) from exc
    except SentimentInputError as exc:
        raise HTTPException(status_code=422, detail={"code": exc.code, "message": str(exc)}) from exc
