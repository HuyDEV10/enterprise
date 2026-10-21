from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

import pandas as pd

FINENTITY_REPOSITORY = "https://github.com/yixuantt/FinEntity"
FINENTITY_COMMIT = "3b6cedc5485b669c2ed168f1d949f517636eb7b8"
FINENTITY_RAW_URL = (
    "https://raw.githubusercontent.com/yixuantt/FinEntity/"
    f"{FINENTITY_COMMIT}/data/FinEntity.json"
)
VALID_LABELS = {"POSITIVE", "NEGATIVE", "NEUTRAL"}


def sha256_file(path: Path, chunk_size: int = 1024 * 1024) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as handle:
        while chunk := handle.read(chunk_size):
            digest.update(chunk)
    return digest.hexdigest()


def load_finentity(path: Path) -> tuple[pd.DataFrame, dict[str, Any]]:
    path = Path(path)
    if not path.is_file():
        raise FileNotFoundError(f"FinEntity dataset not found: {path}")

    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, list) or not payload:
        raise ValueError("FinEntity.json must be a non-empty JSON array")

    rows: list[dict[str, str]] = []
    original_contents = 0
    for sample_index, sample in enumerate(payload):
        if not isinstance(sample, dict):
            raise ValueError(f"FinEntity sample {sample_index} is not an object")
        content = str(sample.get("content", "")).strip()
        annotations = sample.get("annotations")
        if not content or not isinstance(annotations, list):
            raise ValueError(f"FinEntity sample {sample_index} has invalid content/annotations")
        original_contents += 1
        group_id = hashlib.sha256(content.encode("utf-8")).hexdigest()
        for annotation_index, annotation in enumerate(annotations):
            if not isinstance(annotation, dict):
                raise ValueError(f"Annotation {sample_index}:{annotation_index} is not an object")
            entity = str(annotation.get("value", "")).strip()
            label = str(annotation.get("label", annotation.get("tag", ""))).upper().strip()
            if not entity or label not in VALID_LABELS:
                raise ValueError(
                    f"Invalid FinEntity annotation {sample_index}:{annotation_index}: entity={entity!r}, label={label!r}"
                )
            rows.append(
                {
                    "content": content,
                    "entity": entity,
                    "label": label,
                    "group_id": group_id,
                    "input_text": f"TARGET={entity} || TEXT={content}",
                }
            )

    frame = pd.DataFrame(rows)
    if frame.empty:
        raise ValueError("FinEntity contains no entity-level annotations")

    metadata = {
        "repository": FINENTITY_REPOSITORY,
        "commit": FINENTITY_COMMIT,
        "sha256": sha256_file(path),
        "contentCount": original_contents,
        "annotationCount": int(len(frame)),
        "labelCounts": {label: int((frame["label"] == label).sum()) for label in sorted(VALID_LABELS)},
    }
    return frame, metadata
