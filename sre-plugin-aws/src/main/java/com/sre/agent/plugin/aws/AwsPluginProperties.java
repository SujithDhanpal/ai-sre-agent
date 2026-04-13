package com.sre.agent.plugin.aws;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sre.plugins.aws")
@Getter
@Setter
public class AwsPluginProperties {

    private boolean enabled = true;
    private String region = "us-east-1";
}
