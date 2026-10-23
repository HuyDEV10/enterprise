from __future__ import annotations

from pathlib import Path
import threading
from typing import Any

import joblib
import numpy as np

from app.core.config import SENTIMENT_MODEL_PATH
from app.schemas.sentiment import EntitySentimentRequest, EntitySentimentResponse
from app.ml.sentiment_text import prepare_target_context
from app.ml.sentiment_runtime import route_language


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
        self.manifest_path = self.model_path.parent / "active.json"
        self._lock = threading.RLock()
        self._cached_kind = None

    def _load_bundle(self) -> dict[str, Any]:
        with self._lock:
            return self._load_bundle_locked()

    def _load_bundle_locked(self) -> dict[str, Any]:
        if self.manifest_path.is_file():
            mtime_ns = self.manifest_path.stat().st_mtime_ns
            if self._cached_bundle is None or self._cached_mtime_ns != mtime_ns or self._cached_kind != "v2":
                from app.ml.sentiment_runtime import SentimentRuntime
                try:
                    runtime = SentimentRuntime(self.manifest_path)
                except (OSError, ValueError, ImportError, RuntimeError, KeyError, TypeError) as error:
                    raise EntitySentimentUnavailableError("Active sentiment model could not be loaded or verified") from error
                self._cached_bundle = {"runtime": runtime, "model_version": runtime.metadata["modelVersion"]}
                self._cached_mtime_ns, self._cached_kind = mtime_ns, "v2"
            return self._cached_bundle
        if not self.model_path.is_file():
            raise EntitySentimentUnavailableError("Entity sentiment model artifact is not available")
        mtime_ns = self.model_path.stat().st_mtime_ns
        if self._cached_bundle is None or self._cached_mtime_ns != mtime_ns or self._cached_kind != "v1":
            loaded = joblib.load(self.model_path)
            if not isinstance(loaded, dict) or loaded.get("pipeline") is None:
                raise EntitySentimentUnavailableError("Entity sentiment model artifact has an invalid format")
            self._cached_bundle = loaded
            self._cached_mtime_ns = mtime_ns
            self._cached_kind = "v1"
        return self._cached_bundle

    def analyze(self, request: EntitySentimentRequest) -> EntitySentimentResponse:
        bundle = self._load_bundle()
        if "runtime" in bundle:
            return EntitySentimentResponse(**bundle["runtime"].predict([request])[0])
        context = prepare_target_context(request.entity, request.text)
        pipeline = bundle["pipeline"]
        formatted = format_entity_input(request.entity, request.text)
        sentiment = str(pipeline.predict([formatted])[0]).upper()
        if sentiment not in {"POSITIVE", "NEGATIVE", "NEUTRAL"}:
            raise EntitySentimentUnavailableError(f"Unexpected sentiment label returned by model: {sentiment}")

        confidence = 1.0
        if hasattr(pipeline, "predict_proba"):
            probabilities = np.asarray(pipeline.predict_proba([formatted])[0], dtype="float64")
            if probabilities.shape != (3,) or not np.isfinite(probabilities).all() or np.any(probabilities < 0) or abs(float(probabilities.sum())-1.) > 1e-5:
                raise EntitySentimentUnavailableError("Entity sentiment model returned invalid probabilities")
            confidence = float(np.max(probabilities))

        return EntitySentimentResponse(
            entity=request.entity.strip(),
            sentiment=sentiment,
            confidence=round(confidence, 6),
            modelVersion=str(bundle.get("model_version", "UNKNOWN")),
            language=route_language(request.text),
            needsReview=True,
            reviewReason="LEGACY_UNCALIBRATED",
            inputTruncated=context.truncated,
        )

    def analyze_many(self, requests: list[EntitySentimentRequest]) -> list[EntitySentimentResponse]:
        bundle = self._load_bundle()
        if "runtime" in bundle:
            return [EntitySentimentResponse(**value) for value in bundle["runtime"].predict(requests)]
        return [self.analyze(request) for request in requests]


entity_sentiment_service = EntitySentimentService()
