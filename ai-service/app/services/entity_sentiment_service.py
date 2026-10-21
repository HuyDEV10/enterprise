from __future__ import annotations

from pathlib import Path
from typing import Any

import joblib
import numpy as np

from app.core.config import SENTIMENT_MODEL_PATH
from app.schemas.sentiment import EntitySentimentRequest, EntitySentimentResponse


class EntitySentimentUnavailableError(RuntimeError):
    pass


def format_entity_input(entity: str, text: str) -> str:
    normalized_entity = " ".join(entity.strip().split())
    normalized_text = " ".join(text.strip().split())
    if not normalized_entity or not normalized_text:
        raise ValueError("entity and text must not be blank")
    return f"TARGET={normalized_entity} || TEXT={normalized_text}"


class EntitySentimentService:
    def __init__(self, model_path: Path = SENTIMENT_MODEL_PATH) -> None:
        self.model_path = Path(model_path)
        self._cached_bundle: dict[str, Any] | None = None
        self._cached_mtime_ns: int | None = None

    def _load_bundle(self) -> dict[str, Any]:
        if not self.model_path.is_file():
            raise EntitySentimentUnavailableError("Entity sentiment model artifact is not available")
        mtime_ns = self.model_path.stat().st_mtime_ns
        if self._cached_bundle is None or self._cached_mtime_ns != mtime_ns:
            loaded = joblib.load(self.model_path)
            if not isinstance(loaded, dict) or loaded.get("pipeline") is None:
                raise EntitySentimentUnavailableError("Entity sentiment model artifact has an invalid format")
            self._cached_bundle = loaded
            self._cached_mtime_ns = mtime_ns
        return self._cached_bundle

    def analyze(self, request: EntitySentimentRequest) -> EntitySentimentResponse:
        bundle = self._load_bundle()
        pipeline = bundle["pipeline"]
        formatted = format_entity_input(request.entity, request.text)
        sentiment = str(pipeline.predict([formatted])[0]).upper()
        if sentiment not in {"POSITIVE", "NEGATIVE", "NEUTRAL"}:
            raise EntitySentimentUnavailableError(f"Unexpected sentiment label returned by model: {sentiment}")

        confidence = 1.0
        if hasattr(pipeline, "predict_proba"):
            probabilities = np.asarray(pipeline.predict_proba([formatted])[0], dtype="float64")
            confidence = float(np.max(probabilities))

        return EntitySentimentResponse(
            entity=request.entity.strip(),
            sentiment=sentiment,
            confidence=round(confidence, 6),
            modelVersion=str(bundle.get("model_version", "UNKNOWN")),
        )


entity_sentiment_service = EntitySentimentService()
