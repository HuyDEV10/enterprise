# Dataset provenance and local layout

Stage 6 uses real-world public datasets only. Raw datasets are local-only and are ignored by Git.
Mock data may be used only in isolated technical unit tests and must never be used to train or evaluate a model.

## M5 Forecasting - Walmart retail demand

Purpose: train/evaluate demand forecasting.

Primary source: https://www.kaggle.com/competitions/m5-forecasting-accuracy
Open archival mirror: https://doi.org/10.5281/zenodo.10203108
Competition information: https://mofc.unic.ac.cy/m5-competition/

Required files:

- `data/raw/m5/sales_train_evaluation.csv`
- `data/raw/m5/calendar.csv`
- `data/raw/m5/sell_prices.csv`

Initial Stage 6 subset: `store_id=CA_1`, `dept_id=FOODS_3`.
Item and store identifiers remain anonymized exactly as supplied by M5.

## FinEntity

Purpose: train/evaluate entity-level financial sentiment classification.

Source: https://github.com/yixuantt/FinEntity
Paper: https://aclanthology.org/2023.emnlp-main.956/

Required file:

- `data/raw/finentity/FinEntity.json`

Labels: Positive, Negative, Neutral.
FinEntity is not used to infer GDELT event type.

## GDELT 2.0

Purpose: real-world external event ingestion.

Source/documentation: https://www.gdeltproject.org/data.html
Data stream: https://data.gdeltproject.org/gdeltv2/

Only recent partitions relevant to supplier countries/regions and selected supply-chain CAMEO categories are ingested.
The system must not download the complete GDELT corpus.

## EM-DAT

Purpose: disaster-impact enrichment; not a supervised training dataset in Stage 6.

Public platform: https://public.emdat.be/
Documentation: https://doc.emdat.be/docs/data-structure-and-content/emdat-public-table/

Keep missing impact/economic-damage values as missing. Do not convert unknown/unreported values to zero.

Expected local path after export/download:

- `data/raw/emdat/` (exact filename documented after the actual export is obtained)

## World Bank Pink Sheet

Purpose: monthly commodity-price risk/opportunity signals; not supervised ML in Stage 6.

Source: https://www.worldbank.org/en/research/commodity-markets

Expected local file:

- `data/raw/world_bank/CMO-Historical-Data-Monthly.xlsx`

Commodity-to-product relationships must be explicitly verified before a commodity signal is attached to a Product.
