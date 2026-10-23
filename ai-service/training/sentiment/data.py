"""Verified public corpora, conservative target labels and document-disjoint splits."""
from __future__ import annotations

import ast
import csv
import hashlib
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.model_selection import StratifiedGroupKFold
from sklearn.neighbors import NearestNeighbors

from app.ml.sentiment_text import LABELS, SentimentInputError, normalize_text, prepare_target_context, target_pattern

SOURCES_PATH = Path(__file__).with_name("sources.json")
BANK_ALIASES = {
    "Vietcombank": ["Vietcombank", "VCB"], "VietinBank": ["VietinBank", "CTG"],
    "BIDV": ["BIDV"], "Techcombank": ["Techcombank", "TCB"], "ACB": ["ACB"],
    "VPBank": ["VPBank", "VPB"], "MBBank": ["MBBank", "MB Bank", "MBB"],
    "Sacombank": ["Sacombank", "STB"], "HDBank": ["HDBank", "HDB"],
    "TPBank": ["TPBank", "TPB"], "SHB": ["SHB"], "VIB": ["VIB"], "OCB": ["OCB"],
    "Agribank": ["Agribank"], "MSB": ["MSB", "Maritime Bank"],
    "Eximbank": ["Eximbank", "EIB"], "SeABank": ["SeABank", "SSB"], "SCB": ["SCB"],
    "DongABank": ["DongABank", "DongA Bank", "Đông Á"], "PVcomBank": ["PVcomBank"],
    "LPBank": ["LienVietPostBank", "LPBank", "LPB"], "Vietbank": ["Vietbank"],
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def save_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def canonical_content(text: str) -> str:
    value = normalize_text(text).casefold()
    value = re.sub(r"\d+(?:[.,]\d+)*", " NUMBER ", value)
    return re.sub(r"[\W_]+", " ", value).strip()


def parse_sentfin_decisions(value: str) -> dict[str, str]:
    try:
        parsed = ast.literal_eval(value)
        if not isinstance(parsed, dict):
            raise ValueError("Decisions must be a dictionary")
        return parsed
    except (SyntaxError, ValueError):
        # Upstream contains unescaped apostrophes in entity names, e.g. Osian's.
        pattern = re.compile(r"['\"](?P<entity>.*?)['\"]\s*:\s*['\"](?P<label>positive|negative|neutral)['\"]", re.I)
        matches = list(pattern.finditer(value))
        if not matches or re.sub(r"[{},\s]", "", pattern.sub("", value)):
            raise ValueError("Malformed SEntFiN Decisions; cannot preserve original labels")
        return {match["entity"]: match["label"] for match in matches}


def load_rows(root: Path):
    sources = json.loads(SOURCES_PATH.read_text(encoding="utf-8"))
    rows, dropped = [], []
    raw_counts, fingerprints = Counter(), {}

    def append(source, sample_id, text, entity, label, scope="entity", official=""):
        raw_counts[source["dataset"]] += 1
        item = {"sample_id": sample_id, "dataset": source["dataset"], "language": source["language"],
                "text": normalize_text(text), "entity": normalize_text(entity), "label": str(label).upper(),
                "label_scope": scope, "official_split": official}
        if item["label"] not in LABELS:
            dropped.append({**item, "reason": "unsupported_original_label"})
            return
        try:
            context = prepare_target_context(item["entity"], item["text"])
        except SentimentInputError as error:
            dropped.append({**item, "reason": error.code})
            return
        item["context"] = context.context
        item["input_truncated"] = context.truncated
        item["canonical_text"] = canonical_content(item["text"])
        rows.append(item)

    for source in sources["files"]:
        path = root / source["path"]
        if not path.is_file():
            raise FileNotFoundError(f"Missing {path}; run pipeline download first")
        actual = sha256(path)
        if actual != source["sha256"]:
            raise ValueError(f"Dataset checksum mismatch for {path}: {actual}")
        fingerprints[source["path"]] = actual
        name = source["dataset"]
        if name == "FinEntity":
            for index, item in enumerate(json.loads(path.read_text(encoding="utf-8"))):
                for annotation_index, annotation in enumerate(item["annotations"]):
                    append(source, f"finentity:{index}:{annotation_index}", item["content"], annotation["value"],
                           annotation.get("label", annotation.get("tag")))
        elif name == "SEntFiN":
            with path.open(encoding="utf-8-sig", newline="") as stream:
                for index, item in enumerate(csv.DictReader(stream)):
                    decisions = parse_sentfin_decisions(item["Decisions"])
                    for entity, label in decisions.items():
                        append(source, f"sentfin:{index}:{entity}", item["Title"], entity, label)
        elif name.startswith("ViTASA-"):
            for index, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines()):
                if not line.strip():
                    continue
                item = json.loads(line)
                for annotation_index, annotation in enumerate(item["label"]):
                    start, end, tag = annotation
                    if not isinstance(start, int) or not isinstance(end, int) or not 0 <= start < end <= len(item["data"]):
                        dropped.append({"dataset": name, "sample_id": f"{name}:{index}:{annotation_index}", "reason": "invalid_span"})
                        continue
                    append(source, f"{name}:{item.get('id',index)}:{annotation_index}", item["data"], item["data"][start:end],
                           tag.rsplit("#", 1)[-1], "human_target_aspect_span")
        elif name == "UTS2017Bank-brand":
            for index, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines()):
                if not line.strip():
                    continue
                item = json.loads(line)
                brand_labels = {annotation["sentiment"].upper() for annotation in item["aspects"] if annotation["aspect"] == "TRADEMARK"}
                if len(brand_labels) != 1:
                    continue
                text = normalize_text(item["text"])
                banks = {}
                for canonical, aliases in BANK_ALIASES.items():
                    mentions = [match.group(0) for alias in aliases for match in target_pattern(alias).finditer(text)]
                    if mentions:
                        banks[canonical] = max(mentions, key=len)
                if len(banks) != 1:
                    dropped.append({"dataset": name, "sample_id": f"bank:{source['officialSplit']}:{index}", "reason": "bank_brand_target_ambiguous_or_absent"})
                    continue
                append(source, f"bank:{source['officialSplit']}:{index}", text, next(iter(banks.values())), next(iter(brand_labels)),
                       "TRADEMARK_with_one_explicit_bank", source["officialSplit"])
        else:
            raise ValueError(f"No verified loader for source {name}")

    # Multiple aspect annotations must never become contradictory entity labels.
    by_pair = defaultdict(list)
    for item in rows:
        by_pair[(item["canonical_text"], item["entity"].casefold())].append(item)
    kept = []
    for group in by_pair.values():
        if len({item["label"] for item in group}) > 1:
            dropped.extend({**item, "reason": "conflicting_target_labels"} for item in group)
            continue
        item = group[0]
        if any(row["official_split"] == "test" for row in group):
            item["official_split"] = "test"
        kept.append(item)
        dropped.extend({**other, "reason": "duplicate_text_target"} for other in group[1:])
    return pd.DataFrame(kept), dropped, {"sources": sources, "verifiedFiles": fingerprints, "rawAnnotations": dict(raw_counts)}


def document_groups(frame: pd.DataFrame, similarity: float = 0.96):
    contents = sorted(frame["canonical_text"].unique())
    parents = list(range(len(contents)))

    def find(index):
        while parents[index] != index:
            parents[index] = parents[parents[index]]
            index = parents[index]
        return index

    edges = 0
    if similarity < 1 and len(contents) > 2:
        matrix = TfidfVectorizer(analyzer="char_wb", ngram_range=(4, 5), min_df=2, max_features=40000, dtype=np.float32).fit_transform(contents)
        neighbor = NearestNeighbors(metric="cosine", radius=1-similarity, n_jobs=2).fit(matrix)
        for left in range(0, len(contents), 256):
            distances, indices = neighbor.radius_neighbors(matrix[left:left+256])
            for offset, (scores, matches) in enumerate(zip(distances, indices)):
                current = left + offset
                for distance, other in zip(scores, matches):
                    if other > current and float(distance) <= 1-similarity:
                        a, b = find(current), find(int(other))
                        if a != b:
                            parents[max(a,b)] = min(a,b)
                            edges += 1
    mapping = {content: hashlib.sha256(contents[find(index)].encode("utf-8")).hexdigest() for index, content in enumerate(contents)}
    result = frame.copy()
    result["group_id"] = result["canonical_text"].map(mapping)
    return result, {"canonicalDocuments": len(contents), "nearDuplicateUnionEdges": edges, "similarityThreshold": similarity,
                    "groups": int(result["group_id"].nunique()), "note": "Unlabelled character features are used only for duplicate detection, never as model features."}


def split_frame(frame: pd.DataFrame, seed: int = 42):
    frame = frame.reset_index(drop=True).copy()
    strata = frame["language"] + ":" + frame["label"]
    folds = np.zeros(len(frame), dtype=np.int64)
    for fold, (_, held_out) in enumerate(StratifiedGroupKFold(n_splits=10, shuffle=True, random_state=seed).split(frame, strata, groups=frame["group_id"])):
        folds[held_out] = fold
    frame["split"] = ["test" if fold == 0 else "validation" if fold == 1 else "train" for fold in folds]
    test_groups = set(frame.loc[frame["official_split"] == "test", "group_id"])
    train_groups = set(frame.loc[frame["official_split"] == "train", "group_id"]) - test_groups
    frame.loc[frame["group_id"].isin(test_groups), "split"] = "test"
    # Preserve the publisher's test set: official training rows can train/validate only.
    frame.loc[frame["group_id"].isin(train_groups) & (frame["split"] == "test"), "split"] = "train"
    parts = {name: frame.loc[frame["split"] == name].copy().reset_index(drop=True) for name in ("train", "validation", "test")}
    groups = {name: set(part["group_id"]) for name, part in parts.items()}
    for first, second in (("train", "validation"), ("train", "test"), ("validation", "test")):
        if groups[first] & groups[second]:
            raise RuntimeError("Document/near-duplicate leakage detected")
    for name, part in parts.items():
        for language in ("en", "vi"):
            if set(part.loc[part["language"] == language, "label"]) != set(LABELS):
                raise RuntimeError(f"{name}/{language} lacks one or more sentiment classes")
    return parts


def prepare(root: Path, output: Path, seed: int = 42):
    output.mkdir(parents=True, exist_ok=True)
    frame, dropped, metadata = load_rows(root)
    print(f"Loaded {len(frame)} unambiguous target labels; grouping near duplicates...", flush=True)
    frame, duplicates = document_groups(frame)
    parts = split_frame(frame, seed)
    summaries = {}
    for name, part in parts.items():
        part.to_json(output / f"{name}.jsonl", orient="records", lines=True, force_ascii=False)
        summaries[name] = {"annotations": len(part), "documentGroups": int(part["group_id"].nunique()),
                          "languages": dict(Counter(part["language"])), "labels": dict(Counter(part["label"])),
                          "datasets": dict(Counter(part["dataset"]))}
    pd.DataFrame(dropped).to_json(output / "quarantine.jsonl", orient="records", lines=True, force_ascii=False)
    metadata.update({"seed": seed, "duplicates": duplicates, "splitStrategy": "global_near_duplicate_grouped_80_10_10_with_official_bank_test",
                     "dropped": dict(Counter(item["reason"] for item in dropped)), "splits": summaries, "groupLeakage": False,
                     "splitFiles": {name: sha256(output / f"{name}.jsonl") for name in parts},
                     "limitations": ["Vietnamese ViTASA is target/aspect sentiment in consumer/hospitality text, not supply-chain news.",
                                     "Vietnamese bank labels are TRADEMARK aspects anchored to a single explicit bank; they are not multi-company news gold labels.",
                                     "No synthetic examples, machine translations, QA records or GDELT tone labels are used for training or reported test metrics."]})
    save_json(output / "data-report.json", metadata)
    manifest = {row["sample_id"]: {"split": row["split"], "group": row["group_id"]} for part in parts.values() for row in part.to_dict("records")}
    save_json(output / "split-manifest.json", manifest)
    print(json.dumps({"splits": summaries, "dropped": metadata["dropped"]}, ensure_ascii=False), flush=True)
    return metadata
