package com.sre.agent.core.repository;

import com.sre.agent.commons.enums.IncidentStatus;
import com.sre.agent.commons.enums.Severity;
import com.sre.agent.commons.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    Optional<Incident> findByCorrelationId(String correlationId);

    boolean existsByCorrelationId(String correlationId);

    Page<Incident> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    List<Incident> findByTenantIdAndStatus(String tenantId, IncidentStatus status);

    List<Incident> findByTenantIdAndSeverityAndStatusNot(String tenantId, Severity severity, IncidentStatus status);

    @Query("SELECT i FROM Incident i WHERE i.tenantId = :tenantId AND i.status NOT IN :resolvedStatuses AND i.createdAt > :since")
    List<Incident> findActiveIncidents(String tenantId, List<IncidentStatus> resolvedStatuses, Instant since);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.tenantId = :tenantId AND i.status = :status")
    long countByTenantIdAndStatus(String tenantId, IncidentStatus status);
}
