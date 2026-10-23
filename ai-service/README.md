# Enterprise Risk AI Service

FastAPI service for Stage 6 of the Enterprise Risk project.

The service has two ML responsibilities:

1. Demand forecasting using real M5 demand history.
2. Target/entity sentiment classification in English and Vietnamese using verified public FinEntity, SEntFiN, ViTASA and UTS2017_Bank annotations.

External-event impact scoring is business exposure scoring that consumes ML signals; it is not presented as a supervised impact-prediction model.

## Requirements

- Python 3.12.x
- FastAPI 0.115.x
- Uvicorn 0.30.x
- pandas
- numpy
- scikit-learn
- joblib

The bilingual sentiment lifecycle adds pinned `torch`/`transformers` dependencies through `requirements-sentiment.txt`. It compares retrained TF-IDF baselines with full-corpus multilingual DistilBERT fine-tuning, selects on validation, calibrates confidence, evaluates a locked holdout and promotes a versioned artifact. See [the complete Windows training and operation guide](../docs/SENTIMENT-PIPELINE.md) for CUDA installation, provenance, resume, evaluation and rollback.

Vietnamese training includes human target/aspect labels from consumer/service reviews and bank-brand reviews. These public-corpus metrics do not validate Vietnamese supplier-disruption news; metadata explicitly records this domain limitation.

Spring Boot sends `domain=supply_chain_news`. The Vietnamese branch in this domain always returns `needsReview=true` / `DOMAIN_NOT_VALIDATED` until independent human-labelled supplier-news validation is available. A contrastive Vietnamese company-news smoke case failed despite high confidence; the raw prediction is retained for inspection and excluded from accepted sentiment evidence in impact scoring. General-domain benchmark metrics remain separate from this serving policy.

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
POST /api/v1/sentiment/entity
POST /api/v1/sentiment/entities
```

The demand endpoint always returns a 28-day recursive forecast plus aggregate demand for the next 7, 14 and 28 days. It requires at least 56 contiguous history days. Missing dates are rejected instead of being silently converted to zero demand.

Before a real model artifact exists, `/health` reports `forecastModelLoaded=false`, `/api/v1/models` omits the model, and the forecast endpoint returns `MODEL_UNAVAILABLE`.

## Bilingual target sentiment lifecycle

Install base requirements, then the appropriate PyTorch wheel and `requirements-sentiment.txt` as documented in the [pipeline guide](../docs/SENTIMENT-PIPELINE.md). Run commands from `ai-service`:

```powershell
python -m training.sentiment.pipeline download
python -m training.sentiment.pipeline prepare
$runDir = 'data/processed/sentiment-runs/bilingual-v2-20261023'
python -m training.sentiment.pipeline train --run-dir $runDir --epochs 5 --batch-size 8 --max-length 128
python -m training.sentiment.pipeline evaluate --run-dir $runDir
python -m training.sentiment.pipeline promote --run-dir $runDir
```

Use `train ... --resume` with unchanged configuration to continue from the last complete epoch. Do not run `evaluate` until validation-only selection has completed. Promotion requires the recorded quality gates and an English/Vietnamese serving preflight; an active manifest points to hash-verified release files. Full metrics and limitations are generated in each run's `evaluation.json` and `MODEL-CARD.md`.

Requests provide an explicit `entity`, actual `text`, and optional `language` (`auto`, `en`, `vi`). Batch requests provide `entities` for the same text. Responses include class probabilities, confidence, model version, language and review flags. The target must be explicitly mentioned. Low-confidence predictions require review; legacy uncalibrated results are always flagged for review.

GDELT ingestion supplies event metadata/source URLs, not article body. Actual headline/article text must be supplied before sentiment analysis. Spring Boot stores sentiment per event/target and uses only evidence matched to the particular supplier when calculating exposure; event-wide legacy sentiment is excluded from supplier sentiment scoring.

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
