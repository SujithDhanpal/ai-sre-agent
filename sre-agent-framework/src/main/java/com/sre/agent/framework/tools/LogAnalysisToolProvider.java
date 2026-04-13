package com.sre.agent.framework.tools;

import com.sre.agent.plugin.api.PluginRegistry;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class LogAnalysisToolProvider implements ToolProvider {

    @Override
    public Set<String> getPluginIds() {
        return Set.of("grafana", "sentry");
    }

    @Override
    public Object createTools(PluginRegistry pluginRegistry, String tenantId) {
        return new LogAnalysisTools(pluginRegistry, tenantId);
    }

    @Override
    public String getDescription() {
        return "LogAnalysisTools(fetchLogs, queryMetrics, getRecentErrors, getErrorDetail)";
    }
}
