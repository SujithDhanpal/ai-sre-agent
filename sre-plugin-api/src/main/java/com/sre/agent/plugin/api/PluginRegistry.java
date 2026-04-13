package com.sre.agent.plugin.api;

import com.sre.agent.commons.enums.PluginType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PluginRegistry {

    private final Map<String, SrePlugin> globalPlugins = new ConcurrentHashMap<>();

    private final Map<TenantPluginKey, SrePlugin> tenantPlugins = new ConcurrentHashMap<>();

    private final List<SrePlugin> allDiscoveredPlugins;

    public PluginRegistry(List<SrePlugin> allDiscoveredPlugins) {
        this.allDiscoveredPlugins = allDiscoveredPlugins;
        allDiscoveredPlugins.forEach(plugin -> {
            globalPlugins.put(plugin.getPluginId(), plugin);
            log.info("Registered plugin: {} [{}] v{}",
                    plugin.getDisplayName(), plugin.getType(), plugin.getVersion());
        });
    }

    public <T extends SrePlugin> Optional<T> getPlugin(String tenantId, PluginType type, Class<T> pluginClass) {
        // Check tenant-specific first
        return tenantPlugins.entrySet().stream()
                .filter(e -> e.getKey().tenantId().equals(tenantId))
                .filter(e -> e.getValue().getType() == type)
                .filter(e -> pluginClass.isInstance(e.getValue()))
                .map(e -> pluginClass.cast(e.getValue()))
                .findFirst()
                .or(() -> globalPlugins.values().stream()
                        .filter(p -> p.getType() == type)
                        .filter(pluginClass::isInstance)
                        .map(pluginClass::cast)
                        .findFirst());
    }

    public <T extends SrePlugin> List<T> getPlugins(String tenantId, PluginType type, Class<T> pluginClass) {
        List<T> result = new ArrayList<>();

        // Add tenant-specific plugins
        tenantPlugins.entrySet().stream()
                .filter(e -> e.getKey().tenantId().equals(tenantId))
                .filter(e -> e.getValue().getType() == type)
                .filter(e -> pluginClass.isInstance(e.getValue()))
                .map(e -> pluginClass.cast(e.getValue()))
                .forEach(result::add);

        // Add global plugins if no tenant override
        if (result.isEmpty()) {
            globalPlugins.values().stream()
                    .filter(p -> p.getType() == type)
                    .filter(pluginClass::isInstance)
                    .map(pluginClass::cast)
                    .forEach(result::add);
        }

        return result;
    }

    public void registerForTenant(String tenantId, SrePlugin plugin, Map<String, String> config) {
        plugin.initialize(config);
        tenantPlugins.put(new TenantPluginKey(tenantId, plugin.getPluginId()), plugin);
        log.info("Registered tenant plugin: tenant={}, plugin={} [{}]",
                tenantId, plugin.getDisplayName(), plugin.getType());
    }

    public void deregisterForTenant(String tenantId, String pluginId) {
        tenantPlugins.remove(new TenantPluginKey(tenantId, pluginId));
        log.info("Deregistered tenant plugin: tenant={}, pluginId={}", tenantId, pluginId);
    }

    public List<PluginDescriptor> getAvailablePlugins() {
        return globalPlugins.values().stream()
                .map(p -> new PluginDescriptor(
                        p.getPluginId(),
                        p.getDisplayName(),
                        p.getDescription(),
                        p.getType(),
                        p.getVersion(),
                        p.getDefaultConfiguration()
                ))
                .collect(Collectors.toList());
    }

    public List<SrePlugin> getPluginsForTenant(String tenantId) {
        List<SrePlugin> plugins = new ArrayList<>(globalPlugins.values());
        tenantPlugins.entrySet().stream()
                .filter(e -> e.getKey().tenantId().equals(tenantId))
                .map(Map.Entry::getValue)
                .forEach(plugins::add);
        return plugins;
    }

    public record TenantPluginKey(String tenantId, String pluginId) {}

    public record PluginDescriptor(
            String pluginId,
            String displayName,
            String description,
            PluginType type,
            String version,
            PluginConfiguration configuration
    ) {}
}
