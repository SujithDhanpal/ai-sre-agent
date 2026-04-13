package com.sre.agent.memory.repository;

import com.sre.agent.commons.model.SemanticMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SemanticMemoryRepository extends JpaRepository<SemanticMemory, UUID> {

    Optional<SemanticMemory> findByTenantIdAndNamespaceAndKey(String tenantId, String namespace, String key);

    List<SemanticMemory> findByTenantIdAndNamespace(String tenantId, String namespace);

    List<SemanticMemory> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
