"""Locked holdout evaluation and atomic, hash-verified local promotion."""
from __future__ import annotations

import json
import shutil
from datetime import datetime, timezone
from pathlib import Path
from types import SimpleNamespace

import joblib
import numpy as np
import pandas as pd

from app.ml.sentiment_text import CONTRACT_VERSION, LABELS
from training.sentiment.data import SOURCES_PATH, save_json, sha256
from training.sentiment.evaluation import (calibrated_probabilities, calibration_metrics, fit_temperatures,
                                          review_thresholds, summarize)
from training.sentiment.trainer import baseline_text


def candidate_artifact_hashes(directory: Path, kind: str):
    """Fingerprint every serving file, including optional tokenizer overrides."""
    if kind == "transformer":
        required = {"config.json", "model.safetensors", "tokenizer.json", "tokenizer_config.json"}
        allowed = required | {"special_tokens_map.json", "vocab.txt", "added_tokens.json"}
    elif kind == "sklearn":
        required = allowed = {"model.joblib"}
    else:
        raise ValueError("Unsupported candidate artifact kind")
    if any(not (directory/name).is_file() for name in required):
        raise ValueError("Candidate serving artifact is incomplete")
    return {name: sha256(directory/name) for name in sorted(allowed) if (directory/name).is_file()}


def assert_evaluated_artifacts(run: Path, report):
    expected = report.get("selectedArtifactSha256")
    if not isinstance(expected, dict) or not expected:
        raise ValueError("Evaluation report does not pin the selected serving artifacts")
    selected = report["selected"]
    actual = candidate_artifact_hashes(run/selected["candidate"], selected["kind"])
    if actual != expected:
        raise ValueError("Candidate serving artifacts changed since holdout evaluation; promotion refused")
    return actual


def candidate_logits(root, run, candidate, frame, config):
    directory = run/candidate["candidate"]
    if candidate["kind"] == "sklearn":
        pipeline = joblib.load(directory/"model.joblib")
        probabilities = pipeline.predict_proba(baseline_text(frame, candidate["targeted"]))
        positions = [list(pipeline.classes_).index(label) for label in LABELS]
        return np.log(np.clip(probabilities[:,positions], 1e-12, 1.))
    import torch
    from transformers import AutoModelForSequenceClassification, AutoTokenizer
    from app.ml.sentiment_runtime import encode_pairs
    from training.sentiment.trainer import transformer_logits
    torch.set_num_threads(4)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    tokenizer = AutoTokenizer.from_pretrained(directory, local_files_only=True, trust_remote_code=False)
    encoded = encode_pairs(tokenizer, frame["entity"].tolist(), frame["context"].tolist(), config["maxLength"], "pt")
    model = AutoModelForSequenceClassification.from_pretrained(directory, local_files_only=True, trust_remote_code=False, use_safetensors=True).to(device)
    values = transformer_logits(model, encoded, device, config["batchSize"]*2, torch)
    del model, encoded
    if device.type == "cuda":
        torch.cuda.empty_cache()
    return values


def evaluate_run(root: Path, run: Path):
    report_path = run/"evaluation.json"
    if report_path.exists():
        print(f"Holdout evaluation is already locked: {report_path}", flush=True)
        return json.loads(report_path.read_text(encoding="utf-8"))
    config = json.loads((run/"run-config.json").read_text(encoding="utf-8"))
    selection = json.loads((run/"selection.json").read_text(encoding="utf-8"))
    candidates = json.loads((run/"candidates.json").read_text(encoding="utf-8"))
    data_report = json.loads((run/"data-report.json").read_text(encoding="utf-8"))
    prepared = root/config["prepared"]
    for name, expected in config["splitFiles"].items():
        if sha256(prepared/f"{name}.jsonl") != expected:
            raise ValueError("Refusing evaluation on modified split files")
    validation = pd.read_json(prepared/"validation.jsonl", lines=True)
    test = pd.read_json(prepared/"test.jsonl", lines=True)
    selected = next(candidate for candidate in candidates if candidate["candidate"] == selection["selected"])
    selected_artifacts = candidate_artifact_hashes(run/selected["candidate"], selected["kind"])
    validation_logits = np.load(run/selected["candidate"]/"validation-logits.npy")
    temperatures = fit_temperatures(validation, validation_logits)
    validation_probabilities = calibrated_probabilities(validation, validation_logits, temperatures)
    thresholds = review_thresholds(validation, validation_probabilities)
    test_logits = candidate_logits(root, run, selected, test, config)
    test_probabilities = calibrated_probabilities(test, test_logits, temperatures)
    comparisons = {}
    for targeted in (False, True):
        baseline = max((candidate for candidate in candidates if candidate["kind"] == "sklearn" and candidate["targeted"] == targeted), key=lambda candidate: candidate["validationScore"])
        logits = candidate_logits(root, run, baseline, test, config)
        probabilities = np.exp(logits)
        comparisons["targeted" if targeted else "legacyDocument"] = {"candidate": baseline["candidate"], "selectedUsing": "validation only", "test": summarize(test, probabilities)}
    prediction = test.copy()
    prediction["predicted"] = [LABELS[index] for index in test_probabilities.argmax(axis=1)]
    prediction["confidence"] = test_probabilities.max(axis=1)
    prediction["needs_review"] = [not thresholds[language]["policyMet"] or confidence < thresholds[language]["confidence"]
                                  for confidence,language in zip(prediction["confidence"],prediction["language"])]
    prediction.to_json(run/"test-predictions.jsonl", orient="records", lines=True, force_ascii=False)
    prediction.loc[prediction["label"] != prediction["predicted"]].to_csv(run/"test-errors.csv", index=False, encoding="utf-8-sig")
    test_metrics = summarize(test, test_probabilities, bootstrap=True)
    baseline_f1 = comparisons["targeted"]["test"]["overall"]["macroF1"]
    gates = {"noDocumentLeakage": data_report["groupLeakage"] is False,
             "selectionDidNotUseTest": selection["testUsedForSelection"] is False,
             "finiteProbabilities": bool(np.isfinite(test_probabilities).all()),
             "noMaterialOverallRegressionVsRetrainedTargetedBaseline": test_metrics["overall"]["macroF1"] >= baseline_f1-.02,
             "noMaterialEnglishRegressionVsTargetedBaseline": test_metrics["languages"]["en"]["macroF1"] >= comparisons["targeted"]["test"]["languages"]["en"]["macroF1"]-.02,
             "noMaterialVietnameseRegressionVsTargetedBaseline": test_metrics["languages"]["vi"]["macroF1"] >= comparisons["targeted"]["test"]["languages"]["vi"]["macroF1"]-.02,
             "englishMacroF1AtLeast060": test_metrics["languages"]["en"]["macroF1"] >= .60,
             "vietnameseMacroF1AtLeast050": test_metrics["languages"]["vi"]["macroF1"] >= .50}
    if candidate_artifact_hashes(run/selected["candidate"], selected["kind"]) != selected_artifacts:
        raise ValueError("Selected serving artifacts changed during evaluation")
    report = {"evaluatedAt": datetime.now(timezone.utc).isoformat(), "selected": selected, "selection": selection,
              "selectedArtifactSha256": selected_artifacts,
              "evaluationSourceSha256": {name: sha256(Path(__file__).with_name(name)) for name in ("lifecycle.py", "evaluation.py")},
              "trainingConfig": config, "dataReport": data_report, "validation": summarize(validation, validation_probabilities),
              "calibration": {"fittedOn": "validation only", "temperatures": temperatures, "reviewThresholds": thresholds,
                              "validationBefore": calibration_metrics(validation, calibrated_probabilities(validation,validation_logits,{"en":1.,"vi":1.})),
                              "validationAfter": calibration_metrics(validation, validation_probabilities),
                              "test": calibration_metrics(test, test_probabilities)},
              "test": test_metrics, "baselines": comparisons, "qualityGates": gates, "promotable": all(gates.values()),
              "vietnameseSupplyChainNewsGoldValidated": False,
              "limitations": data_report["limitations"] + ["Test metrics are from public corpora, not a labelled GDELT supplier-disruption benchmark.",
                  "Entity matching, event severity and business exposure are separate tasks; model confidence is not a probability of supplier failure.",
                  "Reported test predictions are locked. Do not tune against their errors and reuse the same holdout as a fresh test."]}
    save_json(report_path, report)
    write_model_card(run/"MODEL-CARD.md", report)
    print(json.dumps({"selected": selected["candidate"], "testMacroF1": test_metrics["overall"]["macroF1"],
                      "testEnMacroF1": test_metrics["languages"]["en"]["macroF1"], "testViMacroF1": test_metrics["languages"]["vi"]["macroF1"],
                      "qualityGates": gates}, ensure_ascii=False), flush=True)
    return report


def write_model_card(path: Path, report):
    selected, config = report["selected"], report["trainingConfig"]
    rows = []
    for name, value in [("Selected", report["test"]), ("Target-context TF-IDF", report["baselines"]["targeted"]["test"]), ("Legacy document TF-IDF (retrained)", report["baselines"]["legacyDocument"]["test"])]:
        rows.append(f"| {name} | {value['overall']['macroF1']:.4f} | {value['languages']['en']['macroF1']:.4f} | {value['languages']['vi']['macroF1']:.4f} | {value['overall']['accuracy']:.4f} |")
    text = f"""# Bilingual target sentiment model card

Selected candidate: `{selected['candidate']}`. Selection: validation only; the heldout report is locked.
Input: explicit entity mention + original English/Vietnamese text. Labels: NEGATIVE / NEUTRAL / POSITIVE.
Transformer initialization: distilbert/distilbert-base-multilingual-cased, revision 45c032ab32cc946ad88a166f7cb282f58c753c2e (Apache-2.0).

| Model | Test macro F1 | English macro F1 | Vietnamese macro F1 | Accuracy |
| --- | ---: | ---: | ---: | ---: |
{chr(10).join(rows)}

This is a new document-disjoint experiment. Historical metadata from the old 70/15/15 FinEntity-only split is not directly comparable.

Training: {config['device']}; max length {config['maxLength']}; batch {config['batchSize']}; gradient accumulation {config['gradientAccumulation']}; max {config['epochs']} epochs; early stopping patience 2. Every training epoch traverses the full retained training set. Class weighting uses training labels only.

Splits: global canonical/near-duplicate document groups, approximately 80/10/10; official bank test examples remain held out. Exact target duplicates and contradictory target/aspect labels are quarantined. Character features used for duplicate detection never become fitted classifier features.

Calibration: language-specific temperature scaling and review thresholds fitted only on validation. `needsReview` means confidence did not meet that validation policy; the business layer must not treat it as accepted negative evidence.

## Sources and domain coverage

- FinEntity: https://github.com/yixuantt/FinEntity (ODC-BY). Human entity-level financial news.
- SEntFiN: https://github.com/pyRis/SEntFiN (MIT in upstream repository). Human entity-level financial headlines.
- ViTASA: https://github.com/kh4nh12/ViTASA (MIT in upstream repository). Native Vietnamese human target/aspect spans in mobile, hotel and restaurant reviews.
- UTS2017_Bank: https://huggingface.co/datasets/undertheseanlp/UTS2017_Bank (Apache-2.0 publisher card). Only TRADEMARK labels with one explicitly mentioned bank are retained; its official test split is preserved.

Vietnamese support has native-language training/evaluation, but Vietnamese multi-company financial/supply-chain news is not a validated gold domain. ViTASA is not represented as financial-news data, and a bank-brand label is not assigned to every entity in a review. The bank test subset is small; its per-class support is in evaluation.json.

No model-generated labels, automatic translations, synthetic QA fixtures, or GDELT AvgTone are used for training or reported test metrics.

## Business boundary

GDELT discovers real events/source URLs. Sentiment requires actual article text/headline and the intended company mention. The classifier does not identify suppliers or predict inventory disruption. Supplier identity matching, event severity, open orders, shipments and inventory exposure are handled in Spring Boot before risk/alert creation. Low-confidence sentiment requires review. A positive article about one company must never be applied as another supplier's sentiment.

## Evaluation artifacts

- evaluation.json: per-language/per-dataset metrics, per-class precision/recall/F1, confusion matrices, grouped bootstrap intervals, calibration and promotion gates.
- test-predictions.jsonl / test-errors.csv: real heldout predictions for error analysis; not a new training set.
- data-report.json / split-manifest.json: checksums, provenance, grouping and exclusions.
- run-config.json / candidate histories: seeds, hyperparameters, software/hardware and source fingerprints.

Limitations:
{chr(10).join('- '+limitation for limitation in report['limitations'])}

Automatic local promotion gates: {json.dumps(report['qualityGates'], ensure_ascii=False)}.
"""
    path.write_text(text, encoding="utf-8")


def promote_run(root: Path, run: Path):
    from app.ml.sentiment_runtime import SentimentRuntime
    report = json.loads((run/"evaluation.json").read_text(encoding="utf-8"))
    if not report["promotable"] or not all(report["qualityGates"].values()):
        raise ValueError("Model quality gates failed; current production artifact was kept")
    selected = report["selected"]
    evaluated_artifacts = assert_evaluated_artifacts(run, report)
    version = "bilingual-entity-v2-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    model_root = root/"models/entity_sentiment_model"
    destination = model_root/"releases"/version
    destination.mkdir(parents=True, exist_ok=False)
    candidate = run/selected["candidate"]
    for filename in evaluated_artifacts:
        shutil.copy2(candidate/filename, destination/filename)
    if candidate_artifact_hashes(destination, selected["kind"]) != evaluated_artifacts:
        raise ValueError("Candidate serving artifacts changed while copying; active model was kept")
    metadata = {"artifactVersion": 2, "modelName": "DISTILMBERT_TARGET_SENTIMENT" if selected["kind"] == "transformer" else "TARGET_CONTEXT_TFIDF_LOGREG",
                "modelVersion": version, "trainedAt": report["evaluatedAt"], "inputContract": CONTRACT_VERSION,
                "languages": ["en", "vi"], "labels": LABELS, "trainingConfig": report["trainingConfig"],
                "dataset": {"name": "FinEntity+SEntFiN+ViTASA+UTS2017Bank-brand", "sources": report["dataReport"]["sources"]},
                "selectionMetric": report["trainingConfig"]["selectionMetric"], "validationMetrics": report["validation"],
                "testMetrics": report["test"], "calibration": report["calibration"], "limitations": report["limitations"],
                "vietnameseSupplyChainNewsGoldValidated": False,
                "servingPolicy": {"vietnameseSupplyChainNewsRequiresReview": True, "requestDomain": "supply_chain_news"},
                "servingSourceSha256": {name: sha256(root/"app/ml"/name) for name in ("sentiment_text.py", "sentiment_runtime.py")},
                "evaluatedArtifactSha256": evaluated_artifacts,
                "evaluationSourceSha256": report.get("evaluationSourceSha256", {})}
    save_json(destination/"metadata.json", metadata)
    shutil.copy2(run/"MODEL-CARD.md", destination/"MODEL-CARD.md")
    with (destination/"MODEL-CARD.md").open("a", encoding="utf-8") as card:
        card.write("\n## Serving domain policy\n\nSpring Boot requests use domain=supply_chain_news. Vietnamese inputs in that domain always require review (DOMAIN_NOT_VALIDATED), regardless of confidence, until an independent human-labelled Vietnamese supplier-news benchmark is validated. General-domain public-corpus metrics above do not validate multi-company Vietnamese news; a technical contrastive news example failed despite high confidence.\n")
    manifest = {"artifactVersion": 2, "inputContract": CONTRACT_VERSION, "kind": selected["kind"], "modelVersion": version,
                "release": f"releases/{version}", "files": {path.name:sha256(path) for path in destination.iterdir() if path.is_file()}}
    temporary = model_root/"active.candidate.json"
    save_json(temporary, manifest)
    runtime = SentimentRuntime(temporary)
    validation = pd.read_json(root/report["trainingConfig"]["prepared"]/"validation.jsonl", lines=True)
    for language in ("en", "vi"):
        sample = validation.loc[validation["language"] == language].iloc[0]
        response = runtime.predict([SimpleNamespace(entity=sample.entity, text=sample.text, language=language)])[0]
        if response["modelVersion"] != version or abs(sum(response["probabilities"].values())-1.) > 1e-5:
            raise ValueError("Serving preflight did not reproduce the artifact contract")
    active = model_root/"active.json"
    if active.exists():
        shutil.copy2(active, model_root/"active.previous.json")
    temporary.replace(active)
    save_json(run/"promotion.json", {"modelVersion": version, "manifest": str(active), "servingPreflight": "en+vi passed", "previousLegacyArtifactPreserved": True})
    print(json.dumps({"promotedModelVersion": version, "manifest": str(active)}), flush=True)
