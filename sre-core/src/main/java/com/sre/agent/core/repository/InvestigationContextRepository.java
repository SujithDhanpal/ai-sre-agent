package com.sre.agent.core.repository;

import com.sre.agent.commons.model.InvestigationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvestigationContextRepository extends JpaRepository<InvestigationContext, UUID> {

    Optional<InvestigationContext> findByIncidentId(UUID incidentId);
}
