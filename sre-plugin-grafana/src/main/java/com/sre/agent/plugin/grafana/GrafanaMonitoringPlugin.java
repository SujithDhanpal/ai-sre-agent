package com.sre.agent.plugin.grafana;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.MonitoringPlugin;
import com.sre.agent.plugin.api.PluginConfiguration;
import com.sre.agent.plugin.api.PluginConfiguration.ConfigField;
import com.sre.agent.plugin.api.PluginConfiguration.ConfigFieldType;
import com.sre.agent.plugin.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "sre.plugins.grafana.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class GrafanaMonitoringPlugin implements MonitoringPlugin {

    private final WebClient lokiClient;
    private final WebClient prometheusClient;
    private final GrafanaPluginProperties properties;

    public GrafanaMonitoringPlugin(WebClient.Builder webClientBuilder, GrafanaPluginProperties properties) {
        this.properties = properties;

        var builder = webClientBuilder.clone();
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder = builder.defaultHeader("Authorization", "Bearer " + properties.getApiKey());
        }

        this.lokiClient = builder.clone()
                .baseUrl(properties.getLoki().getBaseUrl())
                .build();

        this.prometheusClient = builder.clone()
                .baseUrl(properties.getPrometheus().getBaseUrl())
                .build();
    }

    @Override
    public List<LogEntry> fetchLogs(LogQuery query) {
        log.debug("Fetching logs from Loki: service={}, query={}", query.service(), query.query());

        String lokiQuery = buildLokiQuery(query);
        long startNanos = query.startTime().toEpochMilli() * 1_000_000L;
        long endNanos = query.endTime().toEpochMilli() * 1_000_000L;

        try {
            Map<String, Object> response = lokiClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/loki/api/v1/query_range")
                            .queryParam("query", lokiQuery)
                            .queryParam("start", startNanos)
                            .queryParam("end", endNanos)
                            .queryParam("limit", query.limit())
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            return parseLokiResponse(response);
        } catch (Exception e) {
            log.error("Failed to fetch logs from Loki: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<MetricDataPoint> fetchMetrics(MetricQuery query) {
        log.debug("Fetching metrics from Prometheus: query={}", query.query());

        try {
            Map<String, Object> response = prometheusClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/query_range")
                            .queryParam("query", query.query())
                            .queryParam("start", query.startTime().getEpochSecond())
                            .queryParam("end", query.endTime().getEpochSecond())
                            .queryParam("step", query.step())
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            return parsePrometheusResponse(response);
        } catch (Exception e) {
            log.error("Failed to fetch metrics from Prometheus: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<String> getActiveAlertNames(String tenantId) {
        try {
            Map<String, Object> response = prometheusClient.get()
                    .uri("/api/v1/alerts")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            return parseAlertNames(response);
        } catch (Exception e) {
            log.error("Failed to fetch active alerts: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public HealthStatus checkHealth() {
        try {
            lokiClient.get().uri("/loki/api/v1/labels").retrieve().toBodilessEntity().block();
            return HealthStatus.up();
        } catch (Exception e) {
            return HealthStatus.down("Grafana health check failed: " + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getLogLabels() {
        try {
            Map<String, Object> response = lokiClient.get()
                    .uri("/loki/api/v1/labels")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
            if (response != null && "success".equals(response.get("status"))) {
                return (List<String>) response.get("data");
            }
            return List.of();
        } catch (Exception e) {
            log.error("Failed to fetch Loki labels: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getLogLabelValues(String labelName) {
        try {
            Map<String, Object> response = lokiClient.get()
                    .uri("/loki/api/v1/label/{label}/values", labelName)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
            if (response != null && "success".equals(response.get("status"))) {
                return (List<String>) response.get("data");
            }
            return List.of();
        } catch (Exception e) {
            log.error("Failed to fetch Loki label values for {}: {}", labelName, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<LogEntry> fetchLogsRaw(String logqlQuery, java.time.Instant start, java.time.Instant end, int limit) {
        log.debug("Fetching logs with raw LogQL: {}", logqlQuery);
        long startNanos = start.toEpochMilli() * 1_000_000L;
        long endNanos = end.toEpochMilli() * 1_000_000L;

        try {
            // Build the full URI with proper encoding — must use URI.create() to avoid
            // WebClient's UriBuilder re-encoding the already-encoded query parameter
            String baseUrl = properties.getLoki().getBaseUrl();
            // Use %20 for spaces (not +) — Grafana's Loki proxy requires %20 encoding
            String encodedQuery = java.net.URLEncoder.encode(logqlQuery, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
            String fullUrl = baseUrl + "/loki/api/v1/query_range?query=" + encodedQuery
                    + "&start=" + startNanos + "&end=" + endNanos + "&limit=" + limit;

            log.info("Loki raw query: {}", fullUrl.substring(0, Math.min(fullUrl.length(), 200)));

            // Use RestTemplate with URI.create() to prevent any URL re-encoding
            var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
            factory.setReadTimeout(java.time.Duration.ofSeconds(120));
            var restTemplate = new org.springframework.web.client.RestTemplate(factory);
            var headers = new org.springframework.http.HttpHeaders();
            if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                headers.set("Authorization", "Bearer " + properties.getApiKey());
            }
            var requestEntity = new org.springframework.http.HttpEntity<>(headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.exchange(
                    java.net.URI.create(fullUrl),
                    org.springframework.http.HttpMethod.GET,
                    requestEntity,
                    Map.class
            ).getBody();

            return parseLokiResponse(response);
        } catch (Exception e) {
            log.error("Failed to fetch logs with raw LogQL: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // --- SrePlugin methods ---

    @Override
    public String getPluginId() {
        return "grafana";
    }

    @Override
    public String getDisplayName() {
        return "Grafana (Loki + Prometheus)";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public PluginType getType() {
        return PluginType.MONITORING;
    }

    @Override
    public PluginConfiguration getDefaultConfiguration() {
        return new PluginConfiguration(List.of(
                new ConfigField("loki.baseUrl", "Loki URL", "Loki base URL", ConfigFieldType.URL, true, "http://localhost:3100"),
                new ConfigField("prometheus.baseUrl", "Prometheus URL", "Prometheus base URL", ConfigFieldType.URL, true, "http://localhost:9090"),
                new ConfigField("apiKey", "API Key", "Grafana API key (optional)", ConfigFieldType.SECRET, false, "")
        ));
    }

    @Override
    public void initialize(Map<String, String> config) {
        log.info("Initializing Grafana plugin with config keys: {}", config.keySet());
    }

    @Override
    public boolean validateConnection() {
        return checkHealth().healthy();
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    // --- Private helpers ---

    private String buildLokiQuery(LogQuery query) {
        StringBuilder lokiQuery = new StringBuilder("{");
        if (query.service() != null) {
            lokiQuery.append("service_name=\"").append(query.service()).append("\"");
        }
        if (query.labels() != null) {
            for (var entry : query.labels().entrySet()) {
                if (lokiQuery.length() > 1) lokiQuery.append(",");
                lokiQuery.append(entry.getKey()).append("=\"").append(entry.getValue()).append("\"");
            }
        }
        lokiQuery.append("}");

        if (query.query() != null && !query.query().isBlank()) {
            lokiQuery.append(" |= \"").append(query.query()).append("\"");
        }

        return lokiQuery.toString();
    }

    @SuppressWarnings("unchecked")
    private List<LogEntry> parseLokiResponse(Map<String, Object> response) {
        if (response == null) return List.of();
        List<LogEntry> entries = new ArrayList<>();

        try {
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return entries;

            List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("result");
            if (results == null) return entries;

            for (Map<String, Object> stream : results) {
                Map<String, String> labels = (Map<String, String>) stream.get("stream");
                List<List<String>> values = (List<List<String>>) stream.get("values");
                if (values == null) continue;

                for (List<String> value : values) {
                    long nanos = Long.parseLong(value.get(0));
                    String message = value.get(1);
                    entries.add(new LogEntry(
                            Instant.ofEpochMilli(nanos / 1_000_000),
                            labels.getOrDefault("level", "unknown"),
                            message,
                            labels.getOrDefault("service", "unknown"),
                            labels.get("traceId"),
                            labels
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Loki response: {}", e.getMessage());
        }

        return entries;
    }

    @SuppressWarnings("unchecked")
    private List<MetricDataPoint> parsePrometheusResponse(Map<String, Object> response) {
        if (response == null) return List.of();
        List<MetricDataPoint> points = new ArrayList<>();

        try {
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return points;

            List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("result");
            if (results == null) return points;

            for (Map<String, Object> result : results) {
                Map<String, String> metric = (Map<String, String>) result.get("metric");
                String metricName = metric.getOrDefault("__name__", "unknown");

                List<List<Object>> values = (List<List<Object>>) result.get("values");
                if (values == null) continue;

                for (List<Object> value : values) {
                    double timestamp = ((Number) value.get(0)).doubleValue();
                    double val = Double.parseDouble(value.get(1).toString());
                    points.add(new MetricDataPoint(
                            metricName,
                            Instant.ofEpochSecond((long) timestamp),
                            val,
                            metric
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Prometheus response: {}", e.getMessage());
        }

        return points;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseAlertNames(Map<String, Object> response) {
        if (response == null) return List.of();
        try {
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            List<Map<String, Object>> alerts = (List<Map<String, Object>>) data.get("alerts");
            if (alerts == null) return List.of();

            return alerts.stream()
                    .filter(a -> "firing".equals(a.get("state")))
                    .map(a -> {
                        Map<String, String> labels = (Map<String, String>) a.get("labels");
                        return labels.getOrDefault("alertname", "unknown");
                    })
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to parse alerts response: {}", e.getMessage());
            return List.of();
        }
    }
}
