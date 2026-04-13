package com.sre.agent.plugin.api.model;

import java.time.Instant;
import java.util.Map;

public record LogQuery(
        String service,
        Instant startTime,
        Instant endTime,
        String query,
        Map<String, String> labels,
        int limit
) {
    public LogQuery {
        if (limit <= 0) limit = 500;
    }
}
