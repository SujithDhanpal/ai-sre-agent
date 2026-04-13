package com.sre.agent.framework.tools;
import com.sre.agent.commons.InvestigationEventStream;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.DatabasePlugin;
import com.sre.agent.plugin.api.InfrastructurePlugin;
import com.sre.agent.plugin.api.PluginRegistry;
import com.sre.agent.plugin.api.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class InfraTools {

    private final PluginRegistry pluginRegistry;
    private final String tenantId;

    @Tool(description = "Check ECS/container service health: running tasks, stopped tasks, OOM kills, restart events. Use this to determine if containers are crashing or unhealthy.")
    public String checkServiceHealth(
            @ToolParam(description = "Service name in ECS/K8s") String serviceName) {

        InvestigationEventStream.toolCall("checkServiceHealth", serviceName);
        log.info("[Tool] checkServiceHealth: service={}", serviceName);

        return pluginRegistry.getPlugin(tenantId, PluginType.INFRASTRUCTURE, InfrastructurePlugin.class)
                .map(infra -> {
                    List<ServiceInstance> instances = infra.getServiceInstances(serviceName);
                    if (instances.isEmpty()) return "No instances found for service: " + serviceName;

                    return instances.stream()
                            .map(i -> "- %s: status=%s, health=%s, restarts=%d, started=%s%s".formatted(
                                    i.instanceId(), i.status(), i.healthStatus(), i.restartCount(), i.lastStarted(),
                                    i.metadata().containsKey("stoppedReason") && !i.metadata().get("stoppedReason").isBlank()
                                            ? ", stoppedReason=" + i.metadata().get("stoppedReason") : ""))
                            .collect(Collectors.joining("\n"));
                })
                .orElse("Infrastructure plugin not available.");
    }

    @Tool(description = "Check ALB/NLB target group health: how many targets are healthy, unhealthy, or draining. Use this to check if the load balancer is routing traffic to healthy instances.")
    public String checkLoadBalancerHealth(
            @ToolParam(description = "Target group ARN or load balancer name") String targetGroupId) {

        InvestigationEventStream.toolCall("checkLoadBalancerHealth", targetGroupId);
        log.info("[Tool] checkLoadBalancerHealth: target={}", targetGroupId);

        return pluginRegistry.getPlugin(tenantId, PluginType.INFRASTRUCTURE, InfrastructurePlugin.class)
                .map(infra -> {
                    TargetGroupHealth health = infra.getLoadBalancerHealth(targetGroupId);
                    var sb = new StringBuilder();
                    sb.append("Total: %d, Healthy: %d, Unhealthy: %d, Draining: %d\n".formatted(
                            health.totalTargets(), health.healthyTargets(),
                            health.unhealthyTargets(), health.drainingTargets()));
                    for (var target : health.targets()) {
                        sb.append("  - %s: %s%s\n".formatted(target.targetId(), target.state(),
                                target.reason() != null ? " (" + target.reason() + ")" : ""));
                    }
                    return sb.toString();
                })
                .orElse("Infrastructure plugin not available.");
    }

    @Tool(description = "Check SQS queue health: message count, age of oldest message, dead letter queue count. Use this to detect consumer failures or message processing backlogs.")
    public String checkQueueHealth(
            @ToolParam(description = "SQS queue URL or name") String queueIdentifier) {

        InvestigationEventStream.toolCall("checkQueueHealth", queueIdentifier);
        log.info("[Tool] checkQueueHealth: queue={}", queueIdentifier);

        return pluginRegistry.getPlugin(tenantId, PluginType.INFRASTRUCTURE, InfrastructurePlugin.class)
                .map(infra -> {
                    QueueHealth q = infra.getQueueHealth(queueIdentifier);
                    return "Queue: %s\nMessages: %d\nOldest message age: %ds\nDead letter queue: %d\nIn-flight: %d".formatted(
                            q.queueName(), q.approximateMessageCount(),
                            q.approximateAgeOfOldestMessageSeconds(),
                            q.deadLetterQueueCount(), q.inflightMessageCount());
                })
                .orElse("Infrastructure plugin not available.");
    }

    @Tool(description = "Check database connection pool status: active, idle, idle-in-transaction, and waiting connections. Critical for detecting connection leaks or pool exhaustion.")
    public String checkConnectionPool() {

        InvestigationEventStream.toolCall("checkConnectionPool", "Checking DB connections");
        log.info("[Tool] checkConnectionPool");

        return pluginRegistry.getPlugin(tenantId, PluginType.DATABASE, DatabasePlugin.class)
                .map(db -> {
                    ConnectionPoolStatus pool = db.getConnectionPoolStatus();
                    return "Total: %d/%d (max)\nActive: %d\nIdle: %d\nIdle-in-transaction: %d\nWaiting on lock: %d\nUtilization: %.1f%%".formatted(
                            pool.totalConnections(), pool.maxConnections(),
                            pool.activeConnections(), pool.idleConnections(),
                            pool.idleInTransactionConnections(), pool.waitingOnLockConnections(),
                            pool.maxConnections() > 0 ? (double) pool.activeConnections() / pool.maxConnections() * 100 : 0);
                })
                .orElse("Database plugin not available.");
    }

    @Tool(description = "Get currently active database queries and their duration. Use this to find long-running or stuck queries that may be causing timeouts.")
    public String getActiveQueries() {

        InvestigationEventStream.toolCall("getActiveQueries", "Checking active DB queries");
        log.info("[Tool] getActiveQueries");

        return pluginRegistry.getPlugin(tenantId, PluginType.DATABASE, DatabasePlugin.class)
                .map(db -> {
                    List<ActiveQuery> queries = db.getActiveQueries();
                    if (queries.isEmpty()) return "No active queries running.";

                    return queries.stream()
                            .map(q -> "- PID %d [%s] running for %s: %s".formatted(
                                    q.pid(), q.state(), q.duration(),
                                    q.query().length() > 150 ? q.query().substring(0, 150) + "..." : q.query()))
                            .collect(Collectors.joining("\n"));
                })
                .orElse("Database plugin not available.");
    }

    @Tool(description = "Check for database lock contention and deadlocks. Shows which queries are blocking which.")
    public String checkDatabaseLocks() {

        InvestigationEventStream.toolCall("checkDatabaseLocks", "Checking lock contention");
        log.info("[Tool] checkDatabaseLocks");

        return pluginRegistry.getPlugin(tenantId, PluginType.DATABASE, DatabasePlugin.class)
                .map(db -> {
                    List<LockInfo> locks = db.getCurrentLocks();
                    if (locks.isEmpty()) return "No lock contention detected.";

                    return locks.stream()
                            .map(l -> "BLOCKED: PID %d (%s)\n  BY: PID %d (%s)\n  Lock: %s on %s".formatted(
                                    l.blockedPid(), truncate(l.blockedQuery(), 100),
                                    l.blockingPid(), truncate(l.blockingQuery(), 100),
                                    l.lockType(), l.relation()))
                            .collect(Collectors.joining("\n\n"));
                })
                .orElse("Database plugin not available.");
    }

    @Tool(description = "Get slow queries from pg_stat_statements ordered by average execution time. Use this to identify queries that are degrading performance.")
    public String getSlowQueries(
            @ToolParam(description = "Number of slow queries to return, e.g. 10") int limit) {

        InvestigationEventStream.toolCall("getSlowQueries", "Top " + limit + " slow queries");
        log.info("[Tool] getSlowQueries: limit={}", limit);

        return pluginRegistry.getPlugin(tenantId, PluginType.DATABASE, DatabasePlugin.class)
                .map(db -> {
                    List<SlowQuery> queries = db.getSlowQueries(Instant.now().minusSeconds(3600), Math.min(limit, 20));
                    if (queries.isEmpty()) return "No slow query data available (pg_stat_statements may not be enabled).";

                    return queries.stream()
                            .map(q -> "- avg: %.1fms, total: %.0fms, calls: %d, rows: %d\n  %s".formatted(
                                    q.meanExecTimeMs(), q.totalExecTimeMs(), q.calls(), q.rows(),
                                    truncate(q.query(), 200)))
                            .collect(Collectors.joining("\n\n"));
                })
                .orElse("Database plugin not available.");
    }

    @Tool(description = "Check database replication lag between primary and replicas. High lag means replicas are behind and read queries may return stale data.")
    public String checkReplicationLag() {

        InvestigationEventStream.toolCall("checkReplicationLag", "Checking replica lag");
        log.info("[Tool] checkReplicationLag");

        return pluginRegistry.getPlugin(tenantId, PluginType.DATABASE, DatabasePlugin.class)
                .map(db -> {
                    ReplicationStatus status = db.getReplicationStatus();
                    if (!status.hasReplicas()) return "No replicas configured.";

                    return status.replicas().stream()
                            .map(r -> "- Replica %s: state=%s, lag=%d bytes%s".formatted(
                                    r.clientAddr(), r.state(), r.lagBytes(),
                                    r.lagSeconds() != null ? ", lag=%.1fs".formatted(r.lagSeconds()) : ""))
                            .collect(Collectors.joining("\n"));
                })
                .orElse("Database plugin not available.");
    }

    @Tool(description = "Run a read-only diagnostic SQL query against the database. Only SELECT/WITH/EXPLAIN queries are allowed. Use this to investigate data-level issues.")
    public String executeDiagnosticQuery(
            @ToolParam(description = "Read-only SQL query (SELECT, WITH, or EXPLAIN only)") String sql) {

        InvestigationEventStream.toolCall("executeDiagnosticQuery", sql.substring(0, Math.min(sql.length(), 80)));
        log.info("[Tool] executeDiagnosticQuery: {}", sql.substring(0, Math.min(sql.length(), 80)));

        return pluginRegistry.getPlugin(tenantId, PluginType.DATABASE, DatabasePlugin.class)
                .map(db -> {
                    QueryResult result = db.executeReadOnlyQuery(sql, Map.of());
                    if (result.rows().isEmpty()) return "Query returned no rows. (%dms)".formatted(result.executionTimeMs());

                    var sb = new StringBuilder();
                    sb.append("Columns: ").append(String.join(", ", result.columns())).append("\n");
                    sb.append("Rows: ").append(result.rowCount()).append(" (").append(result.executionTimeMs()).append("ms)\n\n");
                    for (var row : result.rows()) {
                        sb.append(row.entrySet().stream()
                                .map(e -> e.getKey() + "=" + e.getValue())
                                .collect(Collectors.joining(" | "))).append("\n");
                    }
                    return sb.toString();
                })
                .orElse("Database plugin not available.");
    }

    @Tool(description = "Check for AWS service disruptions or scheduled maintenance affecting resources in this region.")
    public String checkAWSHealth() {

        InvestigationEventStream.toolCall("checkAWSHealth", "Checking AWS service status");
        log.info("[Tool] checkAWSHealth");

        return pluginRegistry.getPlugin(tenantId, PluginType.INFRASTRUCTURE, InfrastructurePlugin.class)
                .map(infra -> {
                    List<InfraEvent> events = infra.getCloudHealthEvents();
                    if (events.isEmpty()) return "No AWS health events — all services operating normally.";

                    return events.stream()
                            .map(e -> "- [%s] %s: %s (%s)".formatted(e.severity(), e.serviceName(), e.description(), e.timestamp()))
                            .collect(Collectors.joining("\n"));
                })
                .orElse("Infrastructure plugin not available.");
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
