package com.sre.agent.plugin.api.model;

import java.time.Instant;
import java.util.List;

public record ErrorGroup(
        String groupId,
        String title,
        String culprit,
        long eventCount,
        long userCount,
        Instant firstSeen,
        Instant lastSeen,
        String level,
        List<String> tags
) {}
