from __future__ import annotations

import argparse
import urllib.request
from pathlib import Path

from training.sentiment.finentity_dataset import FINENTITY_RAW_URL, load_finentity


def main() -> None:
    parser = argparse.ArgumentParser(description="Download the real FinEntity dataset from its pinned official GitHub commit")
    parser.add_argument("--output", type=Path, default=Path("data/raw/finentity/FinEntity.json"))
    args = parser.parse_args()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(FINENTITY_RAW_URL, headers={"User-Agent": "enterprise-risk-stage6/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        args.output.write_bytes(response.read())

    _, metadata = load_finentity(args.output)
    print(f"Downloaded real FinEntity dataset to {args.output}")
    print(f"SHA256: {metadata['sha256']}")
    print(f"Contents: {metadata['contentCount']}; annotations: {metadata['annotationCount']}")
    print(f"Labels: {metadata['labelCounts']}")


if __name__ == "__main__":
    main()
