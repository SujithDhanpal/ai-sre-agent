-- Maps skills as tools to agents
CREATE TABLE agent_skill_tools (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    agent_id    VARCHAR(255) NOT NULL,
    skill_id    VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE(agent_id, skill_id)
);

CREATE INDEX idx_agent_skill_tools_agent ON agent_skill_tools(agent_id);
