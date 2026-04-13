# AI SRE Agent — Setup Guide

## Prerequisites

- **Docker** and **Docker Compose** installed
- **Anthropic API key** (required — this powers the AI)
- 4GB free RAM for the containers

That's it. Everything else is optional.

---

## Quick Start

### 1. Clone and create `.env`

```bash
cd ai-sre-agent
cp .env.example .env
```

Edit `.env` and add your Anthropic API key:

```bash
ANTHROPIC_API_KEY=sk-ant-api03-your-key-here
```

### 2. Start

```bash
docker compose up -d --build
```

This starts 3 containers:
- **sre-agent** — the application (port 8080)
- **sre-postgres** — PostgreSQL with pgvector (port 15432)
- **sre-redis** — Redis for caching (port 6379)

### 3. Verify

```bash
curl http://localhost:8080/actuator/health
```

Should return `{"status":"UP"}`.

### 4. Test

```bash
curl -X POST http://localhost:8080/api/v1/investigate \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Are there any errors in my-service in the last hour?",
    "services": ["my-service"]
  }'
```

---

## What's Required vs Optional

### Required

| What | Why | How |
|------|-----|-----|
| **Anthropic API key** | Powers all AI reasoning, tool calling, diagnosis | `ANTHROPIC_API_KEY` in `.env` |
| **Docker** | Runs the app, PostgreSQL, Redis | Install Docker Desktop |

### Optional — Connect When Ready

Each integration makes the agent smarter. Start with none and add as needed.

#### Grafana (Loki + Prometheus)

Gives the agent access to your application logs and metrics. Without this, the agent can't search logs.

```bash
# .env
GRAFANA_ENABLED=true
GRAFANA_LOKI_URL=https://your-grafana/api/datasources/proxy/uid/{loki-uid}
GRAFANA_PROMETHEUS_URL=https://your-grafana/api/datasources/proxy/uid/{prometheus-uid}
GRAFANA_API_KEY=glsa_your-api-key
```

**How to find the URLs:**
1. Open Grafana → Settings → Data Sources
2. Click on Loki → note the UID from the URL (e.g., `efbpfm6novkzkd`)
3. Your Loki URL is: `https://your-grafana/api/datasources/proxy/uid/{that-uid}`
4. Same for Prometheus

**How to create the API key:**
1. Grafana → Administration → Service Accounts → Add service account
2. Add token → copy the `glsa_...` key

#### GitHub

Gives the agent access to your source code. Without this, the agent can diagnose from logs but can't read code or suggest specific fixes.

```bash
# .env
GITHUB_ENABLED=true
GITHUB_TOKEN=ghp_your-personal-access-token
GITHUB_OWNER=your-org-or-username
```

**How to create the token:**
1. GitHub → Settings → Developer Settings → Personal Access Tokens → Fine-grained tokens
2. Permissions needed: `Contents: Read`, `Pull requests: Read and write`, `Metadata: Read`

#### OpenAI (Embeddings Only)

Used for skill discovery embeddings. Not required — skill discovery falls back to keyword matching without it. Very cheap (~$0.02 per million tokens).

```bash
# .env
OPENAI_API_KEY=sk-proj-your-key
```

#### Sentry

Gives the agent access to exception tracking — grouped errors, stack traces, release health.

```bash
# .env
SENTRY_ENABLED=true
SENTRY_AUTH_TOKEN=sntrys_your-token
SENTRY_ORG=your-org-slug
```

**How to create the token:**
1. Sentry → Settings → Auth Tokens → Create New Token
2. Scopes: `project:read`, `event:read`, `org:read`

#### AWS

Gives the agent access to infrastructure health — ECS containers, ALB targets, SQS queues, CloudWatch metrics, AWS Health events.

```bash
# .env
AWS_ENABLED=true
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=AKIA...
AWS_SECRET_ACCESS_KEY=your-secret
```

Or if running on EC2/ECS with IAM role — just set `AWS_ENABLED=true` and `AWS_REGION`.

#### PostgreSQL Diagnostics

Gives the agent direct JDBC access to your application database for diagnostics — connection pool, active queries, locks, replication lag.

```bash
# .env
POSTGRES_DIAG_ENABLED=true
POSTGRES_DIAG_URL=jdbc:postgresql://your-db-host:5432/your_app_db
POSTGRES_DIAG_USER=readonly_user
POSTGRES_DIAG_PASSWORD=your-password
```

Use a **read-only** database user. The agent never writes to your application database.

#### SSH Key (for skills that need bastion access)

If your skills need SSH access to a bastion host (e.g., to reach internal databases), mount the key into the container:

In `docker-compose.yml` under the `sre-agent` service, add:
```yaml
volumes:
  - /path/to/your/ssh-key:/keys/your-key:ro
```

---

## Complete `.env.example`

```bash
# === REQUIRED ===
ANTHROPIC_API_KEY=

# === OPTIONAL — Embeddings ===
OPENAI_API_KEY=

# === OPTIONAL — Grafana (logs + metrics) ===
GRAFANA_ENABLED=false
GRAFANA_LOKI_URL=
GRAFANA_PROMETHEUS_URL=
GRAFANA_API_KEY=

# === OPTIONAL — GitHub (code + PRs) ===
GITHUB_ENABLED=false
GITHUB_TOKEN=
GITHUB_OWNER=

# === OPTIONAL — Sentry (error tracking) ===
SENTRY_ENABLED=false
SENTRY_AUTH_TOKEN=
SENTRY_ORG=

# === OPTIONAL — AWS (infrastructure) ===
AWS_ENABLED=false
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=

# === OPTIONAL — PostgreSQL diagnostics (direct DB access) ===
POSTGRES_DIAG_ENABLED=false
POSTGRES_DIAG_URL=
POSTGRES_DIAG_USER=
POSTGRES_DIAG_PASSWORD=
```

---

## What Works With Each Integration

| Connected | What the agent can do |
|-----------|----------------------|
| Nothing (just Claude) | Answer questions based on what you tell it |
| + Grafana | Search logs, check error rates, query metrics |
| + GitHub | Read source code, check git history, trace code flow, suggest fixes with actual file paths |
| + Sentry | See grouped exceptions, stack traces, release health, detect regressions |
| + AWS | Check container health, ALB targets, queue depth, CloudWatch metrics |
| + PostgreSQL | Check connection pool, active queries, locks, replication lag, run diagnostic SQL |
| + Skills | Execute custom bash scripts, query databases via bastion, run playbooks |

---

## After Setup

### Register a skill

```bash
curl -X POST http://localhost:8080/api/v1/skills/upload \
  -F "file=@your-skill.md"
```

### Assign skill to the investigate agent

```bash
curl -X POST http://localhost:8080/api/v1/agents/investigate/tools \
  -H "Content-Type: application/json" \
  -d '{"skillId": "your-skill-id"}'
```

### Add system knowledge

```bash
curl -X POST http://localhost:8080/api/v1/memory/semantic \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "service-topology",
    "key": "my-service",
    "value": "Spring Boot app, PostgreSQL via HikariCP pool 20, publishes to Kafka topic orders.created"
  }'
```

### Investigate

```bash
curl -X POST http://localhost:8080/api/v1/investigate \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What happened with request abc-123?",
    "services": ["my-service"]
  }'
```

### Follow up

```bash
curl -X POST http://localhost:8080/api/v1/investigate/{incidentId}/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What tenant was this?"}'
```

### Get a fix

```bash
curl -X POST http://localhost:8080/api/v1/investigate/{incidentId}/fix
```

### Streaming (real-time reasoning trail)

For real-time updates showing what the agent is doing as it investigates, use the streaming endpoints:

```bash
# Streaming investigation — see tool calls and reasoning in real-time
curl -N -X POST http://localhost:8080/api/v1/investigate/stream \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What happened with request abc-123?",
    "services": ["my-service"]
  }'
```

You'll see events as they happen:
```
event:status
data:Pre-fetching logs from Loki...

event:status
data:Checking recent deploys...

event:status
data:Starting triage agent with 3 tools...

event:tool_call
data:queryLogs: {service_name="my-service"} |= "abc-123" (7d)

event:tool_call
data:readSourceFile: my-service/src/main/java/.../MyController.java

event:tool_call
data:skill:staging-db: database=esd;sql_query=SELECT COUNT(*) FROM users

event:answer
data:## Root Cause
data:...full diagnosis...

event:done
data:{"incidentId":"..."}
```

Streaming follow-up:
```bash
curl -N -X POST http://localhost:8080/api/v1/investigate/{incidentId}/ask/stream \
  -H "Content-Type: application/json" \
  -d '{"question": "What tenant was this?"}'
```

Streaming fix:
```bash
curl -N -X POST http://localhost:8080/api/v1/investigate/{incidentId}/fix/stream
```

All streaming endpoints produce the same investigation results as the sync versions — they just show you the process in real-time.

---

## Troubleshooting

### App won't start

```bash
docker logs sre-agent 2>&1 | tail -20
```

Common issues:
- **Missing API key**: `ANTHROPIC_API_KEY` not set in `.env`
- **Port conflict**: PostgreSQL port 15432 or Redis port 6379 already in use
- **Migration failed**: Run `docker compose down -v && docker compose up -d --build` to reset

### Loki queries return empty

- Check the Loki URL is the **datasource proxy path**, not the Grafana UI URL
- The format is: `https://your-grafana/api/datasources/proxy/uid/{loki-datasource-uid}`
- Verify by calling: `curl -H "Authorization: Bearer {api-key}" "{loki-url}/loki/api/v1/labels"`

### Skills fail to execute

- Check if required tools are installed: `docker exec sre-agent which psql curl ssh`
- Check if SSH key is mounted: `docker exec sre-agent ls -la /keys/`
- Check skill logs: `docker logs sre-agent 2>&1 | grep SkillTool`

### Rebuild after code changes

```bash
mvn package -DskipTests && docker compose up -d --build sre-agent
```
