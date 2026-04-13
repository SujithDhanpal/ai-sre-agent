package com.sre.agent.skill.engine;

import com.sre.agent.plugin.api.PluginRegistry;
import com.sre.agent.skill.executor.SkillBlockExecutor;
import com.sre.agent.skill.executor.SkillBlockExecutor.BlockResult;
import com.sre.agent.skill.executor.SkillBlockExecutor.ExecutionContext;
import com.sre.agent.skill.parser.ParsedSkill;
import com.sre.agent.skill.parser.SkillBlock;
import com.sre.agent.skill.parser.SkillMarkdownParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillEngine {

    private final SkillMarkdownParser parser;
    private final List<SkillBlockExecutor> blockExecutors;
    private final PluginRegistry pluginRegistry;

    private Map<SkillBlock.BlockType, SkillBlockExecutor> executorMap;

    private Map<SkillBlock.BlockType, SkillBlockExecutor> getExecutorMap() {
        if (executorMap == null) {
            executorMap = blockExecutors.stream()
                    .collect(Collectors.toMap(SkillBlockExecutor::supportedType, Function.identity()));
        }
        return executorMap;
    }

    public SkillExecutionResult execute(String markdownContent, Map<String, String> inputParams,
                                         String tenantId, String incidentId) {
        log.info("Executing skill for tenant={}, incident={}", tenantId, incidentId);
        long totalStart = System.currentTimeMillis();

        // 1. Parse the markdown
        ParsedSkill skill = parser.parse(markdownContent);
        log.info("Parsed skill '{}': {} blocks", skill.metadata().id(), skill.blocks().size());

        // 2. Initialize variables with inputs and defaults
        Map<String, String> variables = new LinkedHashMap<>(inputParams);
        for (ParsedSkill.SkillInput input : skill.metadata().inputs()) {
            variables.putIfAbsent(input.name(), input.defaultValue() != null ? input.defaultValue() : "");
        }

        // 3. Execute blocks sequentially
        ExecutionContext context = new ExecutionContext(tenantId, incidentId, pluginRegistry);
        List<StepResult> stepResults = new ArrayList<>();
        boolean allPassed = true;

        for (SkillBlock block : skill.blocks()) {
            log.debug("Executing skill block: step={}, type={}, title='{}'",
                    block.stepNumber(), block.blockType(), block.stepTitle());

            SkillBlockExecutor executor = getExecutorMap().get(block.blockType());
            if (executor == null) {
                log.warn("No executor for block type: {}. Skipping.", block.blockType());
                stepResults.add(new StepResult(
                        block.stepNumber(), block.stepTitle(), block.blockType().name(),
                        false, "No executor for block type: " + block.blockType(), null, 0));
                continue;
            }

            BlockResult result = executor.execute(block, variables, context);

            // Store result as a variable for subsequent blocks
            String outputKey = block.outputAs();
            variables.put(outputKey + ".result", result.success() ? result.output() : result.error());
            variables.put("step" + block.stepNumber() + ".result",
                    result.success() ? result.output() : result.error());

            stepResults.add(new StepResult(
                    block.stepNumber(), block.stepTitle(), block.blockType().name(),
                    result.success(), result.output(), result.error(), result.executionTimeMs()));

            if (!result.success()) {
                allPassed = false;
                if ("abort".equals(block.onFailure())) {
                    log.warn("Skill block failed with abort policy. Stopping execution.");
                    break;
                }
            }
        }

        long totalElapsed = System.currentTimeMillis() - totalStart;
        log.info("Skill '{}' execution complete: {} steps, allPassed={}, totalTime={}ms",
                skill.metadata().id(), stepResults.size(), allPassed, totalElapsed);

        return new SkillExecutionResult(
                skill.metadata().id(),
                skill.metadata().name(),
                allPassed,
                stepResults,
                variables,
                totalElapsed
        );
    }

    public record SkillExecutionResult(
            String skillId,
            String skillName,
            boolean allPassed,
            List<StepResult> stepResults,
            Map<String, String> variables,
            long totalExecutionTimeMs
    ) {
        public String summary() {
            var sb = new StringBuilder();
            sb.append("Skill: ").append(skillName).append(" (").append(skillId).append(")\n");
            sb.append("Status: ").append(allPassed ? "PASSED" : "PARTIAL/FAILED").append("\n");
            sb.append("Total time: ").append(totalExecutionTimeMs).append("ms\n\n");

            for (StepResult step : stepResults) {
                sb.append("Step ").append(step.stepNumber()).append(": ")
                        .append(step.stepTitle())
                        .append(" [").append(step.blockType()).append("] — ")
                        .append(step.success() ? "OK" : "FAILED")
                        .append(" (").append(step.executionTimeMs()).append("ms)\n");
                if (step.output() != null) {
                    String truncated = step.output().length() > 500
                            ? step.output().substring(0, 500) + "..."
                            : step.output();
                    sb.append(truncated).append("\n");
                }
                if (step.error() != null) {
                    sb.append("ERROR: ").append(step.error()).append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    public record StepResult(
            int stepNumber,
            String stepTitle,
            String blockType,
            boolean success,
            String output,
            String error,
            long executionTimeMs
    ) {}
}
