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

## Bilingual target sentiment

Purpose: train/evaluate sentiment toward an explicit target, using three labels: `NEGATIVE`, `NEUTRAL`, `POSITIVE`. A document may have different labels for different targets.

Pinned URLs, revisions, licenses, SHA-256 values and excluded sources are maintained in `training/sentiment/sources.json`. [The pipeline guide](../../docs/SENTIMENT-PIPELINE.md) documents exact lifecycle commands and domain limitations.

| Source | Local files | Annotation scope |
| --- | --- | --- |
| [FinEntity](https://github.com/yixuantt/FinEntity), [paper](https://aclanthology.org/2023.emnlp-main.956/) | `data/raw/finentity/FinEntity.json` | Human entity-level English financial news; ODC-BY |
| [SEntFiN](https://github.com/pyRis/SEntFiN) | `data/raw/sentfin/SEntFiN.csv` | Human entity-level English financial headlines; upstream MIT |
| [ViTASA](https://github.com/kh4nh12/ViTASA) | `data/raw/vitasa/{mobile,hotel,restaurant}.jsonl` | Native Vietnamese target/aspect sentiment in consumer/hospitality domains; upstream MIT |
| [UTS2017_Bank](https://huggingface.co/datasets/undertheseanlp/UTS2017_Bank) | `data/raw/utsbank/{train,test}.jsonl` | Human `TRADEMARK` labels anchored to one explicitly named bank; publisher card Apache-2.0 |

Download with `python -m training.sentiment.pipeline download`; prepare with `python -m training.sentiment.pipeline prepare`. JSONL splits, quarantine records, checksums and provenance/count reports are under `data/processed/sentiment-v2/`.

Split by global canonical/near-duplicate document group, approximately 80/10/10, keeping all targets of a document together. Preserve official bank test. Conflicting target labels, absent mentions, ambiguous bank identities and duplicate text/target pairs are excluded and reported. No QA fixtures, synthetic examples, translations, model-generated labels or GDELT tone are training/test labels.

Vietnamese review corpora are not a labelled Vietnamese supply-chain-news benchmark. Report per-dataset support and keep `vietnameseSupplyChainNewsGoldValidated=false` until a separate real human-labelled benchmark exists. Sentiment labels are not GDELT event categories or probabilities of supplier failure.

## GDELT 2.0

Purpose: real-world external event ingestion.

Source/documentation: https://www.gdeltproject.org/data.html
Data stream: https://data.gdeltproject.org/gdeltv2/

Only recent partitions relevant to supplier countries/regions and selected supply-chain CAMEO categories are ingested.
The system must not download the complete GDELT corpus.

GDELT event exports contain event metadata and source URLs, not full article bodies. The sentiment UI requires actual headline/article text and an explicit company mention. No automatic article-body crawler is implied by ingestion, and `AvgTone` is not entity sentiment ground truth.

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
