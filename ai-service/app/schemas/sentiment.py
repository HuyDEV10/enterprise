from typing import Literal
from pydantic import BaseModel, Field, field_validator
from app.ml.sentiment_text import normalize_text


class EntitySentimentRequest(BaseModel):
    entity: str = Field(min_length=1, max_length=300)
    text: str = Field(min_length=1, max_length=20000)
    language: Literal["auto", "en", "vi"] = "auto"
    domain: Literal["general", "supply_chain_news"] = "general"

    @field_validator("entity", "text")
    @classmethod
    def nonblank(cls, value):
        if not value.strip():
            raise ValueError("Must contain non-whitespace text")
        return value


class EntitySentimentResponse(BaseModel):
    entity: str
    sentiment: Literal["NEGATIVE", "NEUTRAL", "POSITIVE"]
    confidence: float = Field(ge=0.0, le=1.0)
    modelVersion: str
    probabilities: dict[str, float] = Field(default_factory=dict)
    language: Literal["en", "vi"] = "en"
    needsReview: bool = True
    reviewReason: str | None = None
    inputTruncated: bool = False


class EntitySentimentBatchRequest(BaseModel):
    entities: list[str] = Field(min_length=1, max_length=16)
    text: str = Field(min_length=1, max_length=20000)
    language: Literal["auto", "en", "vi"] = "auto"
    domain: Literal["general", "supply_chain_news"] = "general"

    @field_validator("text")
    @classmethod
    def nonblank_text(cls, value):
        if not value.strip():
            raise ValueError("Must contain non-whitespace text")
        return value

    @field_validator("entities")
    @classmethod
    def valid_entities(cls, values):
        if any(not value.strip() or len(value) > 300 for value in values):
            raise ValueError("Each entity must be nonblank and at most 300 characters")
        if len({normalize_text(value).casefold() for value in values}) != len(values):
            raise ValueError("Entities must be unique")
        return values


class EntitySentimentBatchResponse(BaseModel):
    results: list[EntitySentimentResponse]
