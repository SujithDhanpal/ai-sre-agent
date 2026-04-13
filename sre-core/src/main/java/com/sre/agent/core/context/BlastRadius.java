package com.sre.agent.core.context;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.*;
import com.sre.agent.plugin.api.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Assesses blast radius — how bad is the incident?
 * Not an LLM agent — just quick metric/API queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlastRadius {

    private final PluginRegistry pluginRegistry;

    public String assess(List<String> services) {
        var sb = new StringBuilder();
        Instant since = Instant.now().minusSeconds(1800);

        // Error rate from Prometheus
        pluginRegistry.getPlugin("default", PluginType.MONITORING, MonitoringPlugin.class)
                .ifPresent(monitoring -> {
                    for (String service : services) {
                        try {
                            String query = "rate(http_requests_total{service_name=\"" + service + "\",status=~\"5..\"}[5m])";
                            List<MetricDataPoint> points = monitoring.fetchMetrics(
                                    new MetricQuery(query, since, Instant.now(), "60s"));
                            if (!points.isEmpty()) {
                                double latest = points.get(points.size() - 1).value();
                                sb.append("- **").append(service).append(" error rate**: ")
                                        .append(String.format("%.2f%%", latest * 100)).append("\n");
                            }
                        } catch (Exception e) {
                            log.debug("Error rate check failed for {}: {}", service, e.getMessage());
                        }
                    }
                });

        // Affected users from Sentry
        pluginRegistry.getPlugin("default", PluginType.ERROR_TRACKING, ErrorTrackingPlugin.class)
                .ifPresent(sentry -> {
                    for (String service : services) {
                        try {
                            List<ErrorGroup> errors = sentry.getRecentErrorGroups(service, since);
                            long totalUsers = errors.stream().mapToLong(ErrorGroup::userCount).sum();
                            if (totalUsers > 0) {
                                sb.append("- **").append(service).append(" affected users**: ")
                                        .append(totalUsers).append("\n");
                            }
                        } catch (Exception e) {
                            log.debug("Sentry check failed for {}: {}", service, e.getMessage());
                        }
                    }
                });

        // DB connection pool
        pluginRegistry.getPlugin("default", PluginType.DATABASE, DatabasePlugin.class)
                .ifPresent(db -> {
                    try {
                        ConnectionPoolStatus pool = db.getConnectionPoolStatus();
                        double utilization = pool.maxConnections() > 0
                                ? (double) pool.activeConnections() / pool.maxConnections() * 100 : 0;
                        sb.append("- **DB connection pool**: ")
                                .append(pool.activeConnections()).append("/").append(pool.maxConnections())
                                .append(" (").append(String.format("%.0f%%", utilization)).append(")\n");
                    } catch (Exception e) {
                        log.debug("DB pool check failed: {}", e.getMessage());
                    }
                });

        return sb.isEmpty() ? "No blast radius data available." : sb.toString();
    }
}
