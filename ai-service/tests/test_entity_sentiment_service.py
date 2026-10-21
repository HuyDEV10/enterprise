import numpy as np

from app.schemas.sentiment import EntitySentimentRequest
from app.services.entity_sentiment_service import EntitySentimentService, format_entity_input


class TechnicalPipeline:
    def predict(self, values):
        assert values == ["TARGET=Acme Corp || TEXT=Acme Corp reported stronger earnings."]
        return np.asarray(["POSITIVE"])

    def predict_proba(self, values):
        return np.asarray([[0.05, 0.10, 0.85]])


def test_format_entity_input_marks_target_entity() -> None:
    assert format_entity_input(" Acme   Corp ", " Acme Corp   improved. ") == "TARGET=Acme Corp || TEXT=Acme Corp improved."


def test_entity_sentiment_service_contract(monkeypatch) -> None:
    service = EntitySentimentService()
    monkeypatch.setattr(
        service,
        "_load_bundle",
        lambda: {"pipeline": TechnicalPipeline(), "model_version": "technical-test"},
    )

    response = service.analyze(
        EntitySentimentRequest(entity="Acme Corp", text="Acme Corp reported stronger earnings.")
    )

    assert response.sentiment == "POSITIVE"
    assert response.confidence == 0.85
    assert response.modelVersion == "technical-test"
