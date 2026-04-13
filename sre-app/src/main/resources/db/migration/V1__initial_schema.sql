-- ============================================================
-- AI SRE Platform — Initial Schema
-- PostgreSQL 16 + pgvector
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- TENANTS
-- ============================================================
CREATE TABLE tenants (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(255) NOT NULL UNIQUE,
    plan        VARCHAR(50)  NOT NULL DEFAULT 'FREE',
    autonomy_config JSONB,
    plugin_configs  JSONB,
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- INCIDENTS
-- ============================================================
CREATE TABLE incidents (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           VARCHAR(255) NOT NULL,
    correlation_id      VARCHAR(512) NOT NULL UNIQUE,
    alert_source        VARCHAR(50)  NOT NULL,
    external_alert_id   VARCHAR(512),
    severity            VARCHAR(10)  NOT NULL,
    status              VARCHAR(50)  NOT NULL DEFAULT 'NEW',
    title               TEXT         NOT NULL,
    description         TEXT,
    raw_alert_payload   JSONB,
    affected_services   JSONB        DEFAULT '[]'::jsonb,
    environment         VARCHAR(100),
    source_plugin       VARCHAR(100),
    assigned_to         VARCHAR(255),
    resolved_at         TIMESTAMPTZ,
    resolved_by         VARCHAR(255),
    resolution_summary  TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_incidents_tenant_id ON incidents(tenant_id);
CREATE INDEX idx_incidents_status ON incidents(status);
CREATE INDEX idx_incidents_severity ON incidents(severity);
CREATE INDEX idx_incidents_created_at ON incidents(created_at DESC);

-- ============================================================
-- INCIDENT TIMELINE
-- ============================================================
CREATE TABLE incident_timeline (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id UUID         NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    event_type  VARCHAR(100) NOT NULL,
    actor       VARCHAR(255) NOT NULL,
    summary     TEXT,
    metadata    JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_timeline_incident_id ON incident_timeline(incident_id);

-- ============================================================
-- INCIDENT ANALYSES
-- ============================================================
CREATE TABLE incident_analyses (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id         UUID         NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    analysis_type       VARCHAR(50)  NOT NULL,
    summary             TEXT,
    root_cause          TEXT,
    confidence          DOUBLE PRECISION,
    hypotheses          JSONB,
    evidence_sources    JSONB,
    llm_model           VARCHAR(100),
    tokens_used         INTEGER,
    analysis_duration_ms BIGINT,
    agent_id            VARCHAR(100),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_analyses_incident_id ON incident_analyses(incident_id);

-- ============================================================
-- FIX PROPOSALS
-- ============================================================
CREATE TABLE fix_proposals (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id             UUID         NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    analysis_id             UUID,
    fix_type                VARCHAR(50)  NOT NULL,
    status                  VARCHAR(50)  NOT NULL DEFAULT 'PROPOSED',
    description             TEXT,
    diff_patch              TEXT,
    target_repo             VARCHAR(512),
    target_branch           VARCHAR(255),
    confidence_score        DOUBLE PRECISION,
    validation_verdict      VARCHAR(20),
    compilation_passed      BOOLEAN,
    tests_passed            BOOLEAN,
    tests_total             INTEGER,
    tests_failed            INTEGER,
    static_analysis_passed  BOOLEAN,
    validation_details      JSONB,
    sandbox_execution_time_ms BIGINT,
    pull_request_url        VARCHAR(1024),
    approved_by             VARCHAR(255),
    rollback_plan           JSONB,
    code_patches            JSONB,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_fix_proposals_incident_id ON fix_proposals(incident_id);

-- ============================================================
-- AGENT DEFINITIONS
-- ============================================================
CREATE TABLE agent_definitions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           VARCHAR(255),
    agent_id            VARCHAR(255) NOT NULL,
    display_name        VARCHAR(255) NOT NULL,
    description         TEXT,
    source              VARCHAR(50)  NOT NULL,
    system_prompt       TEXT         NOT NULL,
    assigned_skills     JSONB,
    assigned_plugins    JSONB,
    llm_model           VARCHAR(100),
    max_tool_iterations INTEGER      DEFAULT 15,
    max_token_budget    INTEGER      DEFAULT 50000,
    is_reviewer         BOOLEAN      DEFAULT false,
    active              BOOLEAN      DEFAULT true,
    version             VARCHAR(50)  DEFAULT '1.0.0',
    created_by          VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_def_tenant_id ON agent_definitions(tenant_id);
CREATE INDEX idx_agent_def_agent_id ON agent_definitions(agent_id);

-- ============================================================
-- SKILL DEFINITIONS
-- ============================================================
CREATE TABLE skill_definitions (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id         VARCHAR(255),
    skill_id          VARCHAR(255) NOT NULL,
    display_name      VARCHAR(255) NOT NULL,
    description       TEXT,
    source            VARCHAR(50)  NOT NULL,
    skill_type        VARCHAR(50)  NOT NULL,
    markdown_content  TEXT,
    executor_class    VARCHAR(512),
    steps             JSONB,
    input_schema      TEXT,
    output_schema     TEXT,
    required_plugins  JSONB,
    git_repo_url      VARCHAR(1024),
    git_file_path     VARCHAR(1024),
    git_commit_sha    VARCHAR(100),
    active            BOOLEAN      DEFAULT true,
    version           VARCHAR(50)  DEFAULT '1.0.0',
    created_by        VARCHAR(255),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_skill_def_tenant_id ON skill_definitions(tenant_id);
CREATE INDEX idx_skill_def_skill_id ON skill_definitions(skill_id);

-- ============================================================
-- AGENT MESSAGES
-- ============================================================
CREATE TABLE agent_messages (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id     UUID         NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    from_agent      VARCHAR(255) NOT NULL,
    to_agent        VARCHAR(255),
    message_type    VARCHAR(50)  NOT NULL,
    content         TEXT         NOT NULL,
    confidence      DOUBLE PRECISION,
    evidence        JSONB,
    structured_data JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_msg_incident_id ON agent_messages(incident_id);
CREATE INDEX idx_agent_msg_from_agent ON agent_messages(from_agent);

-- ============================================================
-- EPISODIC MEMORY (past incidents with outcomes)
-- ============================================================
CREATE TABLE episodic_memory (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               VARCHAR(255) NOT NULL,
    incident_id             UUID,
    incident_summary        TEXT,
    diagnosis_summary       TEXT,
    resolution_summary      TEXT,
    diagnosis_was_correct   BOOLEAN,
    fix_worked              BOOLEAN,
    confidence_at_diagnosis DOUBLE PRECISION,
    human_correction        TEXT,
    error_patterns          JSONB,
    root_cause_categories   JSONB,
    affected_services       JSONB,
    embedding               vector(1536),
    resolved_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_episodic_tenant_id ON episodic_memory(tenant_id);
CREATE INDEX idx_episodic_incident_id ON episodic_memory(incident_id);

-- ============================================================
-- SEMANTIC MEMORY (system knowledge)
-- ============================================================
CREATE TABLE semantic_memory (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id     VARCHAR(255) NOT NULL,
    namespace     VARCHAR(255) NOT NULL,
    key           VARCHAR(512) NOT NULL,
    value         TEXT         NOT NULL,
    source        VARCHAR(50)  NOT NULL,
    confidence    DOUBLE PRECISION DEFAULT 1.0,
    last_verified TIMESTAMPTZ,
    created_by    VARCHAR(255),
    embedding     vector(1536),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_semantic_tenant_id ON semantic_memory(tenant_id);
CREATE INDEX idx_semantic_namespace_key ON semantic_memory(namespace, key);

-- ============================================================
-- PROCEDURAL MEMORY (correction rules, playbooks)
-- ============================================================
CREATE TABLE procedural_memory (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           VARCHAR(255) NOT NULL,
    type                VARCHAR(50)  NOT NULL,
    trigger_pattern     TEXT         NOT NULL,
    instruction         TEXT         NOT NULL,
    reasoning           TEXT,
    source              VARCHAR(50)  NOT NULL,
    source_incident_id  UUID,
    effectiveness_score DOUBLE PRECISION DEFAULT 0.5,
    times_applied       INTEGER      DEFAULT 0,
    times_effective     INTEGER      DEFAULT 0,
    active              BOOLEAN      DEFAULT true,
    created_by          VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_procedural_tenant_id ON procedural_memory(tenant_id);
CREATE INDEX idx_procedural_type ON procedural_memory(type);

-- ============================================================
-- HNSW INDEXES for vector similarity search
-- ============================================================
CREATE INDEX idx_episodic_embedding ON episodic_memory USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_semantic_embedding ON semantic_memory USING hnsw (embedding vector_cosine_ops);
