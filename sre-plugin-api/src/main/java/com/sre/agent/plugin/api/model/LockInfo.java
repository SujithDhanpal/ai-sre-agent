package com.sre.agent.plugin.api.model;

public record LockInfo(
        int blockedPid,
        String blockedQuery,
        int blockingPid,
        String blockingQuery,
        String lockType,
        String relation
) {}
