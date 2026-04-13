package com.sre.agent.plugin.api.model;

import java.time.Instant;
import java.util.Map;

public record LogEntry(
        Instant timestamp,
        String level,
        String message,
        String service,
        String traceId,
        Map<String, String> labels
) {}
