package com.sre.agent.plugin.sentry;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.ErrorTrackingPlugin;
import com.sre.agent.plugin.api.PluginConfiguration;
import com.sre.agent.plugin.api.PluginConfiguration.ConfigField;
import com.sre.agent.plugin.api.PluginConfiguration.ConfigFieldType;
import com.sre.agent.plugin.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.*;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "sre.plugins.sentry.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class SentryErrorTrackingPlugin implements ErrorTrackingPlugin {

    private final WebClient sentryClient;
    private final SentryPluginProperties properties;

    public SentryErrorTrackingPlugin(WebClient.Builder webClientBuilder, SentryPluginProperties properties) {
        this.properties = properties;
        this.sentryClient = webClientBuilder.clone()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + (properties.getAuthToken() != null ? properties.getAuthToken() : ""))
                .build();
    }

    @Override
    public List<ErrorGroup> getRecentErrorGroups(String service, Instant since) {
        log.debug("Fetching recent error groups from Sentry: service={}", service);
        try {
            List<Map<String, Object>> issues = sentryClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/projects/{org}/{project}/issues/")
                            .queryParam("query", "is:unresolved")
                            .queryParam("sort", "freq")
                            .queryParam("limit", 25)
                            .build(properties.getOrganization(), service))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            if (issues == null) return List.of();

            return issues.stream()
                    .map(this::mapToErrorGroup)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch Sentry error groups: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ErrorGroupDetail getErrorGroupDetail(String errorGroupId) {
        log.debug("Fetching error group detail from Sentry: groupId={}", errorGroupId);
        try {
            Map<String, Object> event = sentryClient.get()
                    .uri("/issues/{issueId}/events/latest/", errorGroupId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (event == null) return null;

            List<ErrorGroupDetail.StackFrame> frames = new ArrayList<>();
            Map<String, Object> entries = (Map<String, Object>) ((List<?>) event.getOrDefault("entries", List.of()))
                    .stream()
                    .filter(e -> "exception".equals(((Map<String, Object>) e).get("type")))
                    .findFirst().orElse(null);

            String exceptionType = "";
            String exceptionValue = "";

            if (entries != null) {
                Map<String, Object> data = (Map<String, Object>) entries.get("data");
                if (data != null) {
                    List<Map<String, Object>> values = (List<Map<String, Object>>) data.get("values");
                    if (values != null && !values.isEmpty()) {
                        Map<String, Object> exc = values.get(0);
                        exceptionType = (String) exc.getOrDefault("type", "");
                        exceptionValue = (String) exc.getOrDefault("value", "");

                        Map<String, Object> stacktrace = (Map<String, Object>) exc.get("stacktrace");
                        if (stacktrace != null) {
                            List<Map<String, Object>> framesList = (List<Map<String, Object>>) stacktrace.get("frames");
                            if (framesList != null) {
                                frames = framesList.stream()
                                        .map(f -> new ErrorGroupDetail.StackFrame(
                                                (String) f.getOrDefault("filename", ""),
                                                (String) f.getOrDefault("function", ""),
                                                f.get("lineNo") != null ? ((Number) f.get("lineNo")).intValue() : 0,
                                                (String) f.getOrDefault("context_line", ""),
                                                (String) f.getOrDefault("module", "")
                                        ))
                                        .collect(Collectors.toList());
                            }
                        }
                    }
                }
            }

            Map<String, String> tags = new HashMap<>();
            List<List<String>> tagsList = (List<List<String>>) event.getOrDefault("tags", List.of());
            for (List<String> tag : tagsList) {
                if (tag.size() >= 2) tags.put(tag.get(0), tag.get(1));
            }

            return new ErrorGroupDetail(
                    errorGroupId,
                    (String) event.getOrDefault("title", ""),
                    exceptionType,
                    exceptionValue,
                    frames,
                    List.of(),
                    tags,
                    Map.of()
            );
        } catch (Exception e) {
            log.error("Failed to fetch Sentry error detail: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<ErrorGroup> getNewErrors(String service, Instant since) {
        log.debug("Fetching new errors from Sentry: service={}", service);
        try {
            List<Map<String, Object>> issues = sentryClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/projects/{org}/{project}/issues/")
                            .queryParam("query", "is:unresolved firstSeen:>" + DateTimeFormatter.ISO_INSTANT.format(since))
                            .queryParam("sort", "date")
                            .queryParam("limit", 25)
                            .build(properties.getOrganization(), service))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            if (issues == null) return List.of();
            return issues.stream().map(this::mapToErrorGroup).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch new Sentry errors: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<ErrorGroup> getRegressions(String service, Instant since) {
        log.debug("Fetching regression errors from Sentry: service={}", service);
        try {
            List<Map<String, Object>> issues = sentryClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/projects/{org}/{project}/issues/")
                            .queryParam("query", "is:regressed")
                            .queryParam("sort", "date")
                            .queryParam("limit", 25)
                            .build(properties.getOrganization(), service))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            if (issues == null) return List.of();
            return issues.stream().map(this::mapToErrorGroup).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch Sentry regressions: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public ReleaseHealth getReleaseHealth(String service, String version) {
        log.debug("Fetching release health from Sentry: service={}, version={}", service, version);
        try {
            Map<String, Object> release = sentryClient.get()
                    .uri("/organizations/{org}/releases/{version}/", properties.getOrganization(), version)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (release == null) return null;
            return mapToReleaseHealth(release);
        } catch (Exception e) {
            log.error("Failed to fetch Sentry release health: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<ReleaseHealth> getRecentReleases(String service, int limit) {
        log.debug("Fetching recent releases from Sentry: service={}", service);
        try {
            List<Map<String, Object>> releases = sentryClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/organizations/{org}/releases/")
                            .queryParam("project", service)
                            .queryParam("per_page", limit)
                            .build(properties.getOrganization()))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            if (releases == null) return List.of();
            return releases.stream().map(this::mapToReleaseHealth).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch Sentry releases: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // --- SrePlugin methods ---

    @Override
    public String getPluginId() { return "sentry"; }

    @Override
    public String getDisplayName() { return "Sentry Error Tracking"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public PluginType getType() { return PluginType.ERROR_TRACKING; }

    @Override
    public PluginConfiguration getDefaultConfiguration() {
        return new PluginConfiguration(List.of(
                new ConfigField("baseUrl", "Sentry URL", "Sentry API base URL", ConfigFieldType.URL, true, "https://sentry.io/api/0"),
                new ConfigField("authToken", "Auth Token", "Sentry authentication token", ConfigFieldType.SECRET, true, ""),
                new ConfigField("organization", "Organization", "Sentry organization slug", ConfigFieldType.STRING, true, "")
        ));
    }

    @Override
    public void initialize(Map<String, String> config) {
        log.info("Initializing Sentry plugin for org: {}", properties.getOrganization());
    }

    @Override
    public boolean validateConnection() {
        try {
            sentryClient.get().uri("/").retrieve().toBodilessEntity().block();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isEnabled() { return properties.isEnabled(); }

    // --- Mappers ---

    @SuppressWarnings("unchecked")
    private ErrorGroup mapToErrorGroup(Map<String, Object> issue) {
        return new ErrorGroup(
                String.valueOf(issue.get("id")),
                (String) issue.getOrDefault("title", ""),
                (String) issue.getOrDefault("culprit", ""),
                issue.get("count") != null ? Long.parseLong(issue.get("count").toString()) : 0,
                issue.get("userCount") != null ? ((Number) issue.get("userCount")).longValue() : 0,
                parseInstant((String) issue.get("firstSeen")),
                parseInstant((String) issue.get("lastSeen")),
                (String) issue.getOrDefault("level", "error"),
                List.of()
        );
    }

    @SuppressWarnings("unchecked")
    private ReleaseHealth mapToReleaseHealth(Map<String, Object> release) {
        return new ReleaseHealth(
                (String) release.getOrDefault("version", ""),
                release.get("crashFreeSessionsPercent") != null ?
                        ((Number) release.get("crashFreeSessionsPercent")).doubleValue() : 0,
                release.get("crashFreeUsersPercent") != null ?
                        ((Number) release.get("crashFreeUsersPercent")).doubleValue() : 0,
                0, 0, 0,
                parseInstant((String) release.get("dateCreated"))
        );
    }

    private Instant parseInstant(String dateStr) {
        if (dateStr == null) return Instant.now();
        try {
            return Instant.parse(dateStr);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
