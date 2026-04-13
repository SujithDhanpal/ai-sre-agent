package com.sre.agent.demo;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.DatabasePlugin;
import com.sre.agent.plugin.api.PluginConfiguration;
import com.sre.agent.plugin.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
@Profile("demo")
@Slf4j
public class DemoDatabasePlugin implements DatabasePlugin {

    private final Instant now = Instant.now();

    @Override
    public ConnectionPoolStatus getConnectionPoolStatus() {
        log.info("[DEMO] getConnectionPoolStatus");
        // total=20, active=20, idle=0, idle-in-txn=3, waiting=12, max=20
        return new ConnectionPoolStatus(20, 20, 0, 3, 12, 20);
    }

    @Override
    public List<ActiveQuery> getActiveQueries() {
        log.info("[DEMO] getActiveQueries");
        return List.of(
                new ActiveQuery(1001, "SELECT * FROM payment_transactions WHERE status='PENDING' AND created_at > '2026-04-13 08:00:00' ORDER BY created_at LIMIT 500", "active", Duration.ofSeconds(45), "ClientRead", "payment-service-pod-1"),
                new ActiveQuery(1002, "SELECT * FROM payment_transactions WHERE status='PENDING' AND created_at > '2026-04-13 08:00:00' ORDER BY created_at LIMIT 500", "active", Duration.ofSeconds(44), "ClientRead", "payment-service-pod-2"),
                new ActiveQuery(1003, "SELECT * FROM payment_transactions WHERE status='PENDING' AND created_at > '2026-04-13 08:00:00' ORDER BY created_at LIMIT 500", "active", Duration.ofSeconds(43), "ClientRead", "payment-service-pod-3"),
                new ActiveQuery(1004, "UPDATE payment_transactions SET status='RECONCILED', reconciled_at=NOW() WHERE id IN (SELECT id FROM payment_transactions WHERE status='PENDING' LIMIT 500)", "active", Duration.ofSeconds(38), "Lock", "payment-service-pod-1"),
                new ActiveQuery(1005, "INSERT INTO payment_audit_log (transaction_id, action, created_at) VALUES ($1, $2, $3)", "idle in transaction", Duration.ofSeconds(120), null, "payment-service-pod-2")
        );
    }

    @Override
    public List<SlowQuery> getSlowQueries(Instant since, int limit) {
        log.info("[DEMO] getSlowQueries");
        return List.of(
                new SlowQuery("SELECT * FROM payment_transactions WHERE status='PENDING' AND created_at > $1 ORDER BY created_at LIMIT 500", 847, 45200.0, 38294000.0, 425000),
                new SlowQuery("UPDATE payment_transactions SET status='RECONCILED', reconciled_at=NOW() WHERE id IN (...)", 312, 28400.0, 8860800.0, 156000),
                new SlowQuery("INSERT INTO payment_audit_log (transaction_id, action, created_at) VALUES ($1, $2, $3)", 42500, 12.5, 531250.0, 42500)
        );
    }

    @Override
    public List<LockInfo> getCurrentLocks() {
        log.info("[DEMO] getCurrentLocks");
        return List.of(
                new LockInfo(1004, "UPDATE payment_transactions SET status='RECONCILED'...", 1005, "INSERT INTO payment_audit_log...", "RowExclusiveLock", "payment_transactions")
        );
    }

    @Override
    public ReplicationStatus getReplicationStatus() {
        log.info("[DEMO] getReplicationStatus");
        return new ReplicationStatus(true, List.of(
                new ReplicationStatus.ReplicaInfo("10.0.1.50", "streaming", 1024, 0.3)
        ));
    }

    @Override
    public QueryResult executeReadOnlyQuery(String sql, Map<String, Object> params) {
        log.info("[DEMO] executeReadOnlyQuery: {}", sql);
        if (sql.toLowerCase().contains("pg_stat_activity")) {
            return new QueryResult(
                    List.of("state", "count", "wait_event_type"),
                    List.of(
                            Map.of("state", "active", "count", 20, "wait_event_type", "ClientRead"),
                            Map.of("state", "idle", "count", 0, "wait_event_type", ""),
                            Map.of("state", "idle in transaction", "count", 3, "wait_event_type", "")
                    ), 3, 12
            );
        }
        return new QueryResult(List.of(), List.of(), 0, 5);
    }

    @Override
    public long getRowCount(String tableName, String whereClause) {
        return 1_247_832;
    }

    @Override
    public List<Map<String, Object>> sampleRows(String tableName, String whereClause, int limit) {
        return List.of(
                Map.of("id", "txn_90281", "status", "PENDING", "amount", 249.99, "created_at", "2026-04-13T09:15:00Z"),
                Map.of("id", "txn_90282", "status", "PENDING", "amount", 89.50, "created_at", "2026-04-13T09:15:01Z")
        );
    }

    @Override public String getPluginId() { return "postgres"; }
    @Override public String getDisplayName() { return "Demo PostgreSQL Diagnostics"; }
    @Override public String getVersion() { return "1.0.0-demo"; }
    @Override public PluginType getType() { return PluginType.DATABASE; }
    @Override public PluginConfiguration getDefaultConfiguration() { return new PluginConfiguration(List.of()); }
    @Override public void initialize(Map<String, String> config) {}
    @Override public boolean validateConnection() { return true; }
}
