"""Reproducible sentiment lifecycle: download -> prepare -> train -> evaluate -> promote."""
from __future__ import annotations

import argparse
import json
import shutil
import ssl
import sys
import time
import urllib.request
from pathlib import Path

from training.sentiment.data import SOURCES_PATH, prepare, save_json, sha256

ROOT = Path(__file__).resolve().parents[2]


def tls_context():
    # Explicit context keeps certificate verification and the Windows trust store;
    # it does not attempt to write an inherited, potentially inaccessible TLS debug log.
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    context.load_default_certs()
    return context


def download_file(url: str, target: Path, expected_hash: str, expected_size: int | None = None):
    if target.is_file() and sha256(target) == expected_hash:
        print(f"Verified {target.name}", flush=True)
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_suffix(target.suffix + ".part")
    if target.is_file() and not partial.exists():
        target.replace(partial)
    context = tls_context()
    for attempt in range(5):
        size = partial.stat().st_size if partial.exists() else 0
        headers = {"User-Agent": "enterprise-risk-sentiment/2.0"}
        if size:
            headers["Range"] = f"bytes={size}-"
        request = urllib.request.Request(url, headers=headers)
        try:
            with urllib.request.urlopen(request, context=context, timeout=60) as response:
                append = size > 0 and response.status == 206
                with partial.open("ab" if append else "wb") as stream:
                    while chunk := response.read(1024 * 1024):
                        stream.write(chunk)
            if expected_size is not None and partial.stat().st_size != expected_size:
                raise OSError("Incomplete download")
            if sha256(partial) != expected_hash:
                partial.unlink()
                raise ValueError(f"SHA256 mismatch for {target.name}")
            partial.replace(target)
            print(f"Downloaded and verified {target.name}", flush=True)
            return
        except (OSError, ValueError) as error:
            if attempt == 4:
                raise
            print(f"Download retry {attempt+1}/4 for {target.name}: {type(error).__name__}", flush=True)
            time.sleep(2)


def download(root: Path, include_model: bool = True):
    sources = json.loads(SOURCES_PATH.read_text(encoding="utf-8"))
    for source in sources["files"]:
        download_file(source["url"], root/source["path"], source["sha256"])
    if include_model:
        model = sources["pretrainedModel"]
        api = f"https://huggingface.co/api/models/{model['repository']}/tree/{model['revision']}?recursive=true"
        request = urllib.request.Request(api, headers={"User-Agent": "enterprise-risk-sentiment/2.0"})
        with urllib.request.urlopen(request, context=tls_context(), timeout=60) as response:
            files = {item["path"]: item for item in json.load(response)}
        manifest = {}
        for filename in model["files"]:
            entry = files[filename]
            expected = entry.get("lfs", {}).get("oid")
            url = f"https://huggingface.co/{model['repository']}/resolve/{model['revision']}/{filename}"
            if expected is None:
                with urllib.request.urlopen(urllib.request.Request(url), context=tls_context(), timeout=60) as response:
                    content = response.read()
                import hashlib
                expected = hashlib.sha256(content).hexdigest()
            download_file(url, root/model["localPath"]/filename, expected, entry["size"])
            manifest[filename] = expected
        save_json(root/model["localPath"]/"source-manifest.json", {**model, "sha256": manifest})


def promote(root: Path, run: Path):
    from training.sentiment.lifecycle import promote_run
    promote_run(root, run)


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["download", "prepare", "train", "evaluate", "promote"])
    parser.add_argument("--prepared", type=Path, default=Path("data/processed/sentiment-v2"))
    parser.add_argument("--run-dir", type=Path)
    parser.add_argument("--epochs", type=int, default=5)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--max-length", type=int, default=128)
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--datasets-only", action="store_true")
    args = parser.parse_args()
    if args.command == "download":
        download(ROOT, not args.datasets_only)
    elif args.command == "prepare":
        prepare(ROOT, ROOT/args.prepared)
    elif args.command == "train":
        from training.sentiment.trainer import train
        train(ROOT, args)
    elif args.command == "evaluate":
        from training.sentiment.lifecycle import evaluate_run
        if args.run_dir is None:
            parser.error("--run-dir is required for evaluate")
        evaluate_run(ROOT, ROOT/args.run_dir)
    elif args.command == "promote":
        if args.run_dir is None:
            parser.error("--run-dir is required for promote")
        promote(ROOT, ROOT/args.run_dir)


if __name__ == "__main__":
    main()
