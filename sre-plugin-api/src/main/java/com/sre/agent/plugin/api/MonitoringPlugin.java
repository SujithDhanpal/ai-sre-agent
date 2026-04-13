package com.sre.agent.plugin.api;

import com.sre.agent.plugin.api.model.*;

import java.util.List;

public interface MonitoringPlugin extends SrePlugin {

    List<LogEntry> fetchLogs(LogQuery query);

    List<MetricDataPoint> fetchMetrics(MetricQuery query);

    List<String> getActiveAlertNames(String tenantId);

    HealthStatus checkHealth();

    /** Get available log label names — agent calls this first to discover the schema */
    default List<String> getLogLabels() { return List.of(); }

    /** Get values for a specific label — e.g., all service names */
    default List<String> getLogLabelValues(String labelName) { return List.of(); }

    /** Execute a raw LogQL query string — the LLM writes the query, not Java code */
    default List<LogEntry> fetchLogsRaw(String logqlQuery, java.time.Instant start, java.time.Instant end, int limit) {
        return List.of();
    }
}
