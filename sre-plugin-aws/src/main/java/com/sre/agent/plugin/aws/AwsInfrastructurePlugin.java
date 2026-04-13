package com.sre.agent.plugin.aws;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.InfrastructurePlugin;
import com.sre.agent.plugin.api.PluginConfiguration;
import com.sre.agent.plugin.api.PluginConfiguration.ConfigField;
import com.sre.agent.plugin.api.PluginConfiguration.ConfigFieldType;
import com.sre.agent.plugin.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*;
import software.amazon.awssdk.services.health.HealthClient;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "sre.plugins.aws.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class AwsInfrastructurePlugin implements InfrastructurePlugin {

    private final AwsPluginProperties properties;
    private final CloudWatchClient cloudWatch;
    private final EcsClient ecs;
    private final ElasticLoadBalancingV2Client elb;
    private final SqsClient sqs;
    private final RdsClient rds;

    public AwsInfrastructurePlugin(AwsPluginProperties properties) {
        this.properties = properties;
        Region region = Region.of(properties.getRegion());

        this.cloudWatch = CloudWatchClient.builder().region(region).build();
        this.ecs = EcsClient.builder().region(region).build();
        this.elb = ElasticLoadBalancingV2Client.builder().region(region).build();
        this.sqs = SqsClient.builder().region(region).build();
        this.rds = RdsClient.builder().region(region).build();
    }

    @Override
    public List<ServiceInstance> getServiceInstances(String serviceName) {
        log.debug("Fetching ECS tasks for service: {}", serviceName);
        try {
            ListTasksResponse tasksResponse = ecs.listTasks(ListTasksRequest.builder()
                    .serviceName(serviceName)
                    .build());

            if (tasksResponse.taskArns().isEmpty()) return List.of();

            DescribeTasksResponse described = ecs.describeTasks(DescribeTasksRequest.builder()
                    .tasks(tasksResponse.taskArns())
                    .build());

            return described.tasks().stream()
                    .map(task -> new ServiceInstance(
                            task.taskArn(),
                            serviceName,
                            task.lastStatus(),
                            task.healthStatusAsString() != null ? task.healthStatusAsString() : "UNKNOWN",
                            0,
                            task.startedAt(),
                            Map.of(
                                    "cpu", String.valueOf(task.cpu()),
                                    "memory", String.valueOf(task.memory()),
                                    "stoppedReason", task.stoppedReason() != null ? task.stoppedReason() : ""
                            )
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch ECS tasks for {}: {}", serviceName, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<InfraEvent> getRecentEvents(String serviceName, Instant since) {
        log.debug("Fetching ECS service events for: {}", serviceName);
        try {
            DescribeServicesResponse svcResponse = ecs.describeServices(DescribeServicesRequest.builder()
                    .services(serviceName)
                    .build());

            if (svcResponse.services().isEmpty()) return List.of();

            return svcResponse.services().get(0).events().stream()
                    .filter(e -> e.createdAt().isAfter(since))
                    .map(e -> new InfraEvent(
                            e.id(),
                            "ECS_SERVICE_EVENT",
                            serviceName,
                            e.message(),
                            "INFO",
                            e.createdAt()
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch ECS events for {}: {}", serviceName, e.getMessage());
            return List.of();
        }
    }

    @Override
    public TargetGroupHealth getLoadBalancerHealth(String targetGroupArn) {
        log.debug("Fetching ALB target group health: {}", targetGroupArn);
        try {
            DescribeTargetHealthResponse response = elb.describeTargetHealth(
                    DescribeTargetHealthRequest.builder()
                            .targetGroupArn(targetGroupArn)
                            .build());

            List<TargetGroupHealth.TargetStatus> targets = response.targetHealthDescriptions().stream()
                    .map(t -> new TargetGroupHealth.TargetStatus(
                            t.target().id(),
                            t.targetHealth().stateAsString(),
                            t.targetHealth().reasonAsString(),
                            t.targetHealth().description()
                    ))
                    .collect(Collectors.toList());

            int healthy = (int) targets.stream().filter(t -> "healthy".equals(t.state())).count();
            int unhealthy = (int) targets.stream().filter(t -> "unhealthy".equals(t.state())).count();
            int draining = (int) targets.stream().filter(t -> "draining".equals(t.state())).count();

            return new TargetGroupHealth(
                    targetGroupArn,
                    targets.size(),
                    healthy,
                    unhealthy,
                    draining,
                    targets
            );
        } catch (Exception e) {
            log.error("Failed to fetch ALB health for {}: {}", targetGroupArn, e.getMessage());
            return new TargetGroupHealth(targetGroupArn, 0, 0, 0, 0, List.of());
        }
    }

    @Override
    public QueueHealth getQueueHealth(String queueUrl) {
        log.debug("Fetching SQS queue health: {}", queueUrl);
        try {
            var response = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(
                            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
                            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED)
                    .build());

            var attrs = response.attributes();
            long messageCount = Long.parseLong(attrs.getOrDefault(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0"));
            long inflight = Long.parseLong(attrs.getOrDefault(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "0"));

            String queueName = queueUrl.substring(queueUrl.lastIndexOf('/') + 1);
            return new QueueHealth(queueName, messageCount, 0, 0, inflight);
        } catch (Exception e) {
            log.error("Failed to fetch SQS health for {}: {}", queueUrl, e.getMessage());
            return new QueueHealth(queueUrl, 0, 0, 0, 0);
        }
    }

    @Override
    public List<InfraEvent> getCloudHealthEvents() {
        log.debug("Fetching AWS Health events");
        try {
            var response = HealthClient.builder()
                    .region(Region.US_EAST_1) // Health API only available in us-east-1
                    .build()
                    .describeEvents(software.amazon.awssdk.services.health.model.DescribeEventsRequest.builder()
                            .filter(software.amazon.awssdk.services.health.model.EventFilter.builder()
                                    .eventStatusCodes(
                                            software.amazon.awssdk.services.health.model.EventStatusCode.OPEN,
                                            software.amazon.awssdk.services.health.model.EventStatusCode.UPCOMING)
                                    .build())
                            .build());

            return response.events().stream()
                    .map(e -> new InfraEvent(
                            e.arn(),
                            "AWS_HEALTH",
                            e.service(),
                            e.eventTypeCode() + ": " + e.statusCodeAsString(),
                            e.eventTypeCategoryAsString(),
                            e.startTime()
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to fetch AWS Health events (requires Business/Enterprise support): {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<DeploymentRecord> getRecentDeployments(String serviceName, int limit) {
        log.debug("Fetching ECS deployments for: {}", serviceName);
        try {
            DescribeServicesResponse response = ecs.describeServices(DescribeServicesRequest.builder()
                    .services(serviceName)
                    .build());

            if (response.services().isEmpty()) return List.of();

            return response.services().get(0).deployments().stream()
                    .limit(limit)
                    .map(d -> new DeploymentRecord(
                            d.id(),
                            serviceName,
                            d.taskDefinition(),
                            "",
                            "",
                            d.status(),
                            d.createdAt()
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch ECS deployments for {}: {}", serviceName, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<MetricDataPoint> queryCloudMetrics(CloudMetricQuery query) {
        log.debug("Querying CloudWatch metrics: {}/{}", query.namespace(), query.metricName());
        try {
            List<Dimension> dimensions = query.dimensions().entrySet().stream()
                    .map(e -> Dimension.builder().name(e.getKey()).value(e.getValue()).build())
                    .collect(Collectors.toList());

            GetMetricDataResponse response = cloudWatch.getMetricData(GetMetricDataRequest.builder()
                    .startTime(query.startTime())
                    .endTime(query.endTime())
                    .metricDataQueries(MetricDataQuery.builder()
                            .id("m1")
                            .metricStat(MetricStat.builder()
                                    .metric(Metric.builder()
                                            .namespace(query.namespace())
                                            .metricName(query.metricName())
                                            .dimensions(dimensions)
                                            .build())
                                    .period(query.periodSeconds())
                                    .stat("Average")
                                    .build())
                            .build())
                    .build());

            List<MetricDataPoint> points = new ArrayList<>();
            for (var result : response.metricDataResults()) {
                for (int i = 0; i < result.values().size(); i++) {
                    points.add(new MetricDataPoint(
                            query.metricName(),
                            result.timestamps().get(i),
                            result.values().get(i),
                            query.dimensions()
                    ));
                }
            }
            return points;
        } catch (Exception e) {
            log.error("Failed to query CloudWatch metrics: {}", e.getMessage());
            return List.of();
        }
    }

    // --- SrePlugin methods ---

    @Override
    public String getPluginId() { return "aws"; }

    @Override
    public String getDisplayName() { return "AWS Infrastructure"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public PluginType getType() { return PluginType.INFRASTRUCTURE; }

    @Override
    public PluginConfiguration getDefaultConfiguration() {
        return new PluginConfiguration(List.of(
                new ConfigField("region", "AWS Region", "AWS region", ConfigFieldType.STRING, true, "us-east-1")
        ));
    }

    @Override
    public void initialize(Map<String, String> config) {
        log.info("Initializing AWS plugin for region: {}", properties.getRegion());
    }

    @Override
    public boolean validateConnection() {
        try {
            cloudWatch.listMetrics(ListMetricsRequest.builder().build());
            return true;
        } catch (Exception e) {
            log.error("AWS connection validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isEnabled() { return properties.isEnabled(); }
}
