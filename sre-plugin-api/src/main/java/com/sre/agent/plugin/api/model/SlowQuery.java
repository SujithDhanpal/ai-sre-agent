package com.sre.agent.plugin.api.model;

public record SlowQuery(
        String query,
        long calls,
        double meanExecTimeMs,
        double totalExecTimeMs,
        long rows
) {}
