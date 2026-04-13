package com.sre.agent.api.controller;

import com.sre.agent.memory.feedback.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/feedback")
@RequiredArgsConstructor
@Slf4j
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/correction")
    public ResponseEntity<Map<String, String>> submitCorrection(
            @PathVariable UUID incidentId,
            @RequestBody CorrectionRequest request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {

        log.info("Diagnosis correction submitted for incident {}: correct={}", incidentId, request.diagnosisWasCorrect());

        feedbackService.submitDiagnosisCorrection(
                tenantId,
                incidentId,
                request.diagnosisWasCorrect(),
                request.actualRootCause(),
                request.whatAgentMissed(),
                request.suggestedRule(),
                userId
        );

        return ResponseEntity.ok(Map.of(
                "status", "accepted",
                "incidentId", incidentId.toString(),
                "message", request.diagnosisWasCorrect()
                        ? "Positive feedback recorded"
                        : "Correction recorded — new rule will be applied to future incidents"
        ));
    }

    @PostMapping("/fix-outcome")
    public ResponseEntity<Map<String, String>> submitFixOutcome(
            @PathVariable UUID incidentId,
            @RequestBody FixOutcomeRequest request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {

        log.info("Fix outcome submitted for incident {}: worked={}", incidentId, request.fixWorked());

        feedbackService.submitFixOutcome(
                tenantId,
                incidentId,
                request.fixWorked(),
                request.whatWentWrong(),
                request.actualFix()
        );

        return ResponseEntity.ok(Map.of(
                "status", "accepted",
                "incidentId", incidentId.toString()
        ));
    }

    @PostMapping("/playbook")
    public ResponseEntity<Map<String, String>> submitPlaybook(
            @PathVariable UUID incidentId,
            @RequestBody PlaybookRequest request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {

        log.info("Playbook submitted for incident {}: {}", incidentId, request.playbookName());

        feedbackService.submitPlaybook(tenantId, incidentId, request.playbookName(), request.steps(), userId);

        return ResponseEntity.ok(Map.of(
                "status", "accepted",
                "incidentId", incidentId.toString(),
                "message", "Playbook stored — agents will use it for matching incidents"
        ));
    }

    public record CorrectionRequest(
            boolean diagnosisWasCorrect,
            String actualRootCause,
            String whatAgentMissed,
            String suggestedRule
    ) {}

    public record FixOutcomeRequest(
            boolean fixWorked,
            String whatWentWrong,
            String actualFix
    ) {}

    public record PlaybookRequest(
            String playbookName,
            String steps
    ) {}
}
