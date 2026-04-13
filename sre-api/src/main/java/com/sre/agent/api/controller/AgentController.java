package com.sre.agent.api.controller;

import com.sre.agent.commons.enums.AgentSource;
import com.sre.agent.commons.model.AgentDefinition;
import com.sre.agent.core.repository.AgentDefinitionRepository;
import com.sre.agent.framework.registry.AgentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
@Slf4j
public class AgentController {

    private final AgentRegistry agentRegistry;
    private final AgentDefinitionRepository agentDefinitionRepository;

    @GetMapping
    public ResponseEntity<List<AgentDefinition>> listAgents(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
        return ResponseEntity.ok(agentRegistry.getActiveAgents(tenantId));
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<AgentDefinition> getAgent(
            @PathVariable String agentId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
        return agentRegistry.getAgent(tenantId, agentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AgentDefinition> createAgent(
            @RequestBody CreateAgentRequest request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {

        log.info("Creating custom agent: agentId={}, tenant={}", request.agentId(), tenantId);

        AgentDefinition agent = AgentDefinition.builder()
                .tenantId(tenantId)
                .agentId(request.agentId())
                .displayName(request.displayName())
                .description(request.description())
                .source(AgentSource.TENANT)
                .systemPrompt(request.systemPrompt())
                .assignedSkills(request.assignedSkills())
                .assignedPlugins(request.assignedPlugins())
                .llmModel(request.llmModel() != null ? request.llmModel() : "claude-sonnet-4-20250514")
                .maxToolIterations(request.maxToolIterations() > 0 ? request.maxToolIterations() : 15)
                .maxTokenBudget(request.maxTokenBudget() > 0 ? request.maxTokenBudget() : 50000)
                .reviewer(request.isReviewer())
                .createdBy(userId)
                .build();

        AgentDefinition saved = agentDefinitionRepository.save(agent);
        agentRegistry.registerTenantAgent(tenantId, saved);

        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/{agentId}")
    public ResponseEntity<AgentDefinition> updateAgent(
            @PathVariable String agentId,
            @RequestBody UpdateAgentRequest request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {

        return agentDefinitionRepository.findByAgentIdAndTenantId(agentId, tenantId)
                .map(existing -> {
                    if (request.displayName() != null) existing.setDisplayName(request.displayName());
                    if (request.description() != null) existing.setDescription(request.description());
                    if (request.systemPrompt() != null) existing.setSystemPrompt(request.systemPrompt());
                    if (request.assignedSkills() != null) existing.setAssignedSkills(request.assignedSkills());
                    if (request.assignedPlugins() != null) existing.setAssignedPlugins(request.assignedPlugins());
                    if (request.llmModel() != null) existing.setLlmModel(request.llmModel());

                    AgentDefinition updated = agentDefinitionRepository.save(existing);
                    agentRegistry.registerTenantAgent(tenantId, updated);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{agentId}/activate")
    public ResponseEntity<Map<String, String>> activateAgent(
            @PathVariable String agentId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {

        return agentDefinitionRepository.findByAgentIdAndTenantId(agentId, tenantId)
                .map(agent -> {
                    agent.setActive(true);
                    agentDefinitionRepository.save(agent);
                    agentRegistry.registerTenantAgent(tenantId, agent);
                    return ResponseEntity.ok(Map.of("status", "activated", "agentId", agentId));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{agentId}/deactivate")
    public ResponseEntity<Map<String, String>> deactivateAgent(
            @PathVariable String agentId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {

        agentRegistry.deactivateAgent(tenantId, agentId);
        agentDefinitionRepository.findByAgentIdAndTenantId(agentId, tenantId)
                .ifPresent(agent -> {
                    agent.setActive(false);
                    agentDefinitionRepository.save(agent);
                });
        return ResponseEntity.ok(Map.of("status", "deactivated", "agentId", agentId));
    }

    @PostMapping("/{agentId}/publish")
    public ResponseEntity<Map<String, String>> publishToMarketplace(
            @PathVariable String agentId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {

        return agentDefinitionRepository.findByAgentIdAndTenantId(agentId, tenantId)
                .map(agent -> {
                    agent.setSource(AgentSource.MARKETPLACE);
                    agentDefinitionRepository.save(agent);
                    log.info("Agent published to marketplace: {}", agentId);
                    return ResponseEntity.ok(Map.of("status", "published", "agentId", agentId));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/import/{marketplaceAgentId}")
    public ResponseEntity<AgentDefinition> importFromMarketplace(
            @PathVariable String marketplaceAgentId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {

        // Find marketplace agent (tenantId is null or source is MARKETPLACE)
        List<AgentDefinition> marketplace = agentDefinitionRepository.findAll().stream()
                .filter(a -> a.getAgentId().equals(marketplaceAgentId))
                .filter(a -> a.getSource() == AgentSource.MARKETPLACE)
                .toList();

        if (marketplace.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AgentDefinition source = marketplace.get(0);
        AgentDefinition imported = AgentDefinition.builder()
                .tenantId(tenantId)
                .agentId(source.getAgentId() + "-imported")
                .displayName(source.getDisplayName())
                .description(source.getDescription())
                .source(AgentSource.TENANT)
                .systemPrompt(source.getSystemPrompt())
                .assignedSkills(source.getAssignedSkills())
                .assignedPlugins(source.getAssignedPlugins())
                .llmModel(source.getLlmModel())
                .maxToolIterations(source.getMaxToolIterations())
                .maxTokenBudget(source.getMaxTokenBudget())
                .reviewer(source.isReviewer())
                .createdBy("marketplace-import")
                .build();

        AgentDefinition saved = agentDefinitionRepository.save(imported);
        agentRegistry.registerTenantAgent(tenantId, saved);

        return ResponseEntity.status(201).body(saved);
    }

    @DeleteMapping("/{agentDbId}")
    public ResponseEntity<Void> deleteAgent(
            @PathVariable UUID agentDbId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {

        agentDefinitionRepository.findById(agentDbId)
                .filter(a -> tenantId.equals(a.getTenantId()))
                .ifPresent(agent -> {
                    agentRegistry.deactivateAgent(tenantId, agent.getAgentId());
                    agentDefinitionRepository.delete(agent);
                });
        return ResponseEntity.noContent().build();
    }

    public record CreateAgentRequest(
            String agentId,
            String displayName,
            String description,
            String systemPrompt,
            List<String> assignedSkills,
            List<String> assignedPlugins,
            String llmModel,
            int maxToolIterations,
            int maxTokenBudget,
            boolean isReviewer
    ) {}

    public record UpdateAgentRequest(
            String displayName,
            String description,
            String systemPrompt,
            List<String> assignedSkills,
            List<String> assignedPlugins,
            String llmModel
    ) {}
}
