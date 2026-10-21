from __future__ import annotations

from pathlib import Path

import pandas as pd

from app.schemas.m5_dataset import M5DemandPoint, M5DemandSeries
from training.forecasting.m5_dataset import validate_m5_dataset


class M5DatasetService:
    def __init__(self, data_dir: Path = Path("data/raw/m5")) -> None:
        self.data_dir = Path(data_dir)

    def load_series(self, item_id: str, store_id: str = "CA_1") -> M5DemandSeries:
        validation = validate_m5_dataset(self.data_dir, verify_md5=False)
        if validation["dayRange"] != [1, 1941]:
            raise ValueError("M5 evaluation range is not d_1..d_1941")

        sales_path = self.data_dir / "sales_train_evaluation.csv"
        calendar_path = self.data_dir / "calendar.csv"
        prices_path = self.data_dir / "sell_prices.csv"

        matching = None
        for chunk in pd.read_csv(sales_path, chunksize=256):
            rows = chunk[(chunk["item_id"] == item_id) & (chunk["store_id"] == store_id)]
            if not rows.empty:
                matching = rows.iloc[0].copy()
                break

        if matching is None:
            raise LookupError(f"M5 series not found for item_id={item_id}, store_id={store_id}")

        day_columns = [f"d_{day}" for day in range(1, 1942)]
        history = pd.DataFrame(
            {
                "sourceDayKey": day_columns,
                "quantity": [float(matching[column]) for column in day_columns],
            }
        )

        calendar = pd.read_csv(calendar_path, usecols=["d", "date", "wm_yr_wk"])
        calendar = calendar.rename(columns={"d": "sourceDayKey"})
        calendar["date"] = pd.to_datetime(calendar["date"], errors="raise")
        history = history.merge(calendar, on="sourceDayKey", how="left", validate="one_to_one")

        prices = pd.read_csv(
            prices_path,
            usecols=["store_id", "item_id", "wm_yr_wk", "sell_price"],
        )
        prices = prices[(prices["store_id"] == store_id) & (prices["item_id"] == item_id)]
        history = history.merge(
            prices[["wm_yr_wk", "sell_price"]],
            on="wm_yr_wk",
            how="left",
            validate="many_to_one",
        )

        points = [
            M5DemandPoint(
                date=row.date.date(),
                quantity=float(row.quantity),
                sellPrice=None if pd.isna(row.sell_price) else float(row.sell_price),
                sourceDayKey=str(row.sourceDayKey),
            )
            for row in history.itertuples(index=False)
        ]

        return M5DemandSeries(
            seriesId=str(matching["id"]),
            itemId=str(matching["item_id"]),
            storeId=str(matching["store_id"]),
            departmentId=str(matching["dept_id"]),
            categoryId=str(matching["cat_id"]),
            stateId=str(matching["state_id"]),
            history=points,
        )


m5_dataset_service = M5DatasetService()
