package com.sre.agent.api.controller;

import com.sre.agent.commons.model.AgentSkillTool;
import com.sre.agent.core.repository.AgentSkillToolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agents/{agentId}/tools")
@RequiredArgsConstructor
@Slf4j
public class AgentSkillToolController {

    private final AgentSkillToolRepository agentSkillToolRepo;

    /**
     * List skills registered as tools for an agent.
     * GET /api/v1/agents/{agentId}/tools
     */
    @GetMapping
    public ResponseEntity<List<AgentSkillTool>> listTools(@PathVariable String agentId) {
        return ResponseEntity.ok(agentSkillToolRepo.findByAgentId(agentId));
    }

    /**
     * Register a skill as a tool for an agent.
     * POST /api/v1/agents/{agentId}/tools
     * { "skillId": "staging-db" }
     */
    @PostMapping
    public ResponseEntity<AgentSkillTool> registerTool(
            @PathVariable String agentId,
            @RequestBody RegisterToolRequest request) {

        log.info("Registering skill '{}' as tool for agent '{}'", request.skillId(), agentId);

        // Check if already registered
        if (agentSkillToolRepo.findByAgentIdAndSkillId(agentId, request.skillId()).isPresent()) {
            return ResponseEntity.ok(agentSkillToolRepo.findByAgentIdAndSkillId(agentId, request.skillId()).get());
        }

        AgentSkillTool tool = agentSkillToolRepo.save(AgentSkillTool.builder()
                .agentId(agentId)
                .skillId(request.skillId())
                .build());

        return ResponseEntity.status(201).body(tool);
    }

    /**
     * Deregister a skill from an agent's tools.
     * DELETE /api/v1/agents/{agentId}/tools/{skillId}
     */
    @DeleteMapping("/{skillId}")
    @Transactional
    public ResponseEntity<Map<String, String>> deregisterTool(
            @PathVariable String agentId,
            @PathVariable String skillId) {

        log.info("Deregistering skill '{}' from agent '{}'", skillId, agentId);
        agentSkillToolRepo.deleteByAgentIdAndSkillId(agentId, skillId);
        return ResponseEntity.ok(Map.of("status", "removed", "agentId", agentId, "skillId", skillId));
    }

    public record RegisterToolRequest(String skillId) {}
}
