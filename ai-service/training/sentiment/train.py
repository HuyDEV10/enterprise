from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, precision_recall_fscore_support
from sklearn.model_selection import GroupShuffleSplit
from sklearn.pipeline import Pipeline

from training.sentiment.finentity_dataset import load_finentity


def metrics(actual, predicted) -> dict[str, float]:
    precision, recall, f1, _ = precision_recall_fscore_support(
        actual, predicted, average="macro", zero_division=0
    )
    return {
        "accuracy": float(accuracy_score(actual, predicted)),
        "macroPrecision": float(precision),
        "macroRecall": float(recall),
        "macroF1": float(f1),
    }


def grouped_split(frame):
    first = GroupShuffleSplit(n_splits=1, train_size=0.70, random_state=42)
    train_idx, remainder_idx = next(first.split(frame, groups=frame["group_id"]))
    train = frame.iloc[train_idx].copy()
    remainder = frame.iloc[remainder_idx].copy()

    second = GroupShuffleSplit(n_splits=1, train_size=0.50, random_state=43)
    validation_rel, test_rel = next(second.split(remainder, groups=remainder["group_id"]))
    validation = remainder.iloc[validation_rel].copy()
    test = remainder.iloc[test_rel].copy()

    group_sets = [set(part["group_id"]) for part in (train, validation, test)]
    if group_sets[0] & group_sets[1] or group_sets[0] & group_sets[2] or group_sets[1] & group_sets[2]:
        raise RuntimeError("Grouped split leaked original FinEntity content across partitions")
    required = {"POSITIVE", "NEGATIVE", "NEUTRAL"}
    for name, part in (("train", train), ("validation", validation), ("test", test)):
        if set(part["label"]) != required:
            raise RuntimeError(f"{name} split does not contain all FinEntity labels")
    return train, validation, test


def build_pipeline() -> Pipeline:
    return Pipeline(
        [
            (
                "tfidf",
                TfidfVectorizer(
                    lowercase=True,
                    ngram_range=(1, 2),
                    min_df=2,
                    max_features=40000,
                    sublinear_tf=True,
                ),
            ),
            (
                "classifier",
                LogisticRegression(
                    max_iter=1000,
                    class_weight="balanced",
                    random_state=42,
                ),
            ),
        ]
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Train entity-level sentiment baseline on the real FinEntity dataset")
    parser.add_argument("--dataset", type=Path, default=Path("data/raw/finentity/FinEntity.json"))
    parser.add_argument("--model-path", type=Path, default=Path("models/entity_sentiment_model/model.joblib"))
    parser.add_argument("--metadata-path", type=Path, default=Path("models/entity_sentiment_model/metadata.json"))
    args = parser.parse_args()

    frame, dataset_metadata = load_finentity(args.dataset)
    train, validation, test = grouped_split(frame)
    pipeline = build_pipeline()
    pipeline.fit(train["input_text"], train["label"])

    validation_metrics = metrics(validation["label"], pipeline.predict(validation["input_text"]))
    test_metrics = metrics(test["label"], pipeline.predict(test["input_text"]))
    trained_at = datetime.now(timezone.utc)
    model_version = f"finentity-tfidf-logreg-{trained_at:%Y%m%dT%H%M%SZ}"

    args.model_path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(
        {
            "artifact_version": 1,
            "model_name": "TFIDF_LOGISTIC_REGRESSION",
            "model_version": model_version,
            "trained_at": trained_at.isoformat(),
            "pipeline": pipeline,
            "input_contract": "TARGET=<entity> || TEXT=<content>",
        },
        args.model_path,
    )

    metadata = {
        "modelName": "TFIDF_LOGISTIC_REGRESSION",
        "modelVersion": model_version,
        "trainedAt": trained_at.isoformat(),
        "dataset": {"name": "FinEntity", **dataset_metadata},
        "split": {
            "strategy": "grouped_by_original_content_70_15_15",
            "trainAnnotations": int(len(train)),
            "validationAnnotations": int(len(validation)),
            "testAnnotations": int(len(test)),
            "trainContents": int(train["group_id"].nunique()),
            "validationContents": int(validation["group_id"].nunique()),
            "testContents": int(test["group_id"].nunique()),
        },
        "inputContract": "TARGET=<entity> || TEXT=<content>",
        "labels": ["NEGATIVE", "NEUTRAL", "POSITIVE"],
        "selectionMetric": "macroF1",
        "validationMetrics": validation_metrics,
        "testMetrics": test_metrics,
        "limitations": [
            "FinEntity is financial-news entity sentiment, not a supply-chain event-type classifier.",
            "The baseline is TF-IDF plus Logistic Regression; no FinBERT result is claimed without a measured experiment.",
            "Entity matching outside the classifier remains a heuristic/business-data task and is not called machine learning.",
        ],
    }
    args.metadata_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    print(json.dumps({"modelVersion": model_version, "validationMetrics": validation_metrics, "testMetrics": test_metrics}, indent=2))


if __name__ == "__main__":
    main()
