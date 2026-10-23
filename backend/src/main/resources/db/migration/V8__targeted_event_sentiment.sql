CREATE TABLE external_event_sentiments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_event_id UUID NOT NULL REFERENCES external_events(id) ON DELETE CASCADE,
    supplier_id UUID REFERENCES suppliers(id),
    target_entity VARCHAR(300) NOT NULL,
    target_key VARCHAR(300) NOT NULL,
    sentiment VARCHAR(30) NOT NULL,
    confidence NUMERIC(8,6) NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    probabilities JSONB NOT NULL DEFAULT '{}'::jsonb,
    language VARCHAR(10) NOT NULL,
    needs_review BOOLEAN NOT NULL DEFAULT TRUE,
    review_reason VARCHAR(100),
    model_version VARCHAR(100) NOT NULL,
    input_truncated BOOLEAN NOT NULL DEFAULT FALSE,
    source_text TEXT NOT NULL,
    source_text_sha256 VARCHAR(64) NOT NULL,
    match_method VARCHAR(50) NOT NULL,
    analyzed_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_event_sentiment_target UNIQUE (external_event_id, target_key),
    CONSTRAINT ck_target_sentiment_label CHECK (sentiment IN ('NEGATIVE','NEUTRAL','POSITIVE'))
);
CREATE INDEX idx_target_sentiment_event_time ON external_event_sentiments(external_event_id, analyzed_at DESC);
CREATE INDEX idx_target_sentiment_supplier ON external_event_sentiments(supplier_id);
