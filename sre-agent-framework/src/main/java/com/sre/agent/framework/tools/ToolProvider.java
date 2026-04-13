package com.sre.agent.framework.tools;

import java.util.Set;

/**
 * Interface for registering custom tool classes.
 * Users implement this to provide tools for their custom plugins.
 *
 * Example:
 *   @Component
 *   public class KafkaToolProvider implements ToolProvider {
 *       public Set<String> getPluginIds() { return Set.of("kafka"); }
 *       public Object createTools(PluginRegistry registry, String tenantId) {
 *           return new KafkaTools(registry, tenantId);
 *       }
 *   }
 */
public interface ToolProvider {

    /**
     * Which plugin IDs trigger this tool provider.
     * If an agent has ANY of these plugins assigned, it gets these tools.
     */
    Set<String> getPluginIds();

    /**
     * Create the tool instance for a given tenant.
     * The returned object should have @Tool-annotated methods.
     */
    Object createTools(com.sre.agent.plugin.api.PluginRegistry pluginRegistry, String tenantId);

    /**
     * Human-readable description for logging.
     */
    default String getDescription() {
        return getClass().getSimpleName();
    }
}
