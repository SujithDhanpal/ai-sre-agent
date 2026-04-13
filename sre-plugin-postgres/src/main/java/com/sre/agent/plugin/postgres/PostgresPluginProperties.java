package com.sre.agent.plugin.postgres;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sre.plugins.postgres")
@Getter
@Setter
public class PostgresPluginProperties {

    private boolean enabled = true;
    private String jdbcUrl;
    private String username;
    private String password;
    private int queryTimeoutSeconds = 10;
    private int maxRowsPerQuery = 1000;
}
