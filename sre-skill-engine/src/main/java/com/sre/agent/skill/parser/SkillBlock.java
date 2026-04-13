package com.sre.agent.skill.parser;

import java.util.Map;

public record SkillBlock(
        int stepNumber,
        String stepTitle,
        BlockType blockType,
        String code,
        Map<String, String> annotations
) {
    public enum BlockType {
        SQL,
        BASH,
        PYTHON,
        PROMQL,
        PROMPT,
        HTTP,
        SKILL
    }

    public int timeoutSeconds() {
        String timeout = annotations.getOrDefault("timeout", "30s");
        return parseTimeout(timeout);
    }

    public boolean isSandboxed() {
        return !"false".equalsIgnoreCase(annotations.getOrDefault("sandbox", "true"));
    }

    public String networkMode() {
        return annotations.getOrDefault("network", "none");
    }

    public String onFailure() {
        return annotations.getOrDefault("on_failure", "continue");
    }

    public String outputAs() {
        return annotations.getOrDefault("output_as", "step" + stepNumber);
    }

    public boolean requiresApproval() {
        return "true".equalsIgnoreCase(annotations.getOrDefault("requires_approval", "false"));
    }

    public String plugin() {
        return annotations.getOrDefault("plugin", null);
    }

    private static int parseTimeout(String timeout) {
        if (timeout.endsWith("s")) return Integer.parseInt(timeout.replace("s", ""));
        if (timeout.endsWith("m")) return Integer.parseInt(timeout.replace("m", "")) * 60;
        return 30;
    }
}
