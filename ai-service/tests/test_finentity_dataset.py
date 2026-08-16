import json
from pathlib import Path

from training.sentiment.finentity_dataset import load_finentity


def test_finentity_loader_expands_entity_annotations(tmp_path: Path) -> None:
    # Technical schema fixture only; never used for training/evaluation metrics.
    path = tmp_path / "FinEntity.json"
    path.write_text(
        json.dumps(
            [
                {
                    "content": "Company A rose while Company B fell.",
                    "annotations": [
                        {"value": "Company A", "label": "Positive"},
                        {"value": "Company B", "label": "Negative"},
                    ],
                }
            ]
        ),
        encoding="utf-8",
    )

    frame, metadata = load_finentity(path)

    assert len(frame) == 2
    assert set(frame["label"]) == {"POSITIVE", "NEGATIVE"}
    assert frame["group_id"].nunique() == 1
    assert metadata["annotationCount"] == 2
