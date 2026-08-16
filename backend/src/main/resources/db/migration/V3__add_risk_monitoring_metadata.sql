ALTER TABLE risk_events
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN rule_code VARCHAR(50),
    ADD COLUMN auto_generated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN source_ref VARCHAR(100);

CREATE INDEX idx_risk_events_source ON risk_events(source);
CREATE INDEX idx_risk_events_rule_code ON risk_events(rule_code);
CREATE INDEX idx_risk_events_auto_generated ON risk_events(auto_generated);
CREATE INDEX idx_risk_events_source_ref ON risk_events(source_ref);

CREATE UNIQUE INDEX uk_active_automatic_risk_rule_source
    ON risk_events(rule_code, source_ref)
    WHERE auto_generated = TRUE
      AND status IN ('OPEN', 'INVESTIGATING');
