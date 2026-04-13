package com.sre.agent.skill.executor;

import com.sre.agent.skill.parser.SkillBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class BashBlockExecutor implements SkillBlockExecutor {

    @Override
    public SkillBlock.BlockType supportedType() {
        return SkillBlock.BlockType.BASH;
    }

    @Override
    public BlockResult execute(SkillBlock block, Map<String, String> variables, ExecutionContext context) {
        long start = System.currentTimeMillis();

        String script = resolveVariables(block.code(), variables);
        int timeoutSeconds = block.timeoutSeconds();

        log.info("[BashExecutor] Running script (timeout: {}s): {}", timeoutSeconds,
                script.substring(0, Math.min(script.length(), 100)));

        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", script);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                long elapsed = System.currentTimeMillis() - start;
                return BlockResult.failure("Script timed out after " + timeoutSeconds + "s", elapsed);
            }

            int exitCode = process.exitValue();
            long elapsed = System.currentTimeMillis() - start;
            String result = output.toString().trim();

            if (exitCode == 0) {
                return BlockResult.success(result, elapsed);
            } else {
                return BlockResult.failure("Exit code " + exitCode + ": " + result, elapsed);
            }

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[BashExecutor] Failed: {}", e.getMessage());
            return BlockResult.failure(e.getMessage(), elapsed);
        }
    }

    private String resolveVariables(String code, Map<String, String> variables) {
        String resolved = code;
        for (var entry : variables.entrySet()) {
            resolved = resolved.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return resolved;
    }
}
