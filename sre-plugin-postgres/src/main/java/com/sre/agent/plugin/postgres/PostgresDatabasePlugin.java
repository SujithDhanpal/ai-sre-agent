package com.sre.agent.plugin.postgres;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.DatabasePlugin;
import com.sre.agent.plugin.api.PluginConfiguration;
import com.sre.agent.plugin.api.PluginConfiguration.ConfigField;
import com.sre.agent.plugin.api.PluginConfiguration.ConfigFieldType;
import com.sre.agent.plugin.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "sre.plugins.postgres.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class PostgresDatabasePlugin implements DatabasePlugin {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresPluginProperties properties;

    public PostgresDatabasePlugin(DataSource dataSource, PostgresPluginProperties properties) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(properties.getQueryTimeoutSeconds());
        this.properties = properties;
    }

    @Override
    public ConnectionPoolStatus getConnectionPoolStatus() {
        log.debug("Querying PostgreSQL connection pool status");
        return jdbcTemplate.queryForObject("""
                SELECT
                    count(*) AS total_connections,
                    count(*) FILTER (WHERE state = 'active') AS active,
                    count(*) FILTER (WHERE state = 'idle') AS idle,
                    count(*) FILTER (WHERE state = 'idle in transaction') AS idle_in_txn,
                    count(*) FILTER (WHERE wait_event_type = 'Lock') AS waiting_on_lock,
                    (SELECT setting::int FROM pg_settings WHERE name = 'max_connections') AS max_conn
                FROM pg_stat_activity
                WHERE backend_type = 'client backend'
                """,
                (rs, rowNum) -> new ConnectionPoolStatus(
                        rs.getInt("total_connections"),
                        rs.getInt("active"),
                        rs.getInt("idle"),
                        rs.getInt("idle_in_txn"),
                        rs.getInt("waiting_on_lock"),
                        rs.getInt("max_conn")
                ));
    }

    @Override
    public List<ActiveQuery> getActiveQueries() {
        log.debug("Querying active PostgreSQL queries");
        return jdbcTemplate.query("""
                SELECT pid, query, state,
                       EXTRACT(EPOCH FROM (now() - query_start)) AS duration_secs,
                       wait_event_type, client_addr::text
                FROM pg_stat_activity
                WHERE state != 'idle'
                  AND backend_type = 'client backend'
                  AND pid != pg_backend_pid()
                ORDER BY duration_secs DESC
                LIMIT 50
                """,
                (rs, rowNum) -> new ActiveQuery(
                        rs.getInt("pid"),
                        rs.getString("query"),
                        rs.getString("state"),
                        Duration.ofSeconds((long) rs.getDouble("duration_secs")),
                        rs.getString("wait_event_type"),
                        rs.getString("client_addr")
                ));
    }

    @Override
    public List<SlowQuery> getSlowQueries(Instant since, int limit) {
        log.debug("Querying slow queries from pg_stat_statements");
        try {
            return jdbcTemplate.query("""
                    SELECT query, calls, mean_exec_time, total_exec_time, rows
                    FROM pg_stat_statements
                    WHERE calls > 0
                    ORDER BY mean_exec_time DESC
                    LIMIT ?
                    """,
                    (rs, rowNum) -> new SlowQuery(
                            rs.getString("query"),
                            rs.getLong("calls"),
                            rs.getDouble("mean_exec_time"),
                            rs.getDouble("total_exec_time"),
                            rs.getLong("rows")
                    ),
                    limit);
        } catch (Exception e) {
            log.warn("pg_stat_statements not available: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<LockInfo> getCurrentLocks() {
        log.debug("Querying current lock contention");
        return jdbcTemplate.query("""
                SELECT
                    blocked.pid AS blocked_pid,
                    blocked.query AS blocked_query,
                    blocking.pid AS blocking_pid,
                    blocking.query AS blocking_query,
                    bl.locktype AS lock_type,
                    bl.relation::regclass::text AS relation
                FROM pg_locks bl
                JOIN pg_stat_activity blocked ON bl.pid = blocked.pid
                JOIN pg_locks bl2 ON bl.locktype = bl2.locktype
                    AND bl.database IS NOT DISTINCT FROM bl2.database
                    AND bl.relation IS NOT DISTINCT FROM bl2.relation
                    AND bl.page IS NOT DISTINCT FROM bl2.page
                    AND bl.tuple IS NOT DISTINCT FROM bl2.tuple
                    AND bl.pid != bl2.pid
                JOIN pg_stat_activity blocking ON bl2.pid = blocking.pid
                WHERE NOT bl.granted AND bl2.granted
                LIMIT 50
                """,
                (rs, rowNum) -> new LockInfo(
                        rs.getInt("blocked_pid"),
                        rs.getString("blocked_query"),
                        rs.getInt("blocking_pid"),
                        rs.getString("blocking_query"),
                        rs.getString("lock_type"),
                        rs.getString("relation")
                ));
    }

    @Override
    public ReplicationStatus getReplicationStatus() {
        log.debug("Querying replication status");
        List<ReplicationStatus.ReplicaInfo> replicas = jdbcTemplate.query("""
                SELECT client_addr::text, state,
                       pg_wal_lsn_diff(sent_lsn, replay_lsn) AS lag_bytes,
                       EXTRACT(EPOCH FROM replay_lag) AS lag_seconds
                FROM pg_stat_replication
                """,
                (rs, rowNum) -> new ReplicationStatus.ReplicaInfo(
                        rs.getString("client_addr"),
                        rs.getString("state"),
                        rs.getLong("lag_bytes"),
                        rs.getObject("lag_seconds") != null ? rs.getDouble("lag_seconds") : null
                ));
        return new ReplicationStatus(!replicas.isEmpty(), replicas);
    }

    @Override
    public QueryResult executeReadOnlyQuery(String sql, Map<String, Object> params) {
        log.info("Executing diagnostic read-only query: {}", sql.substring(0, Math.min(sql.length(), 100)));

        String normalized = sql.trim().toLowerCase();
        if (!normalized.startsWith("select") && !normalized.startsWith("with") && !normalized.startsWith("explain")) {
            throw new IllegalArgumentException("Only SELECT, WITH, and EXPLAIN queries are allowed");
        }

        long start = System.currentTimeMillis();

        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (ResultSet rs) -> {
            List<Map<String, Object>> result = new ArrayList<>();
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            if (columns.isEmpty()) {
                for (int i = 1; i <= colCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                }
            }

            int rowCount = 0;
            while (rs.next() && rowCount < properties.getMaxRowsPerQuery()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                result.add(row);
                rowCount++;
            }
            return result;
        });

        long elapsed = System.currentTimeMillis() - start;
        return new QueryResult(columns, rows != null ? rows : List.of(),
                rows != null ? rows.size() : 0, elapsed);
    }

    @Override
    public long getRowCount(String tableName, String whereClause) {
        String sql = "SELECT count(*) FROM " + sanitizeIdentifier(tableName);
        if (whereClause != null && !whereClause.isBlank()) {
            sql += " WHERE " + whereClause;
        }
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    @Override
    public List<Map<String, Object>> sampleRows(String tableName, String whereClause, int limit) {
        String sql = "SELECT * FROM " + sanitizeIdentifier(tableName);
        if (whereClause != null && !whereClause.isBlank()) {
            sql += " WHERE " + whereClause;
        }
        sql += " LIMIT " + Math.min(limit, properties.getMaxRowsPerQuery());
        return jdbcTemplate.queryForList(sql);
    }

    // --- SrePlugin methods ---

    @Override
    public String getPluginId() { return "postgres"; }

    @Override
    public String getDisplayName() { return "PostgreSQL Diagnostics"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public PluginType getType() { return PluginType.DATABASE; }

    @Override
    public PluginConfiguration getDefaultConfiguration() {
        return new PluginConfiguration(List.of(
                new ConfigField("jdbcUrl", "JDBC URL", "PostgreSQL JDBC URL for diagnostics target", ConfigFieldType.URL, true, ""),
                new ConfigField("username", "Username", "Database username", ConfigFieldType.STRING, true, ""),
                new ConfigField("password", "Password", "Database password", ConfigFieldType.SECRET, true, "")
        ));
    }

    @Override
    public void initialize(Map<String, String> config) {
        log.info("Initializing PostgreSQL diagnostics plugin");
    }

    @Override
    public boolean validateConnection() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            log.error("PostgreSQL connection validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isEnabled() { return properties.isEnabled(); }

    private String sanitizeIdentifier(String identifier) {
        return identifier.replaceAll("[^a-zA-Z0-9_.]", "");
    }
}
