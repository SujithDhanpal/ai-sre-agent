package com.sre.agent.framework.tools;

import com.sre.agent.commons.InvestigationEventStream;
import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.ErrorTrackingPlugin;
import com.sre.agent.plugin.api.MonitoringPlugin;
import com.sre.agent.plugin.api.PluginRegistry;
import com.sre.agent.plugin.api.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class LogAnalysisTools {

    private final PluginRegistry pluginRegistry;
    private final String tenantId;

    @Tool(description = "Discover what log labels are available in the logging system. Call this FIRST before querying logs to learn the label schema - whether services use 'service', 'service_name', 'app', 'container', etc. This is essential since every setup uses different labels.")
    public String discoverLogLabels() {
        log.info("[Tool] discoverLogLabels");
        InvestigationEventStream.toolCall("discoverLogLabels", "Discovering available log labels");
        return pluginRegistry.getPlugin(tenantId, PluginType.MONITORING, MonitoringPlugin.class)
                .map(monitoring -> {
                    List<String> labels = monitoring.getLogLabels();
                    if (labels.isEmpty()) return "No labels found.";
                    return "Available log labels:\n" + labels.stream().map(l -> "- " + l).collect(Collectors.joining("\n"));
                })
                .orElse("Monitoring plugin not available.");
    }

    @Tool(description = "Get all values for a specific log label. Use after discoverLogLabels to find available service names, namespaces, etc. Example: getLabelValues('service_name') returns all services.")
    public String getLabelValues(
            @ToolParam(description = "Label name from discoverLogLabels, e.g. 'service_name', 'namespace', 'container'") String labelName) {
        log.info("[Tool] getLabelValues: label={}", labelName);
        InvestigationEventStream.toolCall("getLabelValues", labelName);
        return pluginRegistry.getPlugin(tenantId, PluginType.MONITORING, MonitoringPlugin.class)
                .map(monitoring -> {
                    List<String> values = monitoring.getLogLabelValues(labelName);
                    if (values.isEmpty()) return "No values found for label: " + labelName;
                    return "Values for '" + labelName + "':\n" + values.stream().map(v -> "- " + v).collect(Collectors.joining("\n"));
                })
                .orElse("Monitoring plugin not available.");
    }

    @Tool(description = "Query logs using a raw LogQL query. You MUST include a stream selector with at least one label — never use empty '{}'. Examples: '{service_name=\"esd\"} |= \"error\"', '{service_name=~\"esd.*\"} |= \"b24cb14f\"', '{namespace=\"atomic-apps\"} |= \"Exception\"'. ALWAYS call discoverLogLabels and getLabelValues first to know the correct label names.")
    public String queryLogs(
            @ToolParam(description = "Full LogQL query with stream selector. MUST include at least one label like service_name or namespace. Example: '{service_name=\"esd\"} |= \"ERROR\"'") String logqlQuery,
            @ToolParam(description = "How far back to look, e.g. '30m', '1h', '6h', '24h', '7d'") String timeRange) {
        log.info("[Tool] queryLogs: query={}, range={}", logqlQuery, timeRange);
        InvestigationEventStream.toolCall("queryLogs", logqlQuery + " (" + timeRange + ")");

        // Reject empty stream selectors — they cause 502 on Loki due to scanning all streams
        if (logqlQuery.startsWith("{}") || logqlQuery.startsWith("{ }")) {
            return "ERROR: Empty stream selector '{}' is not allowed — it would scan all logs and timeout. You MUST include at least one label filter like {service_name=\"esd\"} or {namespace=\"atomic-apps\"}. Call getLabelValues('service_name') to find available services.";
        }

        return pluginRegistry.getPlugin(tenantId, PluginType.MONITORING, MonitoringPlugin.class)
                .map(monitoring -> {
                    Instant end = Instant.now();
                    Instant start = end.minusSeconds(parseTimeRange(timeRange));
                    List<LogEntry> logs = monitoring.fetchLogsRaw(logqlQuery, start, end, 50);
                    if (logs.isEmpty()) return "No logs found for query: " + logqlQuery;
                    return "Found " + logs.size() + " log entries:\n\n" + logs.stream()
                            .map(l -> "[%s] %s %s".formatted(l.timestamp(), l.level(), l.message()))
                            .collect(Collectors.joining("\n"));
                })
                .orElse("Monitoring plugin not available.");
    }

    @Tool(description = "Query Prometheus metrics using PromQL. Use this to check error rates, latency, CPU, memory, or any custom metric.")
    public String queryMetrics(
            @ToolParam(description = "PromQL query, e.g. 'rate(http_requests_total{service=\"payment-service\",status=~\"5..\"}[5m])'") String promqlQuery,
            @ToolParam(description = "How far back to look, e.g. '30m', '1h'") String timeRange) {
        log.info("[Tool] queryMetrics: query={}, range={}", promqlQuery, timeRange);
        InvestigationEventStream.toolCall("queryMetrics", promqlQuery);
        return pluginRegistry.getPlugin(tenantId, PluginType.MONITORING, MonitoringPlugin.class)
                .map(monitoring -> {
                    Instant end = Instant.now();
                    Instant start = end.minusSeconds(parseTimeRange(timeRange));
                    List<MetricDataPoint> points = monitoring.fetchMetrics(
                            new MetricQuery(promqlQuery, start, end, "60s"));
                    if (points.isEmpty()) return "No metric data returned for this query.";
                    return points.stream()
                            .map(p -> "%s -> %.4f %s".formatted(p.timestamp(), p.value(),
                                    p.labels() != null ? p.labels().toString() : ""))
                            .collect(Collectors.joining("\n"));
                })
                .orElse("Monitoring plugin not available.");
    }

    @Tool(description = "Get recent error groups from Sentry for a service, sorted by frequency.")
    public String getRecentErrors(
            @ToolParam(description = "Service/project name in Sentry") String service,
            @ToolParam(description = "How far back to look, e.g. '30m', '1h', '24h'") String timeRange) {
        log.info("[Tool] getRecentErrors: service={}, range={}", service, timeRange);
        InvestigationEventStream.toolCall("getRecentErrors", service + " (" + timeRange + ")");
        return pluginRegistry.getPlugin(tenantId, PluginType.ERROR_TRACKING, ErrorTrackingPlugin.class)
                .map(sentry -> {
                    Instant since = Instant.now().minusSeconds(parseTimeRange(timeRange));
                    List<ErrorGroup> errors = sentry.getRecentErrorGroups(service, since);
                    if (errors.isEmpty()) return "No recent errors found in Sentry for " + service;
                    return errors.stream()
                            .map(e -> "- [%s] %s (count: %d, users: %d, first: %s, last: %s)".formatted(
                                    e.level(), e.title(), e.eventCount(), e.userCount(), e.firstSeen(), e.lastSeen()))
                            .collect(Collectors.joining("\n"));
                })
                .orElse("Error tracking plugin not available.");
    }

    @Tool(description = "Get the full stack trace and context for a specific Sentry error group.")
    public String getErrorDetail(
            @ToolParam(description = "Sentry error group ID") String errorGroupId) {
        log.info("[Tool] getErrorDetail: groupId={}", errorGroupId);
        InvestigationEventStream.toolCall("getErrorDetail", errorGroupId);
        return pluginRegistry.getPlugin(tenantId, PluginType.ERROR_TRACKING, ErrorTrackingPlugin.class)
                .map(sentry -> {
                    ErrorGroupDetail detail = sentry.getErrorGroupDetail(errorGroupId);
                    if (detail == null) return "Error group not found: " + errorGroupId;
                    var sb = new StringBuilder();
                    sb.append("Exception: ").append(detail.type()).append(": ").append(detail.value()).append("\n\n");
                    sb.append("Stack Trace:\n");
                    for (var frame : detail.stackTrace()) {
                        sb.append("  at %s.%s(%s:%d)\n".formatted(frame.module(), frame.function(), frame.filename(), frame.lineNo()));
                    }
                    if (detail.tags() != null && !detail.tags().isEmpty()) sb.append("\nTags: ").append(detail.tags());
                    return sb.toString();
                })
                .orElse("Error tracking plugin not available.");
    }

    private long parseTimeRange(String range) {
        if (range == null || range.isBlank()) return 1800;
        String num = range.replaceAll("[^0-9]", "");
        long value = num.isEmpty() ? 30 : Long.parseLong(num);
        if (range.contains("h")) return value * 3600;
        if (range.contains("d")) return value * 86400;
        return value * 60;
    }
}
