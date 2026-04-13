package com.sre.agent.plugin.api.model;

public record ConnectionPoolStatus(
        int totalConnections,
        int activeConnections,
        int idleConnections,
        int idleInTransactionConnections,
        int waitingOnLockConnections,
        int maxConnections
) {}
