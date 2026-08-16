from __future__ import annotations

from datetime import date

from pydantic import BaseModel, Field, field_validator


class DemandHistoryPoint(BaseModel):
    date: date
    quantity: float = Field(ge=0)


class DemandForecastRequest(BaseModel):
    history: list[DemandHistoryPoint]

    @field_validator("history")
    @classmethod
    def history_must_not_be_empty(cls, value: list[DemandHistoryPoint]) -> list[DemandHistoryPoint]:
        if not value:
            raise ValueError("history must not be empty")
        return value


class DemandForecastPoint(BaseModel):
    date: date
    quantity: float


class DemandForecastSummary(BaseModel):
    next7Days: float
    next14Days: float
    next28Days: float


class DemandForecastResponse(BaseModel):
    historyDays: int
    modelVersion: str
    forecast: list[DemandForecastPoint]
    summary: DemandForecastSummary
