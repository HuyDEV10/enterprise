from pathlib import Path

AI_SERVICE_DIR = Path(__file__).resolve().parents[2]
MODELS_DIR = AI_SERVICE_DIR / "models"

DEMAND_MODEL_PATH = MODELS_DIR / "demand_forecast_model.joblib"
DEMAND_METADATA_PATH = MODELS_DIR / "demand_forecast_metadata.json"

SENTIMENT_MODEL_DIR = MODELS_DIR / "entity_sentiment_model"
SENTIMENT_MODEL_PATH = SENTIMENT_MODEL_DIR / "model.joblib"
SENTIMENT_METADATA_PATH = SENTIMENT_MODEL_DIR / "metadata.json"
