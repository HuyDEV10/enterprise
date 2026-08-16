CREATE TABLE external_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_source VARCHAR(50) NOT NULL,
    external_event_id VARCHAR(100) NOT NULL,
    event_date DATE NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_code VARCHAR(20),
    event_base_code VARCHAR(20),
    event_root_code VARCHAR(20),
    actor1_name VARCHAR(255),
    actor2_name VARCHAR(255),
    country_code VARCHAR(10),
    country VARCHAR(100),
    location VARCHAR(255),
    latitude NUMERIC(10, 6),
    longitude NUMERIC(10, 6),
    goldstein_score NUMERIC(7, 3),
    avg_tone NUMERIC(9, 4),
    num_mentions INTEGER,
    num_sources INTEGER,
    num_articles INTEGER,
    source_url TEXT,
    sentiment VARCHAR(30),
    sentiment_confidence NUMERIC(5, 4),
    sentiment_model_version VARCHAR(100),
    signal VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_external_event_source_id UNIQUE (external_source, external_event_id),
    CONSTRAINT ck_external_event_latitude CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
    CONSTRAINT ck_external_event_longitude CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180)),
    CONSTRAINT ck_external_event_sentiment_confidence CHECK (
        sentiment_confidence IS NULL OR (sentiment_confidence >= 0 AND sentiment_confidence <= 1)
    )
);

CREATE TABLE external_event_impacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_event_id UUID NOT NULL REFERENCES external_events(id) ON DELETE CASCADE,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    impact_score NUMERIC(5, 2) NOT NULL,
    impact_level VARCHAR(30) NOT NULL,
    impact_type VARCHAR(30) NOT NULL,
    explanation TEXT NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_external_event_impact_score CHECK (impact_score >= 0 AND impact_score <= 100),
    CONSTRAINT uk_external_event_supplier_impact UNIQUE (external_event_id, supplier_id)
);

CREATE INDEX idx_external_events_event_date ON external_events(event_date);
CREATE INDEX idx_external_events_country_code ON external_events(country_code);
CREATE INDEX idx_external_events_event_type ON external_events(event_type);
CREATE INDEX idx_external_events_signal ON external_events(signal);
CREATE INDEX idx_external_event_impacts_event_id ON external_event_impacts(external_event_id);
CREATE INDEX idx_external_event_impacts_supplier_id ON external_event_impacts(supplier_id);
CREATE INDEX idx_external_event_impacts_level ON external_event_impacts(impact_level);
