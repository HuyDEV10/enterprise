from pydantic import BaseModel, Field


class EntitySentimentRequest(BaseModel):
    entity: str = Field(min_length=1, max_length=300)
    text: str = Field(min_length=1, max_length=20000)


class EntitySentimentResponse(BaseModel):
    entity: str
    sentiment: str
    confidence: float = Field(ge=0.0, le=1.0)
    modelVersion: str
