package com.sre.agent.api.controller;

import com.sre.agent.commons.model.SkillDefinition;
// TenantContext removed — single-tenant mode
import com.sre.agent.skill.engine.SkillEngine;
import com.sre.agent.skill.registry.SkillRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
@Slf4j
public class SkillController {

    private final SkillRegistryService skillRegistryService;

    @GetMapping
    public ResponseEntity<List<SkillDefinition>> listSkills(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
        return ResponseEntity.ok(skillRegistryService.getAvailableSkills(tenantId));
    }

    @GetMapping("/{skillId}")
    public ResponseEntity<SkillDefinition> getSkill(
            @PathVariable String skillId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
        return skillRegistryService.getSkill(tenantId, skillId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/upload")
    public ResponseEntity<SkillDefinition> uploadSkill(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) throws IOException {

        String markdownContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        log.info("Skill file uploaded: name={}, size={}, tenant={}", file.getOriginalFilename(), file.getSize(), tenantId);

        SkillDefinition skill = skillRegistryService.registerMarkdownSkill(tenantId, markdownContent, userId);
        return ResponseEntity.status(201).body(skill);
    }

    @PostMapping
    public ResponseEntity<SkillDefinition> createSkill(
            @RequestBody CreateSkillRequest request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {

        SkillDefinition skill = skillRegistryService.registerMarkdownSkill(
                tenantId, request.markdownContent(), userId);
        return ResponseEntity.status(201).body(skill);
    }

    @PostMapping("/{skillId}/execute")
    public ResponseEntity<SkillEngine.SkillExecutionResult> executeSkill(
            @PathVariable String skillId,
            @RequestBody(required = false) Map<String, String> params,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestParam(value = "incidentId", required = false) String incidentId) {

        SkillEngine.SkillExecutionResult result = skillRegistryService.executeSkill(
                tenantId, skillId, params != null ? params : Map.of(), incidentId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{skillDbId}")
    public ResponseEntity<Void> deleteSkill(
            @PathVariable UUID skillDbId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {

        skillRegistryService.deleteSkill(tenantId, skillDbId);
        return ResponseEntity.noContent().build();
    }

    public record CreateSkillRequest(String markdownContent) {}
}
