package com.sre.agent.core.repository;

import com.sre.agent.commons.model.AgentDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentDefinitionRepository extends JpaRepository<AgentDefinition, UUID> {

    Optional<AgentDefinition> findByAgentIdAndTenantId(String agentId, String tenantId);

    Optional<AgentDefinition> findByAgentIdAndTenantIdIsNull(String agentId);

    @Query("SELECT a FROM AgentDefinition a WHERE a.active = true AND (a.tenantId = :tenantId OR a.tenantId IS NULL)")
    List<AgentDefinition> findActiveAgentsForTenant(String tenantId);

    List<AgentDefinition> findByTenantIdIsNullAndActiveTrue();
}
