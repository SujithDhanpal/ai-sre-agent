package com.sre.agent.plugin.api.model;

import java.time.Instant;
import java.util.List;

public record CommitInfo(
        String sha,
        String message,
        String author,
        Instant timestamp,
        List<String> filesChanged
) {}
