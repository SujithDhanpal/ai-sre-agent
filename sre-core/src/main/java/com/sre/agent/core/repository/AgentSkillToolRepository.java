package com.sre.agent.core.repository;

import com.sre.agent.commons.model.AgentSkillTool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentSkillToolRepository extends JpaRepository<AgentSkillTool, UUID> {

    List<AgentSkillTool> findByAgentId(String agentId);

    Optional<AgentSkillTool> findByAgentIdAndSkillId(String agentId, String skillId);

    void deleteByAgentIdAndSkillId(String agentId, String skillId);
}
