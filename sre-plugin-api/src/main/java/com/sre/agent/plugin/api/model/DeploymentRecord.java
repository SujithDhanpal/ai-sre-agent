package com.sre.agent.plugin.api.model;

import java.time.Instant;

public record DeploymentRecord(
        String deploymentId,
        String serviceName,
        String version,
        String commitSha,
        String deployedBy,
        String status,
        Instant deployedAt
) {}
