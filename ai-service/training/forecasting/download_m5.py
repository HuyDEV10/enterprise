from __future__ import annotations

import argparse
import urllib.request
from pathlib import Path

from training.forecasting.m5_dataset import EXPECTED_FILES, md5_file

ZENODO_BASE_URL = "https://zenodo.org/records/10203108/files"


def download_file(filename: str, destination_dir: Path) -> None:
    destination_dir.mkdir(parents=True, exist_ok=True)
    destination = destination_dir / filename
    expected_md5 = EXPECTED_FILES[filename]

    if destination.is_file() and md5_file(destination) == expected_md5:
        print(f"OK {filename}: already present and checksum matches")
        return

    temporary = destination.with_suffix(destination.suffix + ".part")
    url = f"{ZENODO_BASE_URL}/{filename}?download=1"
    print(f"Downloading {filename} from Zenodo...")

    request = urllib.request.Request(url, headers={"User-Agent": "enterprise-risk-stage6/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response, temporary.open("wb") as output:
        while chunk := response.read(1024 * 1024):
            output.write(chunk)

    actual_md5 = md5_file(temporary)
    if actual_md5 != expected_md5:
        temporary.unlink(missing_ok=True)
        raise RuntimeError(
            f"Checksum mismatch for {filename}: expected {expected_md5}, got {actual_md5}"
        )
    temporary.replace(destination)
    print(f"OK {filename}: checksum {actual_md5}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Download the real M5 files used by Stage 6B")
    parser.add_argument("--data-dir", type=Path, default=Path("data/raw/m5"))
    args = parser.parse_args()

    for filename in EXPECTED_FILES:
        download_file(filename, args.data_dir)


if __name__ == "__main__":
    main()
