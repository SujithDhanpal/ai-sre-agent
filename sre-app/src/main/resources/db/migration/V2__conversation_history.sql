-- Conversation history for chat-style investigations
CREATE TABLE conversation_history (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id UUID         NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    role        VARCHAR(20)  NOT NULL,
    content     TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversation_incident_id ON conversation_history(incident_id);

CREATE TABLE investigation_context (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id     UUID         NOT NULL UNIQUE REFERENCES incidents(id) ON DELETE CASCADE,
    log_context     TEXT,
    memory_context  TEXT,
    services        JSONB        DEFAULT '[]'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
