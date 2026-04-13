package com.sre.agent.demo;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.MonitoringPlugin;
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
public class DemoMonitoringPlugin implements MonitoringPlugin {

    private final Instant now = Instant.now();

    @Override
    public List<LogEntry> fetchLogs(LogQuery query) {
        return buildPaymentServiceLogs();
    }

    @Override
    public List<LogEntry> fetchLogsRaw(String logqlQuery, Instant start, Instant end, int limit) {
        log.info("[DEMO] fetchLogsRaw: {}", logqlQuery);
        return buildPaymentServiceLogs();
    }

    @Override
    public List<MetricDataPoint> fetchMetrics(MetricQuery query) {
        return List.of(
                new MetricDataPoint("http_requests_total", now.minus(5, ChronoUnit.MINUTES), 4523.0, Map.of("service", "payment-service")),
                new MetricDataPoint("http_error_rate", now.minus(5, ChronoUnit.MINUTES), 0.23, Map.of("service", "payment-service")),
                new MetricDataPoint("http_error_rate", now.minus(2, ChronoUnit.HOURS), 0.001, Map.of("service", "payment-service"))
        );
    }

    @Override
    public List<String> getActiveAlertNames(String tenantId) {
        return List.of("PaymentServiceHighErrorRate", "HikariConnectionPoolExhausted");
    }

    @Override
    public HealthStatus checkHealth() {
        return new HealthStatus(true, "Demo monitoring plugin", Map.of());
    }

    @Override
    public List<String> getLogLabels() {
        return List.of("service_name", "level", "environment", "instance");
    }

    @Override
    public List<String> getLogLabelValues(String labelName) {
        return switch (labelName) {
            case "service_name" -> List.of("payment-service", "order-service", "notification-service", "gateway");
            case "level" -> List.of("INFO", "WARN", "ERROR", "DEBUG");
            case "environment" -> List.of("production", "staging");
            default -> List.of();
        };
    }

    private List<LogEntry> buildPaymentServiceLogs() {
        return List.of(
                log(45, "INFO", "payment-service", "Received POST /api/v1/payments/process request_id=b24cb14f-a9a9-4e6e-ac8e-410b7c7dee13 tenant_id=acme-corp user_id=usr_8472 amount=249.99 currency=USD"),
                log(45, "DEBUG", "payment-service", "Attempting to acquire database connection from HikariPool-1 for request_id=b24cb14f-a9a9-4e6e-ac8e-410b7c7dee13"),
                log(15, "WARN", "payment-service", "HikariPool-1 - Connection is not available, waited 30000ms. Active=20, Idle=0, Waiting=12. request_id=b24cb14f-a9a9-4e6e-ac8e-410b7c7dee13"),
                log(15, "ERROR", "payment-service", "Failed to process payment for request_id=b24cb14f-a9a9-4e6e-ac8e-410b7c7dee13 tenant_id=acme-corp user_id=usr_8472: org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection; nested exception is java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms."),
                log(15, "ERROR", "payment-service", "  at com.acme.payment.service.PaymentService.processPayment(PaymentService.java:87)\n  at com.acme.payment.controller.PaymentController.process(PaymentController.java:42)\n  at com.acme.payment.repository.PaymentRepository.save(PaymentRepository.java:31)\n  Caused by: java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms\n  at com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:696)"),
                log(44, "WARN", "payment-service", "HikariPool-1 - Connection pool stats: Active=20, Idle=0, Waiting=8, Total=20, Max=20"),
                log(43, "INFO", "payment-service", "PaymentReconciliationJob started — processing batch of 500 records (batch_id=rec_20260413_001)"),
                log(42, "INFO", "payment-service", "PaymentReconciliationJob batch query executing — SELECT * FROM payment_transactions WHERE status='PENDING' AND created_at > ? ORDER BY created_at LIMIT 500"),
                log(38, "WARN", "payment-service", "HikariPool-1 - Connection pool stats: Active=18, Idle=2, Waiting=0, Total=20, Max=20"),
                log(35, "INFO", "payment-service", "PaymentReconciliationJob — reconciled 500/500 records in 180s (batch_id=rec_20260413_001), holding 15 connections"),
                log(120, "INFO", "payment-service", "Application started successfully. HikariPool-1 - Start completed. Pool stats: Active=2, Idle=8, Waiting=0, Total=10, Max=20"),
                log(119, "INFO", "payment-service", "Deployed version 2.14.0 (commit: a3f7c2e) — changelog: Increase reconciliation batch size to 500")
        );
    }

    private LogEntry log(int minutesAgo, String level, String service, String message) {
        return new LogEntry(
                now.minus(minutesAgo, ChronoUnit.MINUTES),
                level,
                message,
                service,
                null,
                Map.of("service_name", service, "environment", "production")
        );
    }

    @Override public String getPluginId() { return "grafana"; }
    @Override public String getDisplayName() { return "Demo Grafana (Loki + Prometheus)"; }
    @Override public String getVersion() { return "1.0.0-demo"; }
    @Override public PluginType getType() { return PluginType.MONITORING; }
    @Override public PluginConfiguration getDefaultConfiguration() { return new PluginConfiguration(List.of()); }
    @Override public void initialize(Map<String, String> config) {}
    @Override public boolean validateConnection() { return true; }
}
