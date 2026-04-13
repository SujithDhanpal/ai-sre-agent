package com.sre.agent.memory.feedback;

import com.sre.agent.commons.model.EpisodicMemory;
import com.sre.agent.memory.repository.EpisodicMemoryRepository;
import com.sre.agent.memory.service.EmbeddingService;
import com.sre.agent.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostResolutionLearner {

    private final EpisodicMemoryRepository episodicRepo;
    private final MemoryService memoryService;
    private final EmbeddingService embeddingService;

    public void learnFromResolution(UUID incidentId, String tenantId,
                                     String summary, String diagnosis, String resolvedBy) {
        log.info("Learning from resolved incident: {}", incidentId);
        try {
            String narrative = "Incident resolved by %s. Diagnosis: %s".formatted(resolvedBy, diagnosis);
            float[] embedding = embeddingService.generateEmbedding(tenantId, narrative);

            episodicRepo.save(EpisodicMemory.builder()
                    .tenantId(tenantId)
                    .incidentId(incidentId)
                    .incidentSummary(summary)
                    .diagnosisSummary(diagnosis)
                    .resolutionSummary(diagnosis)
                    .resolvedAt(Instant.now())
                    .embedding(embedding.length > 0 ? embedding : null)
                    .build());

            log.info("Episodic memory stored for incident {}", incidentId);
        } catch (Exception e) {
            log.error("Failed to learn from incident {}: {}", incidentId, e.getMessage(), e);
        }
    }
}
