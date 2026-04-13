package com.sre.agent.demo;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.InfrastructurePlugin;
import com.sre.agent.plugin.api.PluginConfiguration;
import com.sre.agent.plugin.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
@Profile("demo")
@Slf4j
public class DemoInfrastructurePlugin implements InfrastructurePlugin {

    private final Instant now = Instant.now();

    @Override
    public List<ServiceInstance> getServiceInstances(String serviceName) {
        log.info("[DEMO] getServiceInstances: {}", serviceName);
        return List.of(
                new ServiceInstance("pod-1", serviceName, "running", "healthy", 0, now.minus(2, ChronoUnit.HOURS), Map.of("cpu", "72%", "memory", "61%")),
                new ServiceInstance("pod-2", serviceName, "running", "healthy", 0, now.minus(2, ChronoUnit.HOURS), Map.of("cpu", "68%", "memory", "58%")),
                new ServiceInstance("pod-3", serviceName, "running", "healthy", 0, now.minus(2, ChronoUnit.HOURS), Map.of("cpu", "75%", "memory", "63%"))
        );
    }

    @Override
    public List<InfraEvent> getRecentEvents(String serviceName, Instant since) {
        return List.of(
                new InfraEvent("evt-1", "deployment", serviceName, "Deployed v2.14.0 (commit a3f7c2e)", "info", now.minus(2, ChronoUnit.HOURS)),
                new InfraEvent("evt-2", "scaling", serviceName, "Desired count unchanged at 3", "info", now.minus(2, ChronoUnit.HOURS))
        );
    }

    @Override
    public TargetGroupHealth getLoadBalancerHealth(String loadBalancerName) {
        log.info("[DEMO] getLoadBalancerHealth: {}", loadBalancerName);
        return new TargetGroupHealth(loadBalancerName, 3, 3, 0, 0, List.of(
                new TargetGroupHealth.TargetStatus("pod-1", "healthy", "", ""),
                new TargetGroupHealth.TargetStatus("pod-2", "healthy", "", ""),
                new TargetGroupHealth.TargetStatus("pod-3", "healthy", "", "")
        ));
    }

    @Override
    public QueueHealth getQueueHealth(String queueIdentifier) {
        log.info("[DEMO] getQueueHealth: {}", queueIdentifier);
        return new QueueHealth(queueIdentifier, 12, 45, 0, 3);
    }

    @Override
    public List<InfraEvent> getCloudHealthEvents() {
        return List.of();
    }

    @Override
    public List<DeploymentRecord> getRecentDeployments(String serviceName, int limit) {
        return List.of(
                new DeploymentRecord("deploy-847", "payment-service", "v2.14.0", "a3f7c2e",
                        "dev@acme.com", "success", now.minus(2, ChronoUnit.HOURS))
        );
    }

    @Override
    public List<MetricDataPoint> queryCloudMetrics(CloudMetricQuery query) {
        return List.of(
                new MetricDataPoint("HTTPCode_Target_5XX_Count", now.minus(5, ChronoUnit.MINUTES), 1124.0, Map.of()),
                new MetricDataPoint("HTTPCode_Target_2XX_Count", now.minus(5, ChronoUnit.MINUTES), 3726.0, Map.of())
        );
    }

    @Override public String getPluginId() { return "aws"; }
    @Override public String getDisplayName() { return "Demo AWS Infrastructure"; }
    @Override public String getVersion() { return "1.0.0-demo"; }
    @Override public PluginType getType() { return PluginType.INFRASTRUCTURE; }
    @Override public PluginConfiguration getDefaultConfiguration() { return new PluginConfiguration(List.of()); }
    @Override public void initialize(Map<String, String> config) {}
    @Override public boolean validateConnection() { return true; }
}
