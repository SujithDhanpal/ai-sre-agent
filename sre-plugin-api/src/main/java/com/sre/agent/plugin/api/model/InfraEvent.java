package com.sre.agent.plugin.api.model;

import java.time.Instant;

public record InfraEvent(
        String eventId,
        String eventType,
        String serviceName,
        String description,
        String severity,
        Instant timestamp
) {}
