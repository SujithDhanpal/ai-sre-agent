package com.sre.agent.plugin.github;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sre.plugins.github")
@Getter
@Setter
public class GitHubPluginProperties {

    private boolean enabled = true;
    private String baseUrl = "https://api.github.com";
    private String token;
    private String defaultOwner;
}
