from __future__ import annotations

import hashlib
from pathlib import Path
from typing import Any

import pandas as pd

M5_SOURCE = "https://doi.org/10.5281/zenodo.10203108"
EXPECTED_FILES = {
    "calendar.csv": "3ffeab2991b0c8e861d008b39ea4c95c",
    "sales_train_evaluation.csv": "b806dfc9f30a745102b708c09951f6aa",
    "sell_prices.csv": "08c591caa99e55daf3e0ccac913f7c85",
}

SALES_METADATA_COLUMNS = ["id", "item_id", "dept_id", "cat_id", "store_id", "state_id"]
CALENDAR_REQUIRED_COLUMNS = {"date", "wm_yr_wk", "d"}
PRICE_REQUIRED_COLUMNS = {"store_id", "item_id", "wm_yr_wk", "sell_price"}
FIRST_DAY = 1
LAST_DAY = 1941


def md5_file(path: Path, chunk_size: int = 1024 * 1024) -> str:
    digest = hashlib.md5()  # noqa: S324 - checksum is used only to match the published dataset artifact.
    with path.open("rb") as handle:
        while chunk := handle.read(chunk_size):
            digest.update(chunk)
    return digest.hexdigest()


def validate_m5_dataset(data_dir: Path, verify_md5: bool = False) -> dict[str, Any]:
    data_dir = Path(data_dir)
    missing_files = [name for name in EXPECTED_FILES if not (data_dir / name).is_file()]
    if missing_files:
        raise FileNotFoundError("Missing required M5 files: " + ", ".join(missing_files))

    calendar_path = data_dir / "calendar.csv"
    sales_path = data_dir / "sales_train_evaluation.csv"
    prices_path = data_dir / "sell_prices.csv"

    calendar_header = set(pd.read_csv(calendar_path, nrows=0).columns)
    sales_columns = list(pd.read_csv(sales_path, nrows=0).columns)
    price_header = set(pd.read_csv(prices_path, nrows=0).columns)

    if not CALENDAR_REQUIRED_COLUMNS.issubset(calendar_header):
        raise ValueError("calendar.csv does not match the M5 calendar schema")
    if not PRICE_REQUIRED_COLUMNS.issubset(price_header):
        raise ValueError("sell_prices.csv does not match the M5 price schema")
    if not set(SALES_METADATA_COLUMNS).issubset(sales_columns):
        raise ValueError("sales_train_evaluation.csv is missing M5 metadata columns")

    expected_day_columns = [f"d_{day}" for day in range(FIRST_DAY, LAST_DAY + 1)]
    available_day_columns = [column for column in sales_columns if column.startswith("d_")]
    if available_day_columns != expected_day_columns:
        raise ValueError("sales_train_evaluation.csv must contain the ordered range d_1..d_1941")

    calendar_days = set(pd.read_csv(calendar_path, usecols=["d"])["d"].astype(str))
    if not set(expected_day_columns).issubset(calendar_days):
        raise ValueError("calendar.csv does not cover all evaluation days d_1..d_1941")

    checksums: dict[str, str] = {}
    if verify_md5:
        for filename, expected_md5 in EXPECTED_FILES.items():
            actual_md5 = md5_file(data_dir / filename)
            if actual_md5 != expected_md5:
                raise ValueError(f"Checksum mismatch for {filename}: expected {expected_md5}, got {actual_md5}")
            checksums[filename] = actual_md5

    return {
        "source": M5_SOURCE,
        "files": list(EXPECTED_FILES),
        "dayRange": [FIRST_DAY, LAST_DAY],
        "integrityVerified": verify_md5,
        "checksums": checksums,
    }


def load_m5_subset(
    data_dir: Path,
    store_id: str = "CA_1",
    dept_id: str = "FOODS_3",
    max_series: int | None = None,
) -> pd.DataFrame:
    if max_series is not None and max_series < 1:
        raise ValueError("max_series must be positive when supplied")

    sales_path = Path(data_dir) / "sales_train_evaluation.csv"
    matching_chunks: list[pd.DataFrame] = []

    for chunk in pd.read_csv(sales_path, chunksize=256):
        filtered = chunk[(chunk["store_id"] == store_id) & (chunk["dept_id"] == dept_id)]
        if not filtered.empty:
            matching_chunks.append(filtered)

    if not matching_chunks:
        raise ValueError(f"No M5 series found for store_id={store_id}, dept_id={dept_id}")

    subset = pd.concat(matching_chunks, ignore_index=True)
    subset = subset.sort_values(["item_id", "id"]).reset_index(drop=True)
    if max_series is not None:
        subset = subset.head(max_series).copy()

    day_columns = [f"d_{day}" for day in range(FIRST_DAY, LAST_DAY + 1)]
    long_frame = subset.melt(
        id_vars=SALES_METADATA_COLUMNS,
        value_vars=day_columns,
        var_name="source_day_key",
        value_name="quantity",
    )
    long_frame = long_frame.rename(columns={"id": "series_id"})
    long_frame["day_number"] = long_frame["source_day_key"].str[2:].astype("int16")
    long_frame["quantity"] = pd.to_numeric(long_frame["quantity"], errors="raise").astype("float32")

    calendar = pd.read_csv(Path(data_dir) / "calendar.csv", usecols=["d", "date"])
    calendar = calendar.rename(columns={"d": "source_day_key"})
    calendar["date"] = pd.to_datetime(calendar["date"], errors="raise")
    long_frame = long_frame.merge(calendar, on="source_day_key", how="left", validate="many_to_one")
    if long_frame["date"].isna().any():
        raise ValueError("M5 calendar merge produced missing dates")

    return long_frame.sort_values(["series_id", "day_number"]).reset_index(drop=True)
