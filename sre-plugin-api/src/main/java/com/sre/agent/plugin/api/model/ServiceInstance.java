package com.sre.agent.plugin.api.model;

import java.time.Instant;
import java.util.Map;

public record ServiceInstance(
        String instanceId,
        String serviceName,
        String status,
        String healthStatus,
        int restartCount,
        Instant lastStarted,
        Map<String, String> metadata
) {}
