package com.sre.agent.skill.executor;

import com.sre.agent.skill.parser.SkillBlock;

import java.util.Map;

public interface SkillBlockExecutor {

    SkillBlock.BlockType supportedType();

    BlockResult execute(SkillBlock block, Map<String, String> variables, ExecutionContext context);

    record BlockResult(
            boolean success,
            String output,
            String error,
            long executionTimeMs
    ) {
        public static BlockResult success(String output, long ms) {
            return new BlockResult(true, output, null, ms);
        }

        public static BlockResult failure(String error, long ms) {
            return new BlockResult(false, null, error, ms);
        }
    }

    record ExecutionContext(
            String tenantId,
            String incidentId,
            com.sre.agent.plugin.api.PluginRegistry pluginRegistry
    ) {}
}
