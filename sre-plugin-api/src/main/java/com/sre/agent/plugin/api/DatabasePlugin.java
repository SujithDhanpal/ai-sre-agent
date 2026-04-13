package com.sre.agent.plugin.api;

import com.sre.agent.plugin.api.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface DatabasePlugin extends SrePlugin {

    // Connection Health
    ConnectionPoolStatus getConnectionPoolStatus();

    List<ActiveQuery> getActiveQueries();

    // Performance
    List<SlowQuery> getSlowQueries(Instant since, int limit);

    List<LockInfo> getCurrentLocks();

    // Replication
    ReplicationStatus getReplicationStatus();

    // Diagnostics — read-only investigative queries
    QueryResult executeReadOnlyQuery(String sql, Map<String, Object> params);

    long getRowCount(String tableName, String whereClause);

    List<Map<String, Object>> sampleRows(String tableName, String whereClause, int limit);
}
