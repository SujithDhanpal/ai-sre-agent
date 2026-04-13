package com.sre.agent.plugin.api;

import java.util.List;
import java.util.Map;

public record PluginConfiguration(
        List<ConfigField> fields
) {
    public record ConfigField(
            String key,
            String displayName,
            String description,
            ConfigFieldType type,
            boolean required,
            String defaultValue
    ) {}

    public enum ConfigFieldType {
        STRING,
        SECRET,
        URL,
        INTEGER,
        BOOLEAN
    }
}
