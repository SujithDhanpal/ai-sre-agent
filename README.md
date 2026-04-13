# AI SRE Agent — From Alert to Root Cause to Fix

**Built It, Shipped It, My Team Uses It Every Day.**

An autonomous production incident investigation platform built in Java/Spring Boot. Give it an incident query, and it queries live production systems, traces data flows through code, diagnoses the root cause with cited evidence, and generates fixes — all without human intervention.

---

## Problem

When a production incident hits, SREs spend 30-60 minutes manually piecing together the picture — jumping between Grafana logs, AWS dashboards, database queries, source code, and recent deploy history. The diagnosis depends on tribal knowledge that's hard to transfer, and on-call engineers often lack context for services they didn't build.

This platform automates the entire investigation workflow, not just the "summarize these logs" part.

---

## What This Does

- **End-to-end autonomous investigation** — Takes a natural language query ("what happened to request abc-123 in payment-service?"), queries live production systems (Grafana, AWS, PostgreSQL, Sentry, GitHub), traces the data flow through code, and produces a root cause diagnosis with cited evidence. Follow-up questions maintain full conversation context.

- **Plugin architecture (SPI pattern)** — Every external system is behind a Service Provider Interface. Swapping Grafana for Datadog or AWS for GCP means implementing one interface. Currently 5 plugins: Grafana (Loki + Prometheus), GitHub, AWS (ECS, ALB, SQS, CloudWatch), PostgreSQL, and Sentry.

- **Multi-agent system with structured accumulation** — A fast triage agent handles most incidents alone using a ReAct loop. For complex cases, it auto-escalates to specialist agents (log analyst, infrastructure, code & data flow) that run in parallel. Each produces structured findings (root cause, confidence score, key evidence, gaps). A synthesis agent merges them — identifying consensus, resolving conflicts, and surfacing what only one specialist caught.

- **Custom agents & skills — extensible without code changes** — Teams onboard their own specialist agents via API: define a system prompt, assign plugins and skills, pick an LLM model, set token budgets. For skills, existing operations runbooks written as markdown (with embedded bash, SQL, PromQL blocks) become executable agent capabilities. Domain-specific tribal knowledge gets codified once and is available to every agent going forward.

- **Memory system that learns from corrections** — Three-tier memory backed by pgvector: episodic (past incidents), semantic (system topology), procedural (correction rules). When a human corrects a wrong diagnosis, the system creates a rule that gets injected into future investigations.

- **Autonomous fix generation & PR creation** — Once the root cause is confirmed, the agent reads source files from GitHub, generates a minimal code patch, creates a branch, commits the fix, and opens a pull request autonomously.

- **Real-time streaming with reasoning trail** — SSE endpoints stream the agent's thought process live: which tools it's calling, what it found, when it's escalating to specialists.

- **Dissatisfaction-driven escalation** — If a user says "that's wrong" or "dig deeper", the system detects the sentiment and automatically dispatches specialist agents for a deeper investigation.

---

## Investigation Flow

```
                    Incident Query
                         |
                         v
        Pre-fetch: Logs, Deploys, Blast Radius, Memory
                         |
                         v
               Triage Agent (ReAct Loop)
                         |
              +----------+----------+
              |                     |
         Sufficient?           Complex?
              |                     |
              v                     v
        Return answer    Dispatch Specialists (parallel)
                         |          |          |
                     Log Analyst  Infra    Code Analyst
                         |          |          |
                         v          v          v
                    Structured Accumulation & Synthesis
                         |
                         v
                  Root Cause Diagnosis
                         |
                         v
               Fix Generation & PR Creation
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 21 + Spring Boot 3.3 |
| AI/LLM | Spring AI 1.0, Anthropic Claude (Sonnet + Haiku) |
| Embeddings | OpenAI Embeddings API |
| Database | PostgreSQL 16 + pgvector |
| Cache | Redis |
| Streaming | Server-Sent Events (SSE) |
| Containerization | Docker + Docker Compose |

---

## Project Structure

```
ai-sre-agent/
|
|-- sre-commons/            Shared models, enums, entities
|-- sre-plugin-api/         Plugin SPI interfaces (6 plugin types, 23 DTOs)
|-- sre-core/               Repositories, change correlation, blast radius
|-- sre-agent-framework/    Agent registry, tool binding, 6 built-in agents
|-- sre-skill-engine/       Skill parser, 4 block executors (bash, SQL, prompt, PromQL)
|-- sre-memory/             Episodic, semantic, procedural memory + feedback loop
|-- sre-knowledge-base/     Skill discovery via keyword/vector similarity
|
|-- sre-plugin-grafana/     Grafana plugin (Loki logs, Prometheus metrics)
|-- sre-plugin-github/      GitHub plugin (code, commits, PRs, deploys)
|-- sre-plugin-aws/         AWS plugin (ECS, ALB, SQS, CloudWatch, Health)
|-- sre-plugin-postgres/    PostgreSQL diagnostics (queries, locks, replication)
|-- sre-plugin-sentry/      Sentry plugin (errors, stack traces, releases)
|
|-- sre-api/                REST controllers (8 endpoints)
|-- sre-app/                Spring Boot app, config, migrations, prompts
```

**14 modules | 112 Java files | 5 plugins | 6 agents | 25+ tool methods | 12 prompt files**

---

## Quick Start

See **[SETUP.md](SETUP.md)** for full setup instructions.

```bash
# 1. Clone
git clone https://github.com/SujithDhanpal/ai-sre-agent.git
cd ai-sre-agent

# 2. Configure (minimum: Anthropic API key)
cp .env.example .env
# Edit .env with your keys

# 3. Build & run
mvn clean package -DskipTests
docker-compose up --build
```

### Demo Mode (no external systems needed)

Run with synthetic data — no Grafana, AWS, PostgreSQL, or GitHub required. Only needs an Anthropic API key.

```bash
SPRING_PROFILES_ACTIVE=demo ANTHROPIC_API_KEY=your-key-here mvn spring-boot:run -pl sre-app
```

Then try:
```bash
curl -X POST http://localhost:8080/api/v1/investigate \
  -H "Content-Type: application/json" \
  -d '{"query": "what happened to request b24cb14f-a9a9-4e6e-ac8e-410b7c7dee13", "services": ["payment-service"]}'
```

The demo simulates a **payment service timeout caused by DB connection pool exhaustion after a bad deploy** — realistic logs, DB stats, error groups, deploy history, and source code.

---

## API Endpoints

```
POST /api/v1/investigate              Start a new investigation
POST /api/v1/investigate/{id}/ask     Follow-up question
POST /api/v1/investigate/{id}/fix     Generate fix & create PR
GET  /api/v1/investigate/{id}/history Conversation history

POST /api/v1/investigate/stream              SSE streaming investigation
POST /api/v1/investigate/{id}/ask/stream     SSE streaming follow-up
POST /api/v1/investigate/{id}/fix/stream     SSE streaming fix

POST /api/v1/agents                   Register custom agent
POST /api/v1/skills                   Register custom skill
POST /api/v1/feedback/{id}/correction Submit diagnosis correction
```

---

## Documentation

- **[ARCHITECTURE.md](ARCHITECTURE.md)** — Full architecture guide: investigation flow, module map, plugin SPI, tool binding, multi-agent framework, memory system, streaming pattern
- **[SETUP.md](SETUP.md)** — Setup guide: prerequisites, configuration, Docker deployment, optional integrations, troubleshooting

---

## License

MIT
