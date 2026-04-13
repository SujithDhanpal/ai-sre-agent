package com.sre.agent.core.repository;

import com.sre.agent.commons.model.SkillDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillDefinitionRepository extends JpaRepository<SkillDefinition, UUID> {

    Optional<SkillDefinition> findBySkillIdAndTenantId(String skillId, String tenantId);

    Optional<SkillDefinition> findBySkillIdAndTenantIdIsNull(String skillId);

    @Query("SELECT s FROM SkillDefinition s WHERE s.active = true AND (s.tenantId = :tenantId OR s.tenantId IS NULL)")
    List<SkillDefinition> findActiveSkillsForTenant(String tenantId);
}
