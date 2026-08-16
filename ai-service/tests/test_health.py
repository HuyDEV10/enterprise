from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_reports_service_up_without_fabricating_loaded_models() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "forecastModelLoaded": False,
        "sentimentModelLoaded": False,
    }


def test_models_is_empty_before_real_training_artifacts_exist() -> None:
    response = client.get("/api/v1/models")

    assert response.status_code == 200
    assert response.json() == {"models": []}
