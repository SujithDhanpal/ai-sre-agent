package com.sre.agent.plugin.api.model;

import java.util.List;
import java.util.Map;

public record CreatePrRequest(
        String repo,
        String title,
        String body,
        String headBranch,
        String baseBranch,
        List<String> labels,
        Map<String, String> fileChanges
) {}
