package com.sre.agent.memory.feedback;

import com.sre.agent.commons.model.EpisodicMemory;
import com.sre.agent.memory.repository.EpisodicMemoryRepository;
import com.sre.agent.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    private final EpisodicMemoryRepository episodicRepo;
    private final MemoryService memoryService;

    @Transactional
    public void submitDiagnosisCorrection(String tenantId, UUID incidentId,
                                           boolean diagnosisWasCorrect,
                                           String actualRootCause,
                                           String whatAgentMissed,
                                           String suggestedRule,
                                           String submittedBy) {
        log.info("Feedback received for incident {}: diagnosis correct={}", incidentId, diagnosisWasCorrect);

        // 1. Update episodic memory
        episodicRepo.findByIncidentId(incidentId).forEach(ep -> {
            ep.setDiagnosisWasCorrect(diagnosisWasCorrect);
            if (!diagnosisWasCorrect) {
                ep.setHumanCorrection(actualRootCause);
            }
            episodicRepo.save(ep);
        });

        // 2. Create correction rule if diagnosis was wrong
        if (!diagnosisWasCorrect && suggestedRule != null && !suggestedRule.isBlank()) {
            memoryService.storeCorrectionRule(
                    tenantId,
                    whatAgentMissed != null ? whatAgentMissed : actualRootCause,
                    suggestedRule,
                    "Human corrected diagnosis. Actual root cause: " + actualRootCause,
                    incidentId,
                    submittedBy
            );
            log.info("Created correction rule from feedback for incident {}", incidentId);
        }
    }

    @Transactional
    public void submitFixOutcome(String tenantId, UUID incidentId,
                                  boolean fixWorked,
                                  String whatWentWrong,
                                  String actualFix) {
        log.info("Fix outcome for incident {}: worked={}", incidentId, fixWorked);

        episodicRepo.findByIncidentId(incidentId).forEach(ep -> {
            ep.setFixWorked(fixWorked);
            if (!fixWorked && whatWentWrong != null) {
                String correction = ep.getHumanCorrection() != null
                        ? ep.getHumanCorrection() + " | Fix feedback: " + whatWentWrong
                        : "Fix feedback: " + whatWentWrong;
                ep.setHumanCorrection(correction);
            }
            episodicRepo.save(ep);
        });
    }

    @Transactional
    public void submitPlaybook(String tenantId, UUID incidentId,
                                String playbookName, String steps, String submittedBy) {
        log.info("Playbook submitted for incident {}: {}", incidentId, playbookName);

        memoryService.storePlaybook(
                tenantId,
                playbookName,
                steps,
                submittedBy
        );
    }
}
