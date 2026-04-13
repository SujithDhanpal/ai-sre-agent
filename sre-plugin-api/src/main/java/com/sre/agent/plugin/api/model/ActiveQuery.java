package com.sre.agent.plugin.api.model;

import java.time.Duration;

public record ActiveQuery(
        int pid,
        String query,
        String state,
        Duration duration,
        String waitEventType,
        String clientAddr
) {}
