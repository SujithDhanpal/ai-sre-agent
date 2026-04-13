package com.sre.agent.plugin.api.model;

import java.util.List;

public record TargetGroupHealth(
        String targetGroupName,
        int totalTargets,
        int healthyTargets,
        int unhealthyTargets,
        int drainingTargets,
        List<TargetStatus> targets
) {
    public record TargetStatus(
            String targetId,
            String state,
            String reason,
            String description
    ) {}
}
