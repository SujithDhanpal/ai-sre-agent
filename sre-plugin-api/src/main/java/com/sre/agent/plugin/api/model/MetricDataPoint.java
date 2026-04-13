package com.sre.agent.plugin.api.model;

import java.time.Instant;
import java.util.Map;

public record MetricDataPoint(
        String metricName,
        Instant timestamp,
        double value,
        Map<String, String> labels
) {}
