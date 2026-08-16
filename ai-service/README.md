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
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
```

## Dataset integrity

**NO SYNTHETIC TRAINING DATA.**

Training/validation/test data must come from the real public sources documented in `data/README.md`.
Raw and processed data folders are Git-ignored because M5/GDELT can be large.
Technical unit-test fixtures are isolated under `tests/` and are never used to train or evaluate a model.

## Stage 6B - M5 demand forecasting

Required real files:

```text
data/raw/m5/calendar.csv
data/raw/m5/sales_train_evaluation.csv
data/raw/m5/sell_prices.csv
```

Download the archived M5 files from Zenodo and verify the published checksums:

```powershell
python -m training.forecasting.download_m5
```

Validate the real dataset and the default subset (`CA_1 + FOODS_3`):

```powershell
python -m training.forecasting.validate_m5 --verify-md5
```

For a first laptop smoke run using only the first 100 real M5 series from the same subset:

```powershell
python -m training.forecasting.validate_m5 --verify-md5 --max-series 100
python -m training.forecasting.train --verify-md5 --max-series 100
```

For the full `CA_1 + FOODS_3` subset:

```powershell
python -m training.forecasting.train --verify-md5
```

The deterministic `--max-series` option never creates data; it only limits the number of real M5 series loaded for a smaller first run.

### Chronological split

```text
Train:      d_1 .. d_1885
Validation: d_1886 .. d_1913
Test:       d_1914 .. d_1941
```

Validation and test are forecast recursively for 28 days. Future ground-truth demand is not used as a lag during a forecast window.

### Stage 6B model candidates

- Seasonal naive, lag 7
- Rolling mean 28
- Ridge regression
- HistGradientBoostingRegressor

Features:

- lag 1/7/14/28
- rolling mean 7/14/28, always shifted by one day
- rolling standard deviation 7/28, always shifted by one day
- day of week
- month
- day of month

Model v1 deliberately excludes Walmart SNAP/event features and sell price from model input. `sell_prices.csv` is still required and integrity-checked because it is part of the real M5 dataset and may be used in a later model only when future-serving semantics are safe.

Metrics saved from real data:

- MAE
- RMSE
- WAPE

Model selection uses validation WAPE. The final test window is evaluated once after model selection.

Artifacts:

```text
models/demand_forecast_model.joblib
models/demand_forecast_metadata.json
```

No placeholder artifact or fabricated metric is generated when the real M5 files are absent.

## FastAPI endpoints

```text
GET  /health
GET  /api/v1/models
POST /api/v1/forecast/demand
```

The demand endpoint always returns a 28-day recursive forecast plus aggregate demand for the next 7, 14 and 28 days. It requires at least 56 contiguous history days. Missing dates are rejected instead of being silently converted to zero demand.

Before a real model artifact exists, `/health` reports `forecastModelLoaded=false`, `/api/v1/models` omits the model, and the forecast endpoint returns `MODEL_UNAVAILABLE`.

## Run FastAPI

```powershell
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## Run tests

```powershell
pytest -q
```

## Service boundary

React must not call FastAPI directly.

```text
React -> Spring Boot -> FastAPI
```

Spring Boot remains responsible for JWT/RBAC, enterprise database access, incoming-supply calculation, stockout classification, RiskEvent creation, Alert creation, and external-event enterprise exposure.
