from __future__ import annotations

from datetime import date

from pydantic import BaseModel


class M5DemandPoint(BaseModel):
    date: date
    quantity: float
    sellPrice: float | None = None
    sourceDayKey: str


class M5DemandSeries(BaseModel):
    seriesId: str
    itemId: str
    storeId: str
    departmentId: str
    categoryId: str
    stateId: str
    history: list[M5DemandPoint]


class M5SubsetResponse(BaseModel):
    source: str
    integrityVerified: bool
    storeId: str
    departmentId: str
    seriesCount: int
    series: list[M5DemandSeries]
