"""Full-corpus GPU/CPU fine-tuning with validation-only selection and epoch resume."""
from __future__ import annotations

import gc
import hashlib
import json
import math
import os
import platform
import random
import time
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from scipy.special import softmax
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline

from app.ml.sentiment_runtime import encode_pairs
from app.ml.sentiment_text import CONTRACT_VERSION, LABELS
from training.sentiment.data import SOURCES_PATH, save_json, sha256
from training.sentiment.evaluation import class_ids, selection_score, summarize


def seed_everything(seed, torch):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def baseline_text(frame, targeted):
    content = frame["context"] if targeted else frame["text"]
    return ("TARGET=" + frame["entity"] + " || TEXT=" + content).tolist()


def train_baselines(train_frame, validation, run):
    reports = []
    fingerprint = hashlib.sha256((train_frame[["sample_id","entity","label","context"]].to_json(orient="records")+validation[["sample_id","entity","label","context"]].to_json(orient="records")).encode("utf-8")).hexdigest()
    for targeted in (False, True):
        for strength in (.5, 2.):
            name = ("target_context" if targeted else "legacy_document") + f"_lr_C{strength}"
            destination = run/name
            destination.mkdir(exist_ok=True)
            if (destination/"candidate.json").exists() and (destination/"model.joblib").exists():
                previous = json.loads((destination/"candidate.json").read_text(encoding="utf-8"))
                if previous.get("inputFingerprint") == fingerprint:
                    reports.append(previous)
                    print(f"Reuse completed baseline {name}", flush=True)
                    continue
            pipeline = Pipeline([("tfidf", TfidfVectorizer(ngram_range=(1,2), min_df=2, max_features=50000, sublinear_tf=True)),
                                 ("classifier", LogisticRegression(C=strength, class_weight="balanced", max_iter=1500, random_state=42))])
            pipeline.fit(baseline_text(train_frame, targeted), train_frame["label"])
            probabilities = pipeline.predict_proba(baseline_text(validation, targeted))
            indices = [list(pipeline.classes_).index(label) for label in LABELS]
            report = summarize(validation, probabilities[:,indices])
            item = {"candidate": name, "kind": "sklearn", "targeted": targeted, "inputFingerprint": fingerprint, "validationScore": selection_score(report), "validation": report}
            reports.append(item)
            joblib.dump(pipeline, destination/"model.joblib")
            np.save(destination/"validation-logits.npy", np.log(np.clip(probabilities[:,indices], 1e-12, 1.)))
            save_json(destination/"candidate.json", item)
            print(json.dumps({"baseline": name, "validationScore": item["validationScore"]}), flush=True)
    return reports


def transformer_logits(model, encoded, device, batch_size, torch):
    model.eval()
    output = []
    with torch.inference_mode():
        for first in range(0, len(encoded["input_ids"]), batch_size):
            batch = {key: value[first:first+batch_size].to(device) for key, value in encoded.items()}
            with torch.autocast(device_type=device.type, dtype=torch.float16, enabled=device.type == "cuda"):
                output.append(model(**batch).logits.float().cpu().numpy())
    return np.concatenate(output)


def encode_frame(tokenizer, frame, max_length, torch):
    pieces = []
    for first in range(0,len(frame),512):
        part = frame.iloc[first:first+512]
        pieces.append(encode_pairs(tokenizer, part["entity"].tolist(), part["context"].tolist(), max_length, "pt"))
    return {key:torch.cat([piece[key] for piece in pieces]) for key in pieces[0]}


def train_transformer(train_frame, validation, run, pretrained, seed, learning_rate, args, torch):
    from torch.utils.data import DataLoader, TensorDataset
    from transformers import AutoModelForSequenceClassification, AutoTokenizer, get_linear_schedule_with_warmup
    seed_everything(seed, torch)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    name = f"distilmbert_seed{seed}_lr{learning_rate:g}"
    destination = run/name
    destination.mkdir(exist_ok=True)
    if args.resume and (destination/"candidate.json").is_file():
        completed = json.loads((destination/"candidate.json").read_text(encoding="utf-8"))
        if completed.get("completed"):
            print(f"Reuse completed transformer {name}", flush=True)
            return completed
    checkpoint_path = destination/"checkpoint.pt"
    tokenizer = AutoTokenizer.from_pretrained(pretrained, local_files_only=True, trust_remote_code=False)
    tokenizer.add_special_tokens({"additional_special_tokens": ["<target>", "</target>"]})
    tokenizer.save_pretrained(destination)
    train_encoded = encode_frame(tokenizer, train_frame, args.max_length, torch)
    validation_encoded = encode_frame(tokenizer, validation, args.max_length, torch)
    model = AutoModelForSequenceClassification.from_pretrained(pretrained, num_labels=3,
        id2label=dict(enumerate(LABELS)), label2id={label:index for index,label in enumerate(LABELS)},
        local_files_only=True, trust_remote_code=False, use_safetensors=True, attn_implementation="sdpa")
    model.resize_token_embeddings(len(tokenizer), mean_resizing=False)
    model.gradient_checkpointing_enable()
    model.to(device)
    labels = torch.tensor(class_ids(train_frame), dtype=torch.long)
    dataset = TensorDataset(train_encoded["input_ids"], train_encoded["attention_mask"], labels)
    generator = torch.Generator().manual_seed(seed)
    loader = DataLoader(dataset, batch_size=args.batch_size, shuffle=True, num_workers=0, pin_memory=device.type == "cuda", generator=generator)
    accumulation = 4
    steps_per_epoch = math.ceil(len(loader)/accumulation)
    optimizer = torch.optim.AdamW(model.parameters(), lr=learning_rate, weight_decay=.01)
    scheduler = get_linear_schedule_with_warmup(optimizer, int(steps_per_epoch*args.epochs*.1), steps_per_epoch*args.epochs)
    counts = np.bincount(class_ids(train_frame), minlength=3)
    weights = np.sqrt(counts.sum()/(3*counts))
    weights /= weights.mean()
    criterion = torch.nn.CrossEntropyLoss(weight=torch.tensor(weights, dtype=torch.float32, device=device))
    scaler = torch.amp.GradScaler("cuda", enabled=device.type == "cuda")
    start_epoch, best_score, stale, best_report = 0, -1., 0, None
    history = []
    if args.resume and checkpoint_path.exists():
        checkpoint = torch.load(checkpoint_path, map_location="cpu", weights_only=False)
        model.load_state_dict(checkpoint["model"])
        optimizer.load_state_dict(checkpoint["optimizer"])
        scheduler.load_state_dict(checkpoint["scheduler"])
        scaler.load_state_dict(checkpoint["scaler"])
        start_epoch, best_score, stale = checkpoint["epoch"]+1, checkpoint["best_score"], checkpoint["stale"]
        best_report, history = checkpoint["best_report"], checkpoint["history"]
        random.setstate(checkpoint["python_rng"])
        np.random.set_state(checkpoint["numpy_rng"])
        torch.set_rng_state(checkpoint["torch_rng"])
        generator.set_state(checkpoint["loader_rng"])
        if device.type == "cuda" and checkpoint["cuda_rng"] is not None:
            torch.cuda.set_rng_state_all(checkpoint["cuda_rng"])
        del checkpoint
        print(f"Resuming {name} at epoch {start_epoch+1}", flush=True)
    print(json.dumps({"candidate": name, "device": str(device), "trainingAnnotations": len(train_frame),
                      "batchSize": args.batch_size, "effectiveBatchSize": args.batch_size*accumulation}), flush=True)
    for epoch in range(start_epoch, args.epochs):
        model.train()
        optimizer.zero_grad(set_to_none=True)
        loss_sum, examples = 0., 0
        started = time.perf_counter()
        for step, (input_ids, attention_mask, truth) in enumerate(loader):
            batch = {"input_ids": input_ids.to(device), "attention_mask": attention_mask.to(device)}
            with torch.autocast(device_type=device.type, dtype=torch.float16, enabled=device.type == "cuda"):
                logits = model(**batch).logits
                loss = criterion(logits.float(), truth.to(device))
            if not torch.isfinite(loss):
                raise RuntimeError("Nonfinite training loss; checkpoint was not promoted")
            divisor = min(accumulation, len(loader)-(step//accumulation)*accumulation)
            scaler.scale(loss/divisor).backward()
            loss_sum += float(loss.detach())*len(truth)
            examples += len(truth)
            if (step+1) % accumulation == 0 or step+1 == len(loader):
                scaler.unscale_(optimizer)
                torch.nn.utils.clip_grad_norm_(model.parameters(), 1.)
                previous_scale = scaler.get_scale()
                scaler.step(optimizer)
                scaler.update()
                if scaler.get_scale() >= previous_scale:
                    scheduler.step()
                optimizer.zero_grad(set_to_none=True)
            if (step+1) % 250 == 0:
                print(json.dumps({"candidate": name, "epoch": epoch+1, "step": step+1, "steps": len(loader),
                                  "loss": loss_sum/examples, "elapsedSeconds": round(time.perf_counter()-started,1)}), flush=True)
        validation_logits = transformer_logits(model, validation_encoded, device, args.batch_size*2, torch)
        report = summarize(validation, softmax(validation_logits, axis=1))
        score = selection_score(report)
        history.append({"epoch": epoch+1, "trainLoss": loss_sum/examples, "validationScore": score,
                        "validationEnMacroF1": report["languages"]["en"]["macroF1"], "validationViMacroF1": report["languages"]["vi"]["macroF1"],
                        "elapsedSeconds": time.perf_counter()-started})
        if score > best_score + 1e-5:
            best_score, best_report, stale = score, report, 0
            model.save_pretrained(destination, safe_serialization=True)
            np.save(destination/"validation-logits.npy", validation_logits)
        else:
            stale += 1
        state = {"model": model.state_dict(), "optimizer": optimizer.state_dict(), "scheduler": scheduler.state_dict(),
                 "scaler": scaler.state_dict(), "epoch": epoch, "best_score": best_score, "best_report": best_report,
                 "stale": stale, "history": history, "python_rng": random.getstate(), "numpy_rng": np.random.get_state(),
                 "torch_rng": torch.get_rng_state(), "loader_rng": generator.get_state(),
                 "cuda_rng": torch.cuda.get_rng_state_all() if device.type == "cuda" else None}
        temporary = checkpoint_path.with_suffix(".tmp")
        torch.save(state, temporary)
        temporary.replace(checkpoint_path)
        del state
        save_json(destination/"history.json", history)
        print(json.dumps({"candidate": name, **history[-1], "peakGpuMemoryMB": round(torch.cuda.max_memory_allocated()/1024**2,1) if device.type == "cuda" else None}), flush=True)
        if stale >= 2:
            break
    candidate = {"candidate": name, "kind": "transformer", "validationScore": best_score, "validation": best_report,
                 "seed": seed, "learningRate": learning_rate, "completedEpochs": len(history), "classWeights": weights.tolist(), "completed": True}
    save_json(destination/"candidate.json", candidate)
    checkpoint_path.unlink(missing_ok=True)
    del model, optimizer, scheduler, scaler, train_encoded, validation_encoded, loader
    gc.collect()
    if torch.cuda.is_available():
        torch.cuda.empty_cache()
    return candidate


def train(root: Path, args):
    import torch
    torch.set_num_threads(4)
    if torch.cuda.is_available():
        torch.backends.cuda.matmul.allow_tf32 = True
    prepared = root/args.prepared
    data_report = json.loads((prepared/"data-report.json").read_text(encoding="utf-8"))
    for name, expected in data_report["splitFiles"].items():
        if sha256(prepared/f"{name}.jsonl") != expected:
            raise ValueError("Prepared dataset changed after its split manifest was written")
    train_frame = pd.read_json(prepared/"train.jsonl", lines=True)
    validation = pd.read_json(prepared/"validation.jsonl", lines=True)
    run = root/(args.run_dir or Path("data/processed/sentiment-runs")/datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"))
    run.mkdir(parents=True, exist_ok=True)
    existing = run/"run-config.json"
    pretrained_source = json.loads(SOURCES_PATH.read_text(encoding="utf-8"))["pretrainedModel"]
    pretrained_manifest_path = root/pretrained_source["localPath"]/"source-manifest.json"
    if not pretrained_manifest_path.is_file():
        raise ValueError("Pretrained provenance manifest is missing; run pipeline download before training")
    pretrained_manifest = json.loads(pretrained_manifest_path.read_text(encoding="utf-8"))
    if pretrained_manifest["revision"] != pretrained_source["revision"]:
        raise ValueError("Pretrained model revision does not match pinned sources")
    for filename, expected in pretrained_manifest["sha256"].items():
        if sha256(root/pretrained_source["localPath"]/filename) != expected:
            raise ValueError("Pretrained file checksum mismatch: " + filename)
    config = {"artifactVersion": 2, "inputContract": CONTRACT_VERSION, "prepared": str(prepared.relative_to(root)),
              "splitFiles": data_report["splitFiles"], "maxLength": args.max_length, "batchSize": args.batch_size,
              "epochs": args.epochs, "earlyStoppingPatience": 2, "gradientAccumulation": 4,
              "selectionMetric": "0.45*en_macroF1 + 0.45*vi_macroF1 + 0.10*mixed_target_macroF1",
              "python": platform.python_version(), "torch": torch.__version__, "cuda": torch.version.cuda,
              "device": torch.cuda.get_device_name(0) if torch.cuda.is_available() else "CPU",
              "pretrainedModel": pretrained_manifest,
              "sourceFiles": {str(path.relative_to(root)): sha256(path) for path in sorted((root/"training/sentiment").glob("*.py"))},
              "preprocessingSha256": sha256(root/"app/ml/sentiment_text.py")}
    if existing.exists():
        if not args.resume:
            raise ValueError("Run already exists; use a fresh --run-dir or --resume")
        previous = json.loads(existing.read_text(encoding="utf-8"))
        for key in ("splitFiles", "maxLength", "batchSize", "epochs", "preprocessingSha256"):
            if previous[key] != config[key]:
                raise ValueError(f"Cannot resume with changed {key}")
    save_json(existing, config)
    save_json(run/"data-report.json", data_report)
    print(json.dumps({"runDir": str(run), "device": config["device"], "train": len(train_frame), "validation": len(validation)}), flush=True)
    reports = train_baselines(train_frame, validation, run)
    source = json.loads(SOURCES_PATH.read_text(encoding="utf-8"))["pretrainedModel"]
    for seed, learning_rate in ((42, 3e-5), (43, 2e-5)):
        reports.append(train_transformer(train_frame, validation, run, root/source["localPath"], seed, learning_rate, args, torch))
    save_json(run/"candidates.json", reports)
    # A legacy document-level baseline is a comparator, not a target-aware deployable.
    eligible = [candidate for candidate in reports if candidate["kind"] == "transformer" or candidate.get("targeted")]
    winner = max(eligible, key=lambda candidate: candidate["validationScore"])
    save_json(run/"selection.json", {"selected": winner["candidate"], "kind": winner["kind"], "validationScore": winner["validationScore"],
                                    "testUsedForSelection": False, "candidates": [{key: row[key] for key in ("candidate","kind","validationScore")} for row in reports]})
    print(json.dumps({"selectedByValidation": winner["candidate"], "validationScore": winner["validationScore"], "testNotYetEvaluated": True}), flush=True)
