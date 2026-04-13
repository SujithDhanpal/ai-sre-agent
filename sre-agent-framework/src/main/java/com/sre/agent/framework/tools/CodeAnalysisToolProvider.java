package com.sre.agent.framework.tools;

import com.sre.agent.plugin.api.PluginRegistry;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CodeAnalysisToolProvider implements ToolProvider {

    @Override
    public Set<String> getPluginIds() {
        return Set.of("github");
    }

    @Override
    public Object createTools(PluginRegistry pluginRegistry, String tenantId) {
        return new CodeAnalysisTools(pluginRegistry, tenantId);
    }

    @Override
    public String getDescription() {
        return "CodeAnalysisTools(readFile, searchCode, getCommits, getDiff, getDeploys, releaseHealth)";
    }
}
