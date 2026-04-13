package com.sre.agent.framework.registry;

import com.sre.agent.commons.model.AgentDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AgentRegistry {

    private final Map<String, AgentDefinition> platformAgents = new ConcurrentHashMap<>();
    private final Map<TenantAgentKey, AgentDefinition> tenantAgents = new ConcurrentHashMap<>();

    public void registerPlatformAgent(AgentDefinition agent) {
        platformAgents.put(agent.getAgentId(), agent);
        log.info("Registered platform agent: {} [{}]", agent.getAgentId(), agent.getDisplayName());
    }

    public void registerTenantAgent(String tenantId, AgentDefinition agent) {
        tenantAgents.put(new TenantAgentKey(tenantId, agent.getAgentId()), agent);
        log.info("Registered tenant agent: tenant={}, agentId={}", tenantId, agent.getAgentId());
    }

    public Optional<AgentDefinition> getAgent(String tenantId, String agentId) {
        // Tenant-specific first
        AgentDefinition tenantAgent = tenantAgents.get(new TenantAgentKey(tenantId, agentId));
        if (tenantAgent != null && tenantAgent.isActive()) {
            return Optional.of(tenantAgent);
        }
        // Then platform
        AgentDefinition platformAgent = platformAgents.get(agentId);
        if (platformAgent != null && platformAgent.isActive()) {
            return Optional.of(platformAgent);
        }
        return Optional.empty();
    }

    public List<AgentDefinition> getActiveAgents(String tenantId) {
        List<AgentDefinition> agents = platformAgents.values().stream()
                .filter(AgentDefinition::isActive)
                .collect(Collectors.toList());

        tenantAgents.entrySet().stream()
                .filter(e -> e.getKey().tenantId().equals(tenantId))
                .map(Map.Entry::getValue)
                .filter(AgentDefinition::isActive)
                .forEach(agents::add);

        return agents;
    }

    public List<AgentDefinition> getSpecialistAgents(String tenantId) {
        return getActiveAgents(tenantId).stream()
                .filter(a -> !a.isReviewer())
                .filter(a -> !"orchestrator".equals(a.getAgentId()))
                .collect(Collectors.toList());
    }

    public List<AgentDefinition> getReviewerAgents(String tenantId) {
        return getActiveAgents(tenantId).stream()
                .filter(AgentDefinition::isReviewer)
                .collect(Collectors.toList());
    }

    public void deactivateAgent(String tenantId, String agentId) {
        TenantAgentKey key = new TenantAgentKey(tenantId, agentId);
        AgentDefinition agent = tenantAgents.get(key);
        if (agent != null) {
            agent.setActive(false);
            log.info("Deactivated tenant agent: tenant={}, agentId={}", tenantId, agentId);
        }
    }

    private record TenantAgentKey(String tenantId, String agentId) {}
}
