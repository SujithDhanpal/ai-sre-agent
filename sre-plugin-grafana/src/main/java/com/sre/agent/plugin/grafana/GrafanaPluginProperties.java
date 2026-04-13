package com.sre.agent.plugin.grafana;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sre.plugins.grafana")
@Getter
@Setter
public class GrafanaPluginProperties {

    private boolean enabled = true;

    private Loki loki = new Loki();
    private Prometheus prometheus = new Prometheus();
    private String apiKey;

    @Getter
    @Setter
    public static class Loki {
        private String baseUrl = "http://localhost:3100";
        private int defaultLimitLines = 500;
        private String defaultTimeRange = "30m";
    }

    @Getter
    @Setter
    public static class Prometheus {
        private String baseUrl = "http://localhost:9090";
        private String defaultStep = "60s";
    }
}
