"""Local, versioned bilingual target sentiment inference; never downloads at serving time."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

import numpy as np

from app.ml.sentiment_text import CONTRACT_VERSION, LABELS, SentimentInputError, infer_language, normalize_text, prepare_target_context


def route_language(text):
    """Serving-only calibration hint; corpus languages remain human/source-defined."""
    text = normalize_text(text).casefold()
    basic_tones = "áàãéèíìóòõúùý"
    return "vi" if infer_language(text) == "vi" or any(character in basic_tones for character in text) else "en"


def encode_pairs(tokenizer, entities, contexts, max_length=128, tensors=None):
    try:
        encoded = tokenizer(entities, contexts, max_length=max_length, padding="max_length", truncation="only_second",
                            return_token_type_ids=False, return_tensors=tensors)
    except Exception as error:
        raise SentimentInputError("ENTITY_TOO_LONG", "Entity does not fit the model token budget") from error
    marker = tokenizer.convert_tokens_to_ids("<target>")
    ending = tokenizer.convert_tokens_to_ids("</target>")
    retry = []
    for index, row in enumerate(encoded["input_ids"]):
        values = row.tolist() if hasattr(row, "tolist") else row
        if marker not in values or ending not in values:
            retry.append(index)
    for index in retry:
        # Rare long subword sequences: remove left context, preserve the whole target.
        position = contexts[index].find("<target>")
        value = tokenizer(entities[index], contexts[index][position:], max_length=max_length, padding="max_length",
                          truncation="only_second", return_token_type_ids=False)
        if marker not in value["input_ids"] or ending not in value["input_ids"]:
            raise SentimentInputError("TARGET_TRUNCATED", "Target mention does not fit; shorten the entity/context")
        for key in encoded:
            if tensors:
                import torch
                encoded[key][index] = torch.tensor(value[key], dtype=encoded[key].dtype)
            else:
                encoded[key][index] = value[key]
    return encoded


class SentimentRuntime:
    def __init__(self, manifest_path: Path, verify_hashes=True):
        self.manifest_path = Path(manifest_path)
        self.manifest = json.loads(self.manifest_path.read_text(encoding="utf-8"))
        if self.manifest.get("artifactVersion") != 2 or self.manifest.get("inputContract") != CONTRACT_VERSION:
            raise ValueError("Unsupported sentiment artifact contract")
        root = self.manifest_path.parent.resolve()
        self.directory = (root/self.manifest["release"]).resolve()
        if not self.directory.is_relative_to(root):
            raise ValueError("Sentiment release path leaves model directory")
        self.kind = self.manifest.get("kind")
        if self.kind not in {"transformer", "sklearn"}:
            raise ValueError("Unsupported sentiment runtime kind")
        required = {"metadata.json", "model.safetensors", "config.json", "tokenizer.json", "tokenizer_config.json"} if self.kind == "transformer" else {"metadata.json", "model.joblib"}
        files = self.manifest.get("files")
        if not isinstance(files, dict) or not required.issubset(files):
            raise ValueError("Sentiment manifest does not cover required artifact files")
        for filename in ("added_tokens.json", "special_tokens_map.json", "vocab.txt"):
            if self.kind == "transformer" and (self.directory/filename).is_file() and filename not in files:
                raise ValueError("Sentiment tokenizer override is not covered by the manifest")
        if verify_hashes:
            for filename, expected in self.manifest["files"].items():
                path = (self.directory/filename).resolve()
                if not path.is_relative_to(self.directory):
                    raise ValueError("Sentiment artifact file leaves release directory")
                with path.open("rb") as stream:
                    actual = hashlib.file_digest(stream, "sha256").hexdigest()
                if actual != expected:
                    raise ValueError("Sentiment artifact checksum mismatch")
        self.metadata = json.loads((self.directory/"metadata.json").read_text(encoding="utf-8"))
        if self.metadata.get("modelVersion") != self.manifest.get("modelVersion"):
            raise ValueError("Sentiment manifest and metadata versions differ")
        if self.kind == "transformer":
            import torch
            from transformers import AutoModelForSequenceClassification, AutoTokenizer
            self.torch = torch
            torch.set_num_threads(4)
            self.tokenizer = AutoTokenizer.from_pretrained(self.directory, local_files_only=True, trust_remote_code=False)
            self.model = AutoModelForSequenceClassification.from_pretrained(self.directory, local_files_only=True, trust_remote_code=False, use_safetensors=True)
            self.model.eval()
            if [self.model.config.id2label[index] for index in range(3)] != LABELS:
                raise ValueError("Sentiment classifier label order mismatch")
        elif self.kind == "sklearn":
            import joblib
            self.pipeline = joblib.load(self.directory/"model.joblib")
        else:
            raise ValueError("Unsupported sentiment runtime kind")

    def predict(self, requests):
        contexts = [prepare_target_context(request.entity, request.text) for request in requests]
        languages = [request.language if getattr(request, "language", "auto") != "auto" else route_language(request.text) for request in requests]
        if self.kind == "transformer":
            complete = self.tokenizer([item.entity for item in contexts], [item.context for item in contexts],
                                      truncation=False, padding=False, return_token_type_ids=False)
            token_truncated = [len(row) > self.metadata["trainingConfig"]["maxLength"] for row in complete["input_ids"]]
            encoded = encode_pairs(self.tokenizer, [item.entity for item in contexts], [item.context for item in contexts],
                                   self.metadata["trainingConfig"]["maxLength"], "pt")
            with self.torch.inference_mode():
                logits = self.model(**encoded).logits.cpu().numpy().astype(np.float64)
        else:
            token_truncated = [False] * len(contexts)
            texts = [f"TARGET={item.entity} || TEXT={item.context}" for item in contexts]
            probabilities = self.pipeline.predict_proba(texts)
            indices = [list(self.pipeline.classes_).index(label) for label in LABELS]
            logits = np.log(np.clip(probabilities[:,indices], 1e-12, 1.))
        output = []
        for request, context, language, row, clipped in zip(requests, contexts, languages, logits, token_truncated):
            scaled = row/self.metadata["calibration"]["temperatures"][language]
            probabilities = np.exp(scaled-scaled.max())
            probabilities /= probabilities.sum()
            chosen = int(probabilities.argmax())
            confidence = float(probabilities[chosen])
            review = self.metadata["calibration"]["reviewThresholds"][language]
            policy_met = review.get("policyMet", False)
            domain_review = (getattr(request, "domain", "general") == "supply_chain_news" and language == "vi"
                             and not self.metadata.get("vietnameseSupplyChainNewsGoldValidated", False))
            needs_review = domain_review or not policy_met or confidence < review["confidence"]
            output.append({"entity": request.entity.strip(), "sentiment": LABELS[chosen], "confidence": round(confidence,6),
                           "modelVersion": self.metadata["modelVersion"], "probabilities": {label: round(float(probability),6) for label, probability in zip(LABELS,probabilities)},
                           "language": language, "needsReview": needs_review,
                           "reviewReason": "DOMAIN_NOT_VALIDATED" if domain_review else ("VALIDATION_POLICY_UNMET" if not policy_met else ("LOW_CONFIDENCE" if needs_review else None)),
                           "inputTruncated": context.truncated or clipped})
        return output
