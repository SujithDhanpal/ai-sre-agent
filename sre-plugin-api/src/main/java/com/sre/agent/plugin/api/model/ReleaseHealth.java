package com.sre.agent.plugin.api.model;

import java.time.Instant;

public record ReleaseHealth(
        String version,
        double crashFreeSessionsPercent,
        double crashFreeUsersPercent,
        long totalSessions,
        long errorCount,
        int newIssues,
        Instant deployedAt
) {}
