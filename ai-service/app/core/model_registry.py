from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from app.core.config import (
    DEMAND_METADATA_PATH,
    DEMAND_MODEL_PATH,
    SENTIMENT_METADATA_PATH,
    SENTIMENT_MODEL_PATH,
)


class ModelRegistry:
    def __init__(
        self,
        demand_model_path: Path = DEMAND_MODEL_PATH,
        demand_metadata_path: Path = DEMAND_METADATA_PATH,
        sentiment_model_path: Path = SENTIMENT_MODEL_PATH,
        sentiment_metadata_path: Path = SENTIMENT_METADATA_PATH,
    ) -> None:
        self.demand_model_path = Path(demand_model_path)
        self.demand_metadata_path = Path(demand_metadata_path)
        self.sentiment_model_path = Path(sentiment_model_path)
        self.sentiment_metadata_path = Path(sentiment_metadata_path)

    @staticmethod
    def _read_metadata(path: Path) -> dict[str, Any] | None:
        if not path.is_file():
            return None
        try:
            with path.open("r", encoding="utf-8") as handle:
                payload = json.load(handle)
        except (OSError, ValueError):
            return None
        return payload if isinstance(payload, dict) else None

    def demand_model_loaded(self) -> bool:
        return self.demand_model_path.is_file() and self.demand_metadata_path.is_file()

    def sentiment_model_loaded(self) -> bool:
        if (self.sentiment_model_path.parent / "active.json").exists():
            return self._active_sentiment_metadata() is not None
        return self.sentiment_model_path.is_file() and self.sentiment_metadata_path.is_file()

    def _active_sentiment_metadata(self):
        root = self.sentiment_model_path.parent.resolve()
        manifest = self._read_metadata(root/"active.json")
        if not manifest or manifest.get("artifactVersion") != 2 or manifest.get("kind") not in {"transformer", "sklearn"}:
            return None
        try:
            release = (root/manifest["release"]).resolve()
            if not release.is_relative_to(root):
                return None
            weight = "model.safetensors" if manifest.get("kind") == "transformer" else "model.joblib"
            if not (release/weight).is_file():
                return None
            return self._read_metadata(release/"metadata.json")
        except (KeyError, OSError, ValueError):
            return None

    def model_info(self) -> list[dict[str, Any]]:
        models: list[dict[str, Any]] = []

        demand_metadata = self._read_metadata(self.demand_metadata_path)
        if self.demand_model_path.is_file() and demand_metadata is not None:
            models.append({"type": "DEMAND_FORECAST", **demand_metadata})

        active = (self.sentiment_model_path.parent/"active.json").exists()
        sentiment_metadata = self._active_sentiment_metadata() if active else self._read_metadata(self.sentiment_metadata_path)
        if (active or self.sentiment_model_path.is_file()) and sentiment_metadata is not None:
            models.append({"type": "ENTITY_SENTIMENT", **sentiment_metadata})

        return models


model_registry = ModelRegistry()
