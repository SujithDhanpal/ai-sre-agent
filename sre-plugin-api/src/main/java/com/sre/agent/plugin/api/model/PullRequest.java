package com.sre.agent.plugin.api.model;

import java.util.List;

public record PullRequest(
        long number,
        String url,
        String title,
        String body,
        String state,
        String headBranch,
        String baseBranch,
        List<String> labels
) {}
