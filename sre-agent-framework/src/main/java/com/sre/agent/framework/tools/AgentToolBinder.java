package com.sre.agent.framework.tools;

import com.sre.agent.commons.model.AgentDefinition;
import com.sre.agent.plugin.api.PluginRegistry;
import com.sre.agent.plugin.api.SrePlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Dynamically binds tools to agents based on their assignedPlugins.
 *
 * Supports THREE levels of tool registration:
 *
 * Level 1 (No code): User writes .md skill files → agent invokes skills
 *         → No tool binding needed, handled by SkillEngine
 *
 * Level 2 (Plugin with @Tool): User writes one Plugin class with @Tool methods
 *         → AgentToolBinder discovers @Tool methods on the plugin automatically
 *         → Example: KafkaPlugin has @Tool checkConsumerLag() → agent gets it
 *
 * Level 3 (ToolProvider): Separate tool class registered via ToolProvider interface
 *         → For complex cases where one tool class wraps multiple plugins
 *         → Example: InfraTools wraps both AWS + PostgreSQL plugins
 */
@Component
@Slf4j
public class AgentToolBinder {

    private final PluginRegistry pluginRegistry;
    private final List<ToolProvider> toolProviders;

    public AgentToolBinder(PluginRegistry pluginRegistry, List<ToolProvider> toolProviders) {
        this.pluginRegistry = pluginRegistry;
        this.toolProviders = toolProviders;

        log.info("AgentToolBinder initialized:");
        log.info("  Tool Providers (Level 3): {}", toolProviders.size());
        for (ToolProvider provider : toolProviders) {
            log.info("    -> {} (plugins: {})", provider.getDescription(), provider.getPluginIds());
        }

        // Scan plugins for @Tool methods (Level 2)
        List<SrePlugin> pluginsWithTools = findPluginsWithToolMethods();
        log.info("  Plugins with @Tool methods (Level 2): {}", pluginsWithTools.size());
        for (SrePlugin plugin : pluginsWithTools) {
            long toolCount = countToolMethods(plugin);
            log.info("    -> {} ({} @Tool methods)", plugin.getPluginId(), toolCount);
        }
    }

    public List<Object> bindToolsForAgent(AgentDefinition agent, String tenantId) {
        List<String> assignedPlugins = agent.getAssignedPlugins();
        if (assignedPlugins == null || assignedPlugins.isEmpty()) {
            log.debug("Agent '{}' has no assigned plugins — no tools bound", agent.getAgentId());
            return List.of();
        }

        Set<String> pluginSet = new HashSet<>(assignedPlugins);
        List<Object> tools = new ArrayList<>();
        List<String> boundDescriptions = new ArrayList<>();

        // Level 3: Bind ToolProvider-based tools
        for (ToolProvider provider : toolProviders) {
            boolean shouldBind = provider.getPluginIds().stream().anyMatch(pluginSet::contains);
            if (shouldBind) {
                Object toolInstance = provider.createTools(pluginRegistry, tenantId);
                tools.add(toolInstance);
                boundDescriptions.add("[provider] " + provider.getDescription());
            }
        }

        // Level 2: Bind plugins that have @Tool methods directly
        for (SrePlugin plugin : pluginRegistry.getPluginsForTenant(tenantId)) {
            if (pluginSet.contains(plugin.getPluginId()) && hasToolMethods(plugin)) {
                // Check if this plugin is already covered by a ToolProvider
                boolean alreadyCovered = toolProviders.stream()
                        .anyMatch(tp -> tp.getPluginIds().contains(plugin.getPluginId())
                                && tools.stream().anyMatch(t ->
                                    t.getClass().getSimpleName().equals(
                                        tp.createTools(pluginRegistry, tenantId).getClass().getSimpleName())));

                if (!alreadyCovered) {
                    tools.add(plugin);  // The plugin itself is the tool object
                    boundDescriptions.add("[plugin] " + plugin.getPluginId()
                            + "(" + countToolMethods(plugin) + " @Tool methods)");
                }
            }
        }

        log.info("Agent '{}' tool binding: plugins={} -> {} tools: [{}]",
                agent.getAgentId(), assignedPlugins, tools.size(),
                String.join(", ", boundDescriptions));

        return tools;
    }

    private List<SrePlugin> findPluginsWithToolMethods() {
        List<SrePlugin> result = new ArrayList<>();
        // This gets called at startup — scan globally registered plugins
        for (var descriptor : pluginRegistry.getAvailablePlugins()) {
            pluginRegistry.getPluginsForTenant("__global__").stream()
                    .filter(p -> p.getPluginId().equals(descriptor.pluginId()))
                    .filter(this::hasToolMethods)
                    .forEach(result::add);
        }
        return result;
    }

    private boolean hasToolMethods(Object obj) {
        for (Method method : obj.getClass().getMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                return true;
            }
        }
        return false;
    }

    private long countToolMethods(Object obj) {
        return Arrays.stream(obj.getClass().getMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class))
                .count();
    }
}
