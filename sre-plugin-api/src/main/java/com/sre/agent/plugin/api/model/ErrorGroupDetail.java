package com.sre.agent.plugin.api.model;

import java.util.List;
import java.util.Map;

public record ErrorGroupDetail(
        String groupId,
        String title,
        String type,
        String value,
        List<StackFrame> stackTrace,
        List<Breadcrumb> breadcrumbs,
        Map<String, String> tags,
        Map<String, Object> context
) {
    public record StackFrame(
            String filename,
            String function,
            int lineNo,
            String context,
            String module
    ) {}

    public record Breadcrumb(
            String category,
            String message,
            String level,
            String timestamp,
            Map<String, Object> data
    ) {}
}
