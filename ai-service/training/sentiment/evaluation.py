from __future__ import annotations

import numpy as np
from scipy.optimize import minimize_scalar
from scipy.special import logsumexp, softmax
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, precision_recall_fscore_support

from app.ml.sentiment_text import LABELS


def class_ids(frame):
    return np.asarray([LABELS.index(label) for label in frame["label"]], dtype=np.int64)


def classification_metrics(actual, predicted):
    precision, recall, f1, _ = precision_recall_fscore_support(actual, predicted, labels=[0,1,2], average="macro", zero_division=0)
    return {"samples": len(actual), "accuracy": float(accuracy_score(actual, predicted)), "macroPrecision": float(precision),
            "macroRecall": float(recall), "macroF1": float(f1),
            "confusionMatrix": confusion_matrix(actual, predicted, labels=[0,1,2]).tolist(),
            "perClass": classification_report(actual, predicted, labels=[0,1,2], target_names=LABELS, output_dict=True, zero_division=0)}


def grouped_f1_interval(actual, predicted, group_ids, rounds=500, seed=713):
    groups = sorted(set(group_ids))
    positions = {group: index for index, group in enumerate(groups)}
    counts = np.zeros((len(groups), 3, 3), dtype=np.int64)
    for truth, guess, group in zip(actual, predicted, group_ids):
        counts[positions[group], truth, guess] += 1
    rng = np.random.default_rng(seed)
    scores = []
    for _ in range(rounds):
        matrix = counts[rng.integers(0, len(groups), len(groups))].sum(axis=0)
        denominator = matrix.sum(axis=0) + matrix.sum(axis=1)
        f1 = np.divide(2*matrix.diagonal(), denominator, out=np.zeros(3), where=denominator != 0)
        scores.append(float(f1.mean()))
    return {"lower": float(np.quantile(scores, .025)), "upper": float(np.quantile(scores, .975)),
            "method": "document-group bootstrap", "resamples": rounds}


def summarize(frame, probabilities, bootstrap=False):
    actual, predicted = class_ids(frame), np.argmax(probabilities, axis=1)
    result = {"overall": classification_metrics(actual, predicted), "languages": {}, "datasets": {}}
    for field, key in (("language", "languages"), ("dataset", "datasets")):
        for value in sorted(frame[field].unique()):
            mask = np.asarray(frame[field] == value)
            result[key][value] = classification_metrics(actual[mask], predicted[mask])
            if bootstrap and field == "language":
                result[key][value]["macroF1CI95"] = grouped_f1_interval(actual[mask], predicted[mask], frame.loc[mask, "group_id"].tolist())
    mixed_contents = set(frame.groupby("canonical_text")["label"].nunique().loc[lambda values: values > 1].index)
    mask = np.asarray(frame["canonical_text"].isin(mixed_contents))
    result["mixedTarget"] = classification_metrics(actual[mask], predicted[mask]) if mask.any() else None
    if mask.any():
        correct = frame.loc[mask, ["canonical_text"]].copy()
        correct["correct"] = actual[mask] == predicted[mask]
        result["mixedTarget"]["allTargetsCorrectDocumentRate"] = float(correct.groupby("canonical_text")["correct"].all().mean())
        result["mixedTarget"]["documents"] = len(mixed_contents)
    return result


def selection_score(report):
    balanced = .5*(report["languages"]["en"]["macroF1"] + report["languages"]["vi"]["macroF1"])
    return .9*balanced + .1*report["mixedTarget"]["macroF1"] if report["mixedTarget"] else balanced


def fit_temperatures(frame, logits):
    actual = class_ids(frame)
    output = {}
    for language in ("en", "vi"):
        mask = np.asarray(frame["language"] == language)
        values, truth = np.asarray(logits[mask], dtype=np.float64), actual[mask]
        def objective(temperature):
            scaled = values/temperature
            return float((logsumexp(scaled, axis=1)-scaled[np.arange(len(truth)),truth]).mean())
        fitted = minimize_scalar(objective, bounds=(.3, 5.), method="bounded")
        output[language] = float(fitted.x) if fitted.success else 1.
    return output


def calibrated_probabilities(frame, logits, temperatures):
    values = np.asarray(logits, dtype=np.float64).copy()
    for language in ("en", "vi"):
        values[np.asarray(frame["language"] == language)] /= temperatures[language]
    return softmax(values, axis=1)


def calibration_metrics(frame, probabilities):
    actual = class_ids(frame)
    guesses = probabilities.argmax(axis=1)
    confidence = probabilities.max(axis=1)
    ece = 0.
    bins = np.minimum((confidence*10).astype(int), 9)
    for index in range(10):
        mask = bins == index
        if mask.any():
            ece += mask.mean()*abs(float((guesses[mask] == actual[mask]).mean())-float(confidence[mask].mean()))
    return {"negativeLogLikelihood": float(-np.log(np.clip(probabilities[np.arange(len(actual)),actual], 1e-12, 1.)).mean()),
            "brierScore": float(((probabilities-np.eye(3)[actual])**2).sum(axis=1).mean()), "ece10": float(ece)}


def review_thresholds(frame, probabilities):
    actual = class_ids(frame)
    predicted = probabilities.argmax(axis=1)
    confidence = probabilities.max(axis=1)
    thresholds = {}
    for language in ("en", "vi"):
        lang = np.asarray(frame["language"] == language)
        selected = None
        for threshold in np.arange(.45, .951, .025):
            kept = lang & (confidence >= threshold)
            negative = kept & (predicted == 0)
            if kept.sum() < max(20, int(lang.sum()*.25)) or negative.sum() < 10:
                continue
            accuracy = float((predicted[kept] == actual[kept]).mean())
            negative_precision = float((actual[negative] == 0).mean())
            if accuracy >= .80 and negative_precision >= .80:
                selected = {"confidence": float(threshold), "validationCoverage": float(kept.sum()/lang.sum()),
                            "validationAcceptedAccuracy": accuracy, "validationNegativePrecision": negative_precision,
                            "policyMet": True}
                break
        thresholds[language] = selected or {"confidence": .95, "validationCoverage": float((lang & (confidence >= .95)).sum()/lang.sum()),
                                            "policyMet": False, "note": "Precision target not demonstrated; all predictions require review."}
    return thresholds
