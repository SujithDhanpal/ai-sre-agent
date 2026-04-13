package com.sre.agent.plugin.api;

import com.sre.agent.commons.enums.PluginType;

import java.util.Map;

public interface SrePlugin {

    String getPluginId();

    String getDisplayName();

    String getVersion();

    PluginType getType();

    PluginConfiguration getDefaultConfiguration();

    void initialize(Map<String, String> config);

    boolean validateConnection();

    default String getDescription() {
        return getDisplayName();
    }

    default boolean isEnabled() {
        return true;
    }
}
