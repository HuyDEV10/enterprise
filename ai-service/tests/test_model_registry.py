import json
from pathlib import Path

from app.core.model_registry import ModelRegistry


def test_registry_requires_model_and_metadata(tmp_path: Path) -> None:
    model = tmp_path / "demand.joblib"
    metadata = tmp_path / "demand.json"
    sentiment_model = tmp_path / "sentiment.joblib"
    sentiment_metadata = tmp_path / "sentiment.json"

    registry = ModelRegistry(model, metadata, sentiment_model, sentiment_metadata)
    assert registry.demand_model_loaded() is False

    model.write_bytes(b"not-a-real-model-unit-test-placeholder")
    assert registry.demand_model_loaded() is False

    metadata.write_text(json.dumps({"modelName": "unit-test"}), encoding="utf-8")
    assert registry.demand_model_loaded() is True
    assert registry.model_info() == [{"type": "DEMAND_FORECAST", "modelName": "unit-test"}]
