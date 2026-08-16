from __future__ import annotations

import argparse
import json
from pathlib import Path

from training.forecasting.m5_dataset import load_m5_subset, validate_m5_dataset


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate real M5 files and inspect the Stage 6B subset")
    parser.add_argument("--data-dir", type=Path, default=Path("data/raw/m5"))
    parser.add_argument("--store-id", default="CA_1")
    parser.add_argument("--dept-id", default="FOODS_3")
    parser.add_argument("--max-series", type=int, default=None)
    parser.add_argument("--verify-md5", action="store_true")
    args = parser.parse_args()

    validation = validate_m5_dataset(args.data_dir, verify_md5=args.verify_md5)
    subset = load_m5_subset(args.data_dir, args.store_id, args.dept_id, args.max_series)
    summary = {
        **validation,
        "subset": {
            "storeId": args.store_id,
            "departmentId": args.dept_id,
            "seriesCount": int(subset["series_id"].nunique()),
            "observationCount": int(len(subset)),
            "dateStart": subset["date"].min().date().isoformat(),
            "dateEnd": subset["date"].max().date().isoformat(),
        },
    }
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
