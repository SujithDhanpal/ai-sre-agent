# AI SRE Agent — Architecture & Codebase Guide

**14 modules | 110 Java files | Spring Boot 3.3 + Java 21 + Spring AI**

An AI-powered SRE agent that investigates production incidents by analyzing logs, reading code, querying databases, and learning from past resolutions.

---

## How It Works — The Complete Flow

```
┌──────────────────────────────────────────────────────────────────────────┐
│                                                                          │
│  POST /api/v1/investigate                                                │
│  { "query": "What happened with request abc-123?", "services": ["esd"] } │
│                                                                          │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                     CONTEXT GATHERING (parallel, ~5s)                     │
│                                                                          │
│  ┌─────────────┐  ┌──────────────────┐  ┌─────────────┐  ┌───────────┐ │
│  │ Pre-fetch    │  │ Change           │  │ Blast       │  │ Memory    │ │
│  │ Logs         │  │ Correlation      │  │ Radius      │  │ Lookup    │ │
│  │              │  │                  │  │             │  │           │ │
│  │ Loki query   │  │ GitHub: recent   │  │ Error rate  │  │ Episodic  │ │
│  │ for request  │  │ deploys, commits │  │ Affected    │  │ Semantic  │ │
│  │ ID or recent │  │                  │  │ users       │  │ Procedural│ │
│  │ errors       │  │                  │  │ DB pool     │  │ rules     │ │
│  └──────┬───────┘  └────────┬─────────┘  └──────┬──────┘  └─────┬─────┘ │
│         └──────────────┬────┴──────────────┬─────┘               │       │
│                        ▼                   ▼                     ▼       │
│                   Combined into fullContext + memoryContext               │
│                   Persisted to investigation_context table                │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                     TRIAGE AGENT (single ReAct agent, ~15-30s)           │
│                                                                          │
│  Claude receives:                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ System: "You are an expert SRE agent..."                            │ │
│  │         + pre-fetched logs (REAL DATA)                              │ │
│  │         + change correlation (recent deploys)                       │ │
│  │         + blast radius (error rate, impact)                         │ │
│  │         + memory (past incidents, correction rules)                 │ │
│  │         + skill descriptions (if skills registered as tools)        │ │
│  │                                                                     │ │
│  │ Tools available (22 plugin tools + skill tools):                    │ │
│  │   LogAnalysisTools:  discoverLogLabels, getLabelValues, queryLogs,  │ │
│  │                      queryMetrics, getRecentErrors, getErrorDetail  │ │
│  │   InfraTools:        checkServiceHealth, checkLB, checkQueue,       │ │
│  │                      checkConnectionPool, getActiveQueries,         │ │
│  │                      checkDatabaseLocks, getSlowQueries,            │ │
│  │                      checkReplicationLag, executeDiagnosticQuery,    │ │
│  │                      checkAWSHealth                                 │ │
│  │   CodeAnalysisTools: readSourceFile, searchCode, getRecentCommits,  │ │
│  │                      getDiff, getRecentDeployments,                 │ │
│  │                      checkReleaseHealth                             │ │
│  │   SkillTools:        staging-db, check-db-health, etc.             │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  Claude autonomously decides which tools to call (ReAct loop):           │
│    "I see an error in the logs. Let me read the source code..."          │
│    → readSourceFile("esd", "CannedResponseRepository.java")             │
│    → "Found the issue. Let me check the DB..."                           │
│    → execute("database=esd;sql_query=SELECT COUNT(*) FROM users")        │
│    → Returns diagnosis                                                   │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                     SPECIALIST ASSESSMENT (~2s)                           │
│                                                                          │
│  Quick Claude call: "Do you need specialist agents?"                     │
│                                                                          │
│  Available specialists:                                                  │
│    Platform:  log-analyst, infra-agent, code-analyst, fix-generator      │
│    Custom:    kafka-expert, redis-expert (user-registered)               │
│                                                                          │
│  ┌─────────────────┐           ┌───────────────────────────────────────┐ │
│  │ "NONE"          │           │ "log-analyst, infra-agent"            │ │
│  │ Triage was      │           │ Need deeper investigation             │ │
│  │ enough          │           │                                       │ │
│  └────────┬────────┘           └──────────────────┬────────────────────┘ │
│           │                                       │                      │
│           ▼                                       ▼                      │
│     Return triage              Dispatch specialists (parallel)           │
│     result directly            Each gets: same logs + own tools          │
│     (~20s total)               + own system prompt                       │
│                                       │                                  │
│                                       ▼                                  │
│                                Synthesize all findings                   │
│                                Return deep diagnosis                     │
│                                (~60-90s total)                           │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │
                                 ▼
                          Response returned
                          Conversation saved to DB
```

---

## Follow-up Questions

```
POST /investigate/{id}/ask  {"question": "What tenant was this?"}
    │
    ├── Claude checks: is the user dissatisfied?
    │
    ├── NO (normal question)
    │   → Load conversation history from DB
    │   → Load pre-fetched logs from DB (no re-query)
    │   → Single Claude call with full context
    │   → Answer in 2-5s
    │
    └── YES (user dissatisfied — "that's wrong", "dig deeper", etc.)
        → Auto-dispatch specialist agents
        → Specialists investigate in parallel with pre-fetched logs
        → Synthesize into deeper diagnosis
        → Answer in 30-60s
```

---

## Fix Generation

```
POST /investigate/{id}/fix
    │
    ├── Load conversation history (has the diagnosis)
    ├── Claude reads source code via GitHub tools
    ├── Generates unified diff patch
    └── Returns the fix
```

---

## Module Map

```
ai-sre-agent/
│
├── sre-commons/               Shared models, enums
│   ├── model/                 Incident, AgentDefinition, SkillDefinition,
│   │                          ConversationMessage, InvestigationContext,
│   │                          EpisodicMemory, SemanticMemory, ProceduralMemory,
│   │                          AgentSkillTool, BaseEntity
│   └── enums/                 IncidentStatus, Severity, PluginType,
│                              AgentSource, AlertSource, MemorySource,
│                              ProceduralType, SkillType
│
├── sre-plugin-api/            Plugin SPI — interfaces + DTOs
│   ├── SrePlugin              Base interface all plugins implement
│   ├── MonitoringPlugin       Logs + metrics (Grafana/Loki/Prometheus)
│   ├── CodeHostingPlugin      Code + PRs + deploys (GitHub)
│   ├── DatabasePlugin         DB diagnostics (PostgreSQL)
│   ├── InfrastructurePlugin   AWS ECS/ALB/SQS/CloudWatch
│   ├── ErrorTrackingPlugin    Exceptions + releases (Sentry)
│   ├── PluginRegistry         Central registry for all plugins
│   └── model/                 23 DTOs (LogEntry, MetricDataPoint, CommitInfo, etc.)
│
├── sre-plugin-grafana/        Grafana plugin — Loki log queries, Prometheus metrics
├── sre-plugin-github/         GitHub plugin — file contents, commits, PRs, diffs
├── sre-plugin-aws/            AWS plugin — ECS, ALB, SQS, CloudWatch, Health
├── sre-plugin-postgres/       PostgreSQL plugin — pg_stat_activity, locks, slow queries
├── sre-plugin-sentry/         Sentry plugin — error groups, stack traces, releases
│
├── sre-core/                  Core business logic
│   ├── context/
│   │   ├── ChangeCorrelation  Checks recent deploys/commits (GitHub)
│   │   └── BlastRadius        Checks error rate, affected users, DB pool
│   ├── action/
│   │   └── ActionEngine       Creates PRs on GitHub
│   └── repository/            JPA repositories (Incident, Conversation,
│                              InvestigationContext, AgentDefinition,
│                              SkillDefinition, AgentSkillTool)
│
├── sre-agent-framework/       Agent infrastructure
│   ├── agent/
│   │   ├── BuiltInAgents      Defines 6 platform agents with system prompts
│   │   ├── AgentBootstrap     Registers agents on startup
│   │   └── AgentRegistry      In-memory store for platform + custom agents
│   └── tools/
│       ├── ToolProvider        Interface — maps plugin IDs to tool classes
│       ├── AgentToolBinder     Binds tools to agents based on assignedPlugins
│       ├── LogAnalysisTools    6 @Tool methods (logs, metrics, errors)
│       ├── InfraTools          10 @Tool methods (AWS, DB, queues)
│       ├── CodeAnalysisTools   6 @Tool methods (code, git, deploys)
│       ├── SkillToolWrapper    Wraps a skill as a @Tool
│       └── *ToolProvider       3 provider implementations
│
├── sre-skill-engine/          Skill system
│   ├── parser/
│   │   ├── SkillMarkdownParser Parses .md files into blocks
│   │   ├── ParsedSkill         Parsed skill metadata + blocks
│   │   └── SkillBlock          Single executable block
│   ├── executor/
│   │   ├── SkillBlockExecutor  Interface for block executors
│   │   ├── BashBlockExecutor   Runs bash commands
│   │   ├── SqlBlockExecutor    Runs SQL via DatabasePlugin
│   │   ├── PromptBlockExecutor Sends prompts to Claude
│   │   └── PromqlBlockExecutor Runs PromQL via MonitoringPlugin
│   ├── engine/
│   │   └── SkillEngine         Orchestrates block execution
│   └── registry/
│       ├── SkillRegistryService Register, find, execute skills + auto-embed
│       └── SkillBootstrap       Auto-registers built-in skills on startup
│
├── sre-memory/                Memory + learning
│   ├── service/
│   │   ├── MemoryService      Gathers relevant memory for investigations
│   │   ├── MemoryContext      Renders memory into prompt text
│   │   └── EmbeddingService   Generates embeddings via OpenAI
│   ├── repository/
│   │   ├── EpisodicMemoryRepository    Past incidents
│   │   ├── SemanticMemoryRepository    System knowledge
│   │   └── ProceduralMemoryRepository  Correction rules
│   └── feedback/
│       ├── FeedbackService            Human corrections → procedural rules
│       └── PostResolutionLearner      Stores episodic memory after resolution
│
├── sre-knowledge-base/        Skill discovery layer
│   └── SkillDiscoveryService  Finds relevant skills by keyword/vector similarity
│
├── sre-api/                   REST controllers
│   └── controller/
│       ├── InvestigateController     POST /investigate, /ask, /fix, /history
│       ├── AgentController           CRUD for custom agents
│       ├── AgentSkillToolController  Register skills as tools for agents
│       ├── SkillController           Upload/execute/list skills
│       ├── FeedbackController        Human corrections + playbooks
│       ├── MemoryController          Browse/add memory
│       └── IncidentController        List/view/resolve incidents
│
└── sre-app/                   Spring Boot application
    ├── SreAgentApplication    Entry point
    ├── config/
    │   ├── AiConfig           Anthropic=primary ChatModel, OpenAI=embeddings
    │   └── SecurityConfig     CSRF disabled, all endpoints permitted
    ├── resources/
    │   ├── application.yml    All configuration
    │   ├── logback-spring.xml Structured logging
    │   └── db/migration/      Flyway migrations (V1-V4)
    ├── Dockerfile             Alpine + JRE 21 + psql + curl + ssh + kubectl
    └── docker-compose.yml     PostgreSQL + Redis + SRE Agent
```

---

## How Tools Get Bound to Agents

```
Agent Definition (e.g., code-analyst)
    │
    │ assignedPlugins: ["github", "sentry", "postgres", "aws", "grafana"]
    │
    ▼
AgentToolBinder.bindToolsForAgent()
    │
    ├── LogAnalysisToolProvider:  pluginIds = {grafana, sentry}
    │   "grafana" matches → creates LogAnalysisTools (6 tools)
    │
    ├── InfraToolProvider:  pluginIds = {aws, postgres}
    │   "aws" matches → creates InfraTools (10 tools)
    │
    ├── CodeAnalysisToolProvider:  pluginIds = {github}
    │   "github" matches → creates CodeAnalysisTools (6 tools)
    │
    └── Skill tools (from agent_skill_tools table):
        staging-db registered → creates SkillToolWrapper (1 tool)
    │
    ▼
23 @Tool methods available to Claude
```

---

## How Skills Work

```
Upload: POST /api/v1/skills/upload  (staging-db.md)
    │
    ├── SkillMarkdownParser: parse frontmatter + code blocks
    ├── EmbeddingService: generate embedding of name+description
    └── Save to skill_definitions table (with embedding)

Register as tool: POST /api/v1/agents/investigate/tools  {"skillId": "staging-db"}
    │
    └── Save to agent_skill_tools table

During investigation: Claude decides to call the skill
    │
    ├── SkillToolWrapper.execute("database=esd;sql_query=SELECT...")
    ├── SkillRegistryService.executeSkill("staging-db", params)
    ├── SkillEngine: parse .md → route blocks to executors
    ├── BashBlockExecutor: ProcessBuilder runs ssh → bastion → psql
    └── Returns query result to Claude
```

---

## How Memory Works

```
Three types of memory, all queried before every investigation:

EPISODIC (past incidents)
    Written by: PostResolutionLearner after resolution
    Queried by: MemoryService — "have we seen this before?"
    Example: "3 weeks ago, same service had connection pool issue"

SEMANTIC (system knowledge)
    Written by: User via POST /api/v1/memory/semantic
    Queried by: MemoryService — "what do we know about this service?"
    Example: "esd uses HikariCP pool size 20, connects to PostgreSQL"

PROCEDURAL (correction rules)
    Written by: FeedbackService when user corrects the agent
    Queried by: MemoryService — "are there rules for this pattern?"
    Example: "RULE: When esd shows timeout, check connection pool FIRST"

All three are injected into the agent's system prompt as context.
```

---

## How Feedback Loop Works

```
Investigation gives wrong answer
    │
    ▼
Option A: User says "that's wrong" in /ask
    → Claude detects dissatisfaction
    → Auto-dispatches specialist agents
    → Returns deeper investigation

Option B: User submits correction via /feedback/correction
    → Creates PROCEDURAL MEMORY rule
    → Next similar incident, agent follows the rule
    → Agent doesn't make the same mistake twice

Option C: User resolves via /incidents/{id}/resolve
    → PostResolutionLearner stores EPISODIC MEMORY
    → Next similar incident, agent retrieves past resolution
```

---

## How Streaming Works

```
The streaming endpoints produce Server-Sent Events (SSE) showing the 
investigation reasoning trail in real-time.

POST /investigate/stream
    │
    ├── event:status     "Creating investigation..."
    ├── event:status     "Pre-fetching logs from Loki..."
    ├── event:status     "Checking recent deploys..."
    ├── event:status     "Assessing blast radius..."
    ├── event:status     "Loading memory..."
    ├── event:status     "Starting triage agent with 3 tools..."
    │
    │   (agent starts calling tools — each emits an event)
    │
    ├── event:tool_call  "discoverLogLabels: Discovering available log labels"
    ├── event:tool_call  "queryLogs: {service_name=\"esd\"} |= \"abc-123\" (7d)"
    ├── event:tool_call  "readSourceFile: esd/CannedResponseRepository.java"
    ├── event:tool_call  "skill:staging-db: database=esd;sql_query=SELECT..."
    │
    │   (agent has enough evidence, produces diagnosis)
    │
    ├── event:answer     "## Root Cause\nLazy loading outside transaction..."
    └── event:done       {"incidentId":"..."}
```

Event types:
- `status`      — what the system is doing (pre-fetch, agent start, etc.)
- `tool_call`   — agent is calling a tool (log query, code read, DB skill, etc.)
- `tool_result` — tool returned data (summary of what was found)
- `answer`      — the final diagnosis
- `done`        — investigation complete with incidentId
- `error`       — something went wrong

Sync endpoints return the same results — streaming just shows the process.

---

## API Reference

```
# Investigation (synchronous)
POST   /api/v1/investigate                    Start investigation
POST   /api/v1/investigate/{id}/ask           Follow-up question
POST   /api/v1/investigate/{id}/fix           Generate code fix
GET    /api/v1/investigate/{id}/history        View conversation

# Investigation (streaming — real-time reasoning trail via SSE)
POST   /api/v1/investigate/stream             Start investigation (SSE)
POST   /api/v1/investigate/{id}/ask/stream    Follow-up question (SSE)
POST   /api/v1/investigate/{id}/fix/stream    Generate code fix (SSE)

# Agents
GET    /api/v1/agents                          List all agents
POST   /api/v1/agents                          Create custom agent
PUT    /api/v1/agents/{agentId}                Update agent
POST   /api/v1/agents/{agentId}/activate       Enable
POST   /api/v1/agents/{agentId}/deactivate     Disable

# Agent Tools (skill-tool binding)
GET    /api/v1/agents/{agentId}/tools          List skill-tools
POST   /api/v1/agents/{agentId}/tools          Register skill as tool
DELETE /api/v1/agents/{agentId}/tools/{skillId} Remove

# Skills
GET    /api/v1/skills                          List skills
POST   /api/v1/skills/upload                   Upload .md file
POST   /api/v1/skills/{skillId}/execute        Run manually

# Memory
GET    /api/v1/memory/episodic                 Past incidents
GET    /api/v1/memory/semantic                 System knowledge
GET    /api/v1/memory/procedural               Correction rules
POST   /api/v1/memory/semantic                 Add knowledge

# Feedback
POST   /api/v1/incidents/{id}/feedback/correction  Correct wrong diagnosis
POST   /api/v1/incidents/{id}/feedback/fix-outcome  Fix worked or not
POST   /api/v1/incidents/{id}/feedback/playbook     Add playbook

# Incidents
GET    /api/v1/incidents                       List all
GET    /api/v1/incidents/{id}                   Detail
GET    /api/v1/incidents/stats                  Stats
POST   /api/v1/incidents/{id}/resolve           Resolve

# Health
GET    /actuator/health                        App health
GET    /actuator/prometheus                     Metrics
```

---

## Configuration

All via `.env` file:

```bash
# Required
ANTHROPIC_API_KEY=sk-ant-...        # Claude for chat

# Optional — embeddings
OPENAI_API_KEY=sk-proj-...          # OpenAI for skill embeddings

# Connect what you have
GRAFANA_ENABLED=true
GRAFANA_LOKI_URL=https://...        # Grafana datasource proxy URL for Loki
GRAFANA_PROMETHEUS_URL=https://...   # Grafana datasource proxy URL for Prometheus
GRAFANA_API_KEY=glsa_...

GITHUB_ENABLED=true
GITHUB_TOKEN=ghp_...
GITHUB_OWNER=your-org

SENTRY_ENABLED=false                 # Enable when ready
AWS_ENABLED=false                    # Enable when ready
POSTGRES_DIAG_ENABLED=false          # Enable when ready
```

---

## Database Tables

```
incidents                  Investigation records
conversation_history       Chat messages per investigation
investigation_context      Pre-fetched logs + memory per investigation
agent_definitions          Platform + custom agent definitions
skill_definitions          Skill .md content + embeddings
agent_skill_tools          Maps skills as tools to agents
episodic_memory            Past incidents + outcomes (with pgvector embeddings)
semantic_memory            System knowledge (with pgvector embeddings)
procedural_memory          Correction rules + playbooks
```
