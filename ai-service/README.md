# Enterprise Risk AI Service

FastAPI service for Stage 6 of the Enterprise Risk project.

The service has two ML responsibilities:

1. Demand forecasting using real M5 demand history.
2. Entity-level sentiment classification using the real FinEntity dataset.

External-event impact scoring is business exposure scoring that consumes ML signals; it is not presented as a supervised impact-prediction model.

## Requirements

- Python 3.12.x
- FastAPI 0.115.x
- Uvicorn 0.30.x
- pandas
- numpy
- scikit-learn
- joblib

`transformers` and `torch` are intentionally not included in the base requirements. They will be added only if a FinBERT experiment is justified by measured baseline performance and available hardware.

## Create the environment (PowerShell)

```powershell
cd ai-service
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
```

## Dataset integrity

**NO SYNTHETIC TRAINING DATA.**

Training/validation/test data must come from the real public sources documented in `data/README.md`.
Raw and processed data folders are Git-ignored because M5/GDELT can be large.

## Dataset sources

See `data/README.md` for source URLs, required files, intended use, and limitations for:

- M5 Forecasting
- FinEntity
- GDELT 2.0
- EM-DAT
- World Bank Pink Sheet

## Current Stage 6A endpoints

```text
GET /health
GET /api/v1/models
```

Before real training artifacts exist, `/health` intentionally reports both model-loaded flags as `false`, and `/api/v1/models` returns an empty model list. Metrics are never fabricated.

## Run FastAPI

```powershell
cd ai-service
.\.venv\Scripts\Activate.ps1
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Then check:

```text
http://localhost:8000/health
http://localhost:8000/api/v1/models
http://localhost:8000/docs
```

## Run tests

```powershell
cd ai-service
.\.venv\Scripts\Activate.ps1
pytest -q
```

## Training commands

Demand and NLP training commands are added in Stage 6B/6D after the real datasets are downloaded and validated. Stage 6A deliberately does not create placeholder model artifacts or fake metrics.

## Planned artifacts

```text
models/demand_forecast_model.joblib
models/demand_forecast_metadata.json
models/entity_sentiment_model/model.joblib
models/entity_sentiment_model/metadata.json
```

## Service boundary

React must not call FastAPI directly.

```text
React -> Spring Boot -> FastAPI
```

Spring Boot remains responsible for JWT/RBAC, enterprise database access, incoming-supply calculation, stockout classification, RiskEvent creation, Alert creation, and external-event enterprise exposure.
