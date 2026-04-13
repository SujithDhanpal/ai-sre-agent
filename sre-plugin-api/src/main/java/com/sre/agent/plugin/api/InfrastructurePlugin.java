package com.sre.agent.plugin.api;

import com.sre.agent.plugin.api.model.*;

import java.time.Instant;
import java.util.List;

public interface InfrastructurePlugin extends SrePlugin {

    // Compute
    List<ServiceInstance> getServiceInstances(String serviceName);

    List<InfraEvent> getRecentEvents(String serviceName, Instant since);

    // Load Balancer
    TargetGroupHealth getLoadBalancerHealth(String loadBalancerName);

    // Queue / Messaging
    QueueHealth getQueueHealth(String queueIdentifier);

    // Cloud Health
    List<InfraEvent> getCloudHealthEvents();

    // Deploy history
    List<DeploymentRecord> getRecentDeployments(String serviceName, int limit);

    // Generic cloud metrics
    List<MetricDataPoint> queryCloudMetrics(CloudMetricQuery query);
}
