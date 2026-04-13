package com.sre.agent.demo;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.ErrorTrackingPlugin;
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
public class DemoErrorTrackingPlugin implements ErrorTrackingPlugin {

    private final Instant now = Instant.now();

    @Override
    public List<ErrorGroup> getRecentErrorGroups(String service, Instant since) {
        log.info("[DEMO] getRecentErrorGroups: {}", service);
        return List.of(
                new ErrorGroup("err-001", "SQLTransientConnectionException: HikariPool-1 - Connection is not available",
                        "PaymentService.processPayment", 847, 234,
                        now.minus(45, ChronoUnit.MINUTES), now.minus(2, ChronoUnit.MINUTES),
                        "error", List.of("payment-service", "hikari", "timeout")),
                new ErrorGroup("err-002", "SocketTimeoutException: Read timed out",
                        "NotificationService.sendConfirmation", 23, 18,
                        now.minus(30, ChronoUnit.MINUTES), now.minus(5, ChronoUnit.MINUTES),
                        "error", List.of("payment-service", "notification"))
        );
    }

    @Override
    public ErrorGroupDetail getErrorGroupDetail(String errorGroupId) {
        log.info("[DEMO] getErrorGroupDetail: {}", errorGroupId);
        return new ErrorGroupDetail(
                "err-001",
                "SQLTransientConnectionException: HikariPool-1 - Connection is not available",
                "SQLTransientConnectionException",
                "HikariPool-1 - Connection is not available, request timed out after 30000ms",
                List.of(
                        new ErrorGroupDetail.StackFrame("PaymentService.java", "processPayment", 87, "var payment = paymentRepo.save(Payment.from(request));", "com.acme.payment.service"),
                        new ErrorGroupDetail.StackFrame("PaymentController.java", "process", 42, "return paymentService.processPayment(request);", "com.acme.payment.controller"),
                        new ErrorGroupDetail.StackFrame("HikariPool.java", "getConnection", 197, "throw createTimeoutException(startTime);", "com.zaxxer.hikari.pool")
                ),
                List.of(
                        new ErrorGroupDetail.Breadcrumb("http", "POST /api/v1/payments/process", "info", now.minus(15, ChronoUnit.MINUTES).toString(), Map.of("request_id", "b24cb14f-a9a9-4e6e-ac8e-410b7c7dee13")),
                        new ErrorGroupDetail.Breadcrumb("db", "Waiting for connection from HikariPool-1", "warning", now.minus(15, ChronoUnit.MINUTES).toString(), Map.of("pool", "HikariPool-1", "wait_ms", "30000"))
                ),
                Map.of("service", "payment-service", "environment", "production", "release", "v2.14.0"),
                Map.of("pool_active", 20, "pool_max", 20, "pool_waiting", 12)
        );
    }

    @Override
    public List<ErrorGroup> getNewErrors(String service, Instant since) {
        return getRecentErrorGroups(service, since);
    }

    @Override
    public List<ErrorGroup> getRegressions(String service, Instant since) {
        return List.of();
    }

    @Override
    public ReleaseHealth getReleaseHealth(String service, String version) {
        log.info("[DEMO] getReleaseHealth: {} {}", service, version);
        return new ReleaseHealth("v2.14.0", 76.8, 72.1, 12500, 847, 2, now.minus(2, ChronoUnit.HOURS));
    }

    @Override
    public List<ReleaseHealth> getRecentReleases(String service, int limit) {
        return List.of(
                new ReleaseHealth("v2.14.0", 76.8, 72.1, 12500, 847, 2, now.minus(2, ChronoUnit.HOURS)),
                new ReleaseHealth("v2.13.1", 99.7, 99.5, 45000, 12, 0, now.minus(5, ChronoUnit.DAYS))
        );
    }

    @Override public String getPluginId() { return "sentry"; }
    @Override public String getDisplayName() { return "Demo Sentry"; }
    @Override public String getVersion() { return "1.0.0-demo"; }
    @Override public PluginType getType() { return PluginType.ERROR_TRACKING; }
    @Override public PluginConfiguration getDefaultConfiguration() { return new PluginConfiguration(List.of()); }
    @Override public void initialize(Map<String, String> config) {}
    @Override public boolean validateConnection() { return true; }
}
