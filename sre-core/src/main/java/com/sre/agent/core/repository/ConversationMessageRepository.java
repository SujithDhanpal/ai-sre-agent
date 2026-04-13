package com.sre.agent.core.repository;

import com.sre.agent.commons.model.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

    List<ConversationMessage> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);

    long countByIncidentId(UUID incidentId);
}
