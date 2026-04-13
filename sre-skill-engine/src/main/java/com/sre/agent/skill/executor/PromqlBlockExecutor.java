package com.sre.agent.skill.executor;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.MonitoringPlugin;
import com.sre.agent.plugin.api.model.MetricDataPoint;
import com.sre.agent.plugin.api.model.MetricQuery;
import com.sre.agent.skill.parser.SkillBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class PromqlBlockExecutor implements SkillBlockExecutor {

    @Override
    public SkillBlock.BlockType supportedType() {
        return SkillBlock.BlockType.PROMQL;
    }

    @Override
    public BlockResult execute(SkillBlock block, Map<String, String> variables, ExecutionContext context) {
        long start = System.currentTimeMillis();

        String query = resolveVariables(block.code(), variables);
        log.debug("Executing PromQL skill block: {}", query);

        try {
            MonitoringPlugin monitoring = context.pluginRegistry()
                    .getPlugin(context.tenantId(), PluginType.MONITORING, MonitoringPlugin.class)
                    .orElseThrow(() -> new IllegalStateException("No monitoring plugin configured"));

            Instant now = Instant.now();
            Instant thirtyMinAgo = now.minusSeconds(1800);
            List<MetricDataPoint> results = monitoring.fetchMetrics(
                    new MetricQuery(query, thirtyMinAgo, now, "60s"));

            String output = formatMetricResults(results);
            long elapsed = System.currentTimeMillis() - start;
            return BlockResult.success(output, elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("PromQL block execution failed: {}", e.getMessage());
            return BlockResult.failure(e.getMessage(), elapsed);
        }
    }

    private String formatMetricResults(List<MetricDataPoint> points) {
        if (points.isEmpty()) return "No data points returned";

        var sb = new StringBuilder();
        sb.append("Data points: ").append(points.size()).append("\n\n");
        for (var point : points) {
            sb.append(point.timestamp()).append(" → ").append(String.format("%.4f", point.value()));
            if (point.labels() != null && !point.labels().isEmpty()) {
                sb.append(" ").append(point.labels());
            }
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
