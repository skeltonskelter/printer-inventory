CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT,
    username VARCHAR(100) NOT NULL,
    user_role VARCHAR(20) NOT NULL,
    action VARCHAR(40) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id VARCHAR(100),
    entity_identifier VARCHAR(250),
    description VARCHAR(1000) NOT NULL,
    old_values TEXT,
    new_values TEXT
);

CREATE INDEX idx_audit_logs_occurred_at ON audit_logs (occurred_at DESC, id DESC);
CREATE INDEX idx_audit_logs_username ON audit_logs (LOWER(username));
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_entity_type ON audit_logs (entity_type);
