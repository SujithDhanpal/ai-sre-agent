package com.sre.agent.framework.tools;

import com.sre.agent.plugin.api.PluginRegistry;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class InfraToolProvider implements ToolProvider {

    @Override
    public Set<String> getPluginIds() {
        return Set.of("aws", "postgres");
    }

    @Override
    public Object createTools(PluginRegistry pluginRegistry, String tenantId) {
        return new InfraTools(pluginRegistry, tenantId);
    }

    @Override
    public String getDescription() {
        return "InfraTools(checkServiceHealth, checkLB, checkQueue, checkDB*, checkReplication, checkAWS)";
    }
}
