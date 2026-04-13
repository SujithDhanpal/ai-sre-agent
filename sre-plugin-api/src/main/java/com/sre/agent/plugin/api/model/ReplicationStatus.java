package com.sre.agent.plugin.api.model;

import java.util.List;

public record ReplicationStatus(
        boolean hasReplicas,
        List<ReplicaInfo> replicas
) {
    public record ReplicaInfo(
            String clientAddr,
            String state,
            long lagBytes,
            Double lagSeconds
    ) {}
}
