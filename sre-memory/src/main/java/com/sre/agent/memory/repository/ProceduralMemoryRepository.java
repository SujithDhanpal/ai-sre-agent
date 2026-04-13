package com.sre.agent.memory.repository;

import com.sre.agent.commons.enums.ProceduralType;
import com.sre.agent.commons.model.ProceduralMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProceduralMemoryRepository extends JpaRepository<ProceduralMemory, UUID> {

    List<ProceduralMemory> findByTenantIdAndActiveTrue(String tenantId);

    List<ProceduralMemory> findByTenantIdAndTypeAndActiveTrue(String tenantId, ProceduralType type);

    @Query("""
            SELECT p FROM ProceduralMemory p
            WHERE p.tenantId = :tenantId
              AND p.active = true
              AND LOWER(p.triggerPattern) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    List<ProceduralMemory> findMatchingRules(String tenantId, String keyword);

    List<ProceduralMemory> findBySourceIncidentId(UUID incidentId);
}
