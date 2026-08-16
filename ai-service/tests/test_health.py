from fastapi.testclient import TestClient

from app.api import health as health_api
from app.api import models as models_api
from app.main import app

client = TestClient(app)


def test_health_reports_service_up_when_models_are_unavailable(monkeypatch) -> None:
    # Endpoint contract test only. Keep it independent from real local model artifacts.
    monkeypatch.setattr(health_api.model_registry, "demand_model_loaded", lambda: False)
    monkeypatch.setattr(health_api.model_registry, "sentiment_model_loaded", lambda: False)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "forecastModelLoaded": False,
        "sentimentModelLoaded": False,
    }


def test_models_is_empty_when_registry_has_no_available_models(monkeypatch) -> None:
    # Do not depend on whether the developer machine already contains trained artifacts.
    monkeypatch.setattr(models_api.model_registry, "model_info", lambda: [])

    response = client.get("/api/v1/models")

    assert response.status_code == 200
    assert response.json() == {"models": []}


def test_health_and_models_reflect_loaded_forecast_model(monkeypatch) -> None:
    # Technical API contract fixture; these values are not training/evaluation metrics.
    monkeypatch.setattr(health_api.model_registry, "demand_model_loaded", lambda: True)
    monkeypatch.setattr(health_api.model_registry, "sentiment_model_loaded", lambda: False)
    monkeypatch.setattr(
        models_api.model_registry,
        "model_info",
        lambda: [
            {
                "type": "DEMAND_FORECAST",
                "modelName": "TECHNICAL_TEST_MODEL",
                "modelVersion": "technical-test-version",
            }
        ],
    )

    health_response = client.get("/health")
    models_response = client.get("/api/v1/models")

    assert health_response.status_code == 200
    assert health_response.json() == {
        "status": "UP",
        "forecastModelLoaded": True,
        "sentimentModelLoaded": False,
    }
    assert models_response.status_code == 200
    assert models_response.json()["models"][0]["type"] == "DEMAND_FORECAST"
