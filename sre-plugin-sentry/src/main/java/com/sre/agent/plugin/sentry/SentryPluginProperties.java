package com.sre.agent.plugin.sentry;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sre.plugins.sentry")
@Getter
@Setter
public class SentryPluginProperties {

    private boolean enabled = true;
    private String baseUrl = "https://sentry.io/api/0";
    private String authToken;
    private String organization;
}
