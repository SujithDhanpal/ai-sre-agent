package com.sre.agent.plugin.api.model;

import java.util.Map;

public record HealthStatus(
        boolean healthy,
        String message,
        Map<String, Object> details
) {
    public static HealthStatus up() {
        return new HealthStatus(true, "OK", Map.of());
    }

    public static HealthStatus down(String reason) {
        return new HealthStatus(false, reason, Map.of());
    }
}
