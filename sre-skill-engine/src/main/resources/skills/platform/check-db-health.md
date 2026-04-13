---
id: check-db-health
name: Database Health Check
description: Comprehensive PostgreSQL health check — connections, slow queries, locks, replication
version: 1.0.0
author: platform
source: platform

requires:
  plugins: [postgres]
  permissions: [db:read]

inputs:
  - name: service_name
    type: string
    default: "primary"
---

# Database Health Check

Comprehensive PostgreSQL diagnostic check.

## Step 1: Check connection pool

```sql
# plugin: postgres
# timeout: 10s
SELECT count(*) AS total_connections,
       count(*) FILTER (WHERE state = 'active') AS active,
       count(*) FILTER (WHERE state = 'idle') AS idle,
       count(*) FILTER (WHERE state = 'idle in transaction') AS idle_in_txn,
       count(*) FILTER (WHERE wait_event_type = 'Lock') AS waiting_on_lock,
       (SELECT setting::int FROM pg_settings WHERE name = 'max_connections') AS max_conn
FROM pg_stat_activity
WHERE backend_type = 'client backend';
```

## Step 2: Check for long-running queries

```sql
# plugin: postgres
# timeout: 10s
SELECT pid, now() - query_start AS duration, state, left(query, 200) AS query
FROM pg_stat_activity
WHERE state != 'idle'
  AND backend_type = 'client backend'
  AND now() - query_start > interval '5 seconds'
ORDER BY duration DESC
LIMIT 10;
```

## Step 3: Check lock contention

```sql
# plugin: postgres
# timeout: 10s
SELECT blocked.pid AS blocked_pid, left(blocked.query, 150) AS blocked_query,
       blocking.pid AS blocking_pid, left(blocking.query, 150) AS blocking_query
FROM pg_locks bl
JOIN pg_stat_activity blocked ON bl.pid = blocked.pid
JOIN pg_locks bl2 ON bl.locktype = bl2.locktype
    AND bl.database IS NOT DISTINCT FROM bl2.database
    AND bl.relation IS NOT DISTINCT FROM bl2.relation
    AND bl.pid != bl2.pid
JOIN pg_stat_activity blocking ON bl2.pid = blocking.pid
WHERE NOT bl.granted AND bl2.granted
LIMIT 20;
```

## Step 4: Check replication lag

```sql
# plugin: postgres
# timeout: 10s
SELECT client_addr::text, state,
       pg_wal_lsn_diff(sent_lsn, replay_lsn) AS lag_bytes,
       EXTRACT(EPOCH FROM replay_lag)::int AS lag_seconds
FROM pg_stat_replication;
```

## Step 5: Analyze results

```prompt
# model: claude-haiku
# structured_output: true

Based on the database health check results:
- Connection Pool: {{step1.result}}
- Long-Running Queries: {{step2.result}}
- Lock Contention: {{step3.result}}
- Replication Status: {{step4.result}}

Analyze the overall database health. Identify any issues and classify each as:
- CRITICAL: immediate action needed
- WARNING: monitor closely
- OK: no issues

Provide a summary and recommended actions.
```
