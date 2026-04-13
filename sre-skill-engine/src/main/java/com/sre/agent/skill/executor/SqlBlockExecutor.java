package com.sre.agent.skill.executor;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.DatabasePlugin;
import com.sre.agent.plugin.api.model.QueryResult;
import com.sre.agent.skill.parser.SkillBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SqlBlockExecutor implements SkillBlockExecutor {

    @Override
    public SkillBlock.BlockType supportedType() {
        return SkillBlock.BlockType.SQL;
    }

    @Override
    public BlockResult execute(SkillBlock block, Map<String, String> variables, ExecutionContext context) {
        long start = System.currentTimeMillis();

        String sql = resolveVariables(block.code(), variables);
        log.debug("Executing SQL skill block: {}", sql.substring(0, Math.min(sql.length(), 100)));

        try {
            DatabasePlugin dbPlugin = context.pluginRegistry()
                    .getPlugin(context.tenantId(), PluginType.DATABASE, DatabasePlugin.class)
                    .orElseThrow(() -> new IllegalStateException("No database plugin configured"));

            QueryResult result = dbPlugin.executeReadOnlyQuery(sql, Map.of());

            String output = formatQueryResult(result);
            long elapsed = System.currentTimeMillis() - start;
            return BlockResult.success(output, elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("SQL block execution failed: {}", e.getMessage());
            return BlockResult.failure(e.getMessage(), elapsed);
        }
    }

    private String formatQueryResult(QueryResult result) {
        if (result.rows().isEmpty()) return "No rows returned";

        var sb = new StringBuilder();
        sb.append("Columns: ").append(String.join(", ", result.columns())).append("\n");
        sb.append("Rows: ").append(result.rowCount()).append(" (").append(result.executionTimeMs()).append("ms)\n\n");

        for (var row : result.rows()) {
            sb.append(row.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(" | ")));
            sb.append("\n");
        }
        return sb.toString();
    }

    private String resolveVariables(String code, Map<String, String> variables) {
        String resolved = code;
        for (var entry : variables.entrySet()) {
            resolved = resolved.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return resolved;
    }
}
