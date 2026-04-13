package com.sre.agent.demo;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.CodeHostingPlugin;
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
public class DemoCodeHostingPlugin implements CodeHostingPlugin {

    private final Instant now = Instant.now();

    @Override
    public String fetchFileContent(String repo, String path, String ref) {
        log.info("[DEMO] fetchFileContent: {} {}", repo, path);
        if (path.contains("PaymentReconciliationJob")) {
            return """
                    package com.acme.payment.job;

                    import org.springframework.scheduling.annotation.Scheduled;
                    import org.springframework.stereotype.Component;
                    import org.springframework.transaction.annotation.Transactional;

                    @Component
                    public class PaymentReconciliationJob {

                        private static final int BATCH_SIZE = 500; // Changed from 10 in v2.14.0

                        private final PaymentTransactionRepository transactionRepo;
                        private final PaymentAuditLogRepository auditRepo;

                        @Scheduled(fixedRate = 300000) // every 5 minutes
                        @Transactional
                        public void reconcile() {
                            var pending = transactionRepo.findByStatus("PENDING", BATCH_SIZE);
                            for (var txn : pending) {
                                txn.setStatus("RECONCILED");
                                txn.setReconciledAt(Instant.now());
                                transactionRepo.save(txn);
                                auditRepo.save(new AuditEntry(txn.getId(), "RECONCILED"));
                            }
                            // NOTE: Each save() acquires a separate DB connection from the pool
                            // With BATCH_SIZE=500, this holds hundreds of connections simultaneously
                        }
                    }
                    """;
        }
        if (path.contains("PaymentService")) {
            return """
                    package com.acme.payment.service;

                    import org.springframework.stereotype.Service;
                    import org.springframework.transaction.annotation.Transactional;

                    @Service
                    public class PaymentService {

                        private final PaymentRepository paymentRepo;
                        private final NotificationService notificationService;

                        @Transactional
                        public PaymentResult processPayment(PaymentRequest request) {
                            // Line 87: This is where the connection timeout happens
                            var payment = paymentRepo.save(Payment.from(request));
                            notificationService.sendConfirmation(payment);
                            return PaymentResult.success(payment.getId());
                        }
                    }
                    """;
        }
        return "// File not found in demo";
    }

    @Override
    public List<String> listFiles(String repo, String path, String ref) {
        return List.of(
                "src/main/java/com/acme/payment/service/PaymentService.java",
                "src/main/java/com/acme/payment/job/PaymentReconciliationJob.java",
                "src/main/java/com/acme/payment/repository/PaymentRepository.java",
                "src/main/java/com/acme/payment/controller/PaymentController.java",
                "src/main/resources/application.yml"
        );
    }

    @Override
    public List<CommitInfo> getRecentCommits(String repo, String branch, int limit) {
        log.info("[DEMO] getRecentCommits: {}", repo);
        return List.of(
                new CommitInfo("a3f7c2e", "Increase reconciliation batch size to 500 for faster processing",
                        "dev@acme.com", now.minus(2, ChronoUnit.HOURS),
                        List.of("src/main/java/com/acme/payment/job/PaymentReconciliationJob.java")),
                new CommitInfo("b1e9d4a", "Add payment audit logging",
                        "dev@acme.com", now.minus(3, ChronoUnit.DAYS),
                        List.of("src/main/java/com/acme/payment/job/PaymentReconciliationJob.java",
                                "src/main/java/com/acme/payment/repository/PaymentAuditLogRepository.java")),
                new CommitInfo("c5f2a8b", "Fix null check in payment validation",
                        "senior-dev@acme.com", now.minus(5, ChronoUnit.DAYS),
                        List.of("src/main/java/com/acme/payment/service/PaymentService.java"))
        );
    }

    @Override
    public PullRequest createPullRequest(CreatePrRequest request) {
        return new PullRequest(9999, "https://github.com/acme/payment-service/pull/9999",
                request.title(), "Auto-generated fix", "open", "sre-fix/pool-size", "main", List.of("auto-fix"));
    }

    @Override
    public String getDiff(String repo, String baseBranch, String headBranch) {
        return """
                diff --git a/src/main/java/com/acme/payment/job/PaymentReconciliationJob.java b/src/main/java/com/acme/payment/job/PaymentReconciliationJob.java
                --- a/src/main/java/com/acme/payment/job/PaymentReconciliationJob.java
                +++ b/src/main/java/com/acme/payment/job/PaymentReconciliationJob.java
                @@ -9,7 +9,7 @@ public class PaymentReconciliationJob {
                -    private static final int BATCH_SIZE = 10;
                +    private static final int BATCH_SIZE = 500; // Changed from 10 in v2.14.0
                """;
    }

    @Override
    public List<String> searchCode(String repo, String query) {
        log.info("[DEMO] searchCode: {}", query);
        if (query.toLowerCase().contains("hikari") || query.toLowerCase().contains("connection")) {
            return List.of(
                    "src/main/resources/application.yml: spring.datasource.hikari.maximum-pool-size=20",
                    "src/main/resources/application.yml: spring.datasource.hikari.connection-timeout=30000"
            );
        }
        if (query.toLowerCase().contains("batch") || query.toLowerCase().contains("reconcil")) {
            return List.of(
                    "src/main/java/com/acme/payment/job/PaymentReconciliationJob.java: BATCH_SIZE = 500"
            );
        }
        return List.of();
    }

    @Override
    public List<DeploymentRecord> getRecentDeployments(String repo, int limit) {
        log.info("[DEMO] getRecentDeployments");
        return List.of(
                new DeploymentRecord("deploy-847", "payment-service", "v2.14.0", "a3f7c2e",
                        "dev@acme.com", "success", now.minus(2, ChronoUnit.HOURS)),
                new DeploymentRecord("deploy-846", "payment-service", "v2.13.1", "c5f2a8b",
                        "senior-dev@acme.com", "success", now.minus(5, ChronoUnit.DAYS))
        );
    }

    @Override
    public String createBranch(String repo, String branchName, String fromRef) {
        return "demo-branch-sha-abc123";
    }

    @Override
    public void commitFiles(String repo, String branch, String message, Map<String, String> files) {
        log.info("[DEMO] commitFiles to {}: {}", branch, message);
    }

    @Override public String getPluginId() { return "github"; }
    @Override public String getDisplayName() { return "Demo GitHub"; }
    @Override public String getVersion() { return "1.0.0-demo"; }
    @Override public PluginType getType() { return PluginType.CODE_HOSTING; }
    @Override public PluginConfiguration getDefaultConfiguration() { return new PluginConfiguration(List.of()); }
    @Override public void initialize(Map<String, String> config) {}
    @Override public boolean validateConnection() { return true; }
}
