package com.sre.agent.plugin.api.model;

import java.time.Instant;
import java.util.Map;

public record CloudMetricQuery(
        String namespace,
        String metricName,
        Map<String, String> dimensions,
        Instant startTime,
        Instant endTime,
        int periodSeconds
) {
    public CloudMetricQuery {
        if (periodSeconds <= 0) periodSeconds = 60;
    }
}
