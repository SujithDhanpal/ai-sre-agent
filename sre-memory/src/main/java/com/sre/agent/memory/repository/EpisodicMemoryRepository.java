package com.sre.agent.memory.repository;

import com.sre.agent.commons.model.EpisodicMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EpisodicMemoryRepository extends JpaRepository<EpisodicMemory, UUID> {

    List<EpisodicMemory> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<EpisodicMemory> findByIncidentId(UUID incidentId);

    @Query(value = """
            SELECT * FROM episodic_memory
            WHERE tenant_id = :tenantId
              AND embedding IS NOT NULL
            ORDER BY embedding <=> cast(:queryEmbedding as vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<EpisodicMemory> findSimilar(String tenantId, String queryEmbedding, int limit);

    List<EpisodicMemory> findByTenantIdAndDiagnosisWasCorrectFalse(String tenantId);
}
