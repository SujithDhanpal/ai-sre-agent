package com.sre.agent.plugin.api.model;

import java.time.Instant;

public record MetricQuery(
        String query,
        Instant startTime,
        Instant endTime,
        String step
) {
    public MetricQuery {
        if (step == null || step.isBlank()) step = "60s";
    }
}
