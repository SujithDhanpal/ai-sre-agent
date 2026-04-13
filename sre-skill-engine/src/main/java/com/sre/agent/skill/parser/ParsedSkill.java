package com.sre.agent.skill.parser;

import java.util.List;
import java.util.Map;

public record ParsedSkill(
        SkillMetadata metadata,
        List<SkillBlock> blocks
) {
    public record SkillMetadata(
            String id,
            String name,
            String description,
            String version,
            String author,
            String source,
            List<String> requiredPlugins,
            List<String> requiredPermissions,
            List<SkillInput> inputs,
            Map<String, Object> outputSchema
    ) {}

    public record SkillInput(
            String name,
            String type,
            String defaultValue
    ) {}
}
