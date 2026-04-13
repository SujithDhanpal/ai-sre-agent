package com.sre.agent.api.controller;

import com.sre.agent.commons.enums.MemorySource;
import com.sre.agent.commons.model.EpisodicMemory;
import com.sre.agent.commons.model.ProceduralMemory;
import com.sre.agent.commons.model.SemanticMemory;
import com.sre.agent.memory.repository.EpisodicMemoryRepository;
import com.sre.agent.memory.repository.ProceduralMemoryRepository;
import com.sre.agent.memory.repository.SemanticMemoryRepository;
import com.sre.agent.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/memory")
@RequiredArgsConstructor
@Slf4j
public class MemoryController {

    private final EpisodicMemoryRepository episodicRepo;
    private final SemanticMemoryRepository semanticRepo;
    private final ProceduralMemoryRepository proceduralRepo;
    private final MemoryService memoryService;

    @GetMapping("/episodic")
    public ResponseEntity<List<EpisodicMemory>> listEpisodicMemory(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
        return ResponseEntity.ok(episodicRepo.findByTenantIdOrderByCreatedAtDesc(tenantId));
    }

    @GetMapping("/semantic")
    public ResponseEntity<List<SemanticMemory>> listSemanticMemory(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestParam(required = false) String namespace) {

        if (namespace != null) {
            return ResponseEntity.ok(semanticRepo.findByTenantIdAndNamespace(tenantId, namespace));
        }
        return ResponseEntity.ok(semanticRepo.findByTenantIdOrderByCreatedAtDesc(tenantId));
    }

    @PostMapping("/semantic")
    public ResponseEntity<SemanticMemory> addSemanticMemory(
            @RequestBody AddSemanticMemoryRequest request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {

        SemanticMemory memory = memoryService.storeOrUpdateSemanticMemory(
                tenantId, request.namespace(), request.key(),
                request.value(), MemorySource.HUMAN_PROVIDED, userId);
        return ResponseEntity.status(201).body(memory);
    }

    @GetMapping("/procedural")
    public ResponseEntity<List<ProceduralMemory>> listProceduralMemory(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
        return ResponseEntity.ok(proceduralRepo.findByTenantIdAndActiveTrue(tenantId));
    }

    @GetMapping("/corrections")
    public ResponseEntity<List<EpisodicMemory>> listCorrections(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
        return ResponseEntity.ok(episodicRepo.findByTenantIdAndDiagnosisWasCorrectFalse(tenantId));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getMemoryStats(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {

        long episodicCount = episodicRepo.findByTenantIdOrderByCreatedAtDesc(tenantId).size();
        long semanticCount = semanticRepo.findByTenantIdOrderByCreatedAtDesc(tenantId).size();
        long proceduralCount = proceduralRepo.findByTenantIdAndActiveTrue(tenantId).size();
        long correctionsCount = episodicRepo.findByTenantIdAndDiagnosisWasCorrectFalse(tenantId).size();

        return ResponseEntity.ok(Map.of(
                "episodicMemories", episodicCount,
                "semanticMemories", semanticCount,
                "proceduralRules", proceduralCount,
                "humanCorrections", correctionsCount
        ));
    }

    public record AddSemanticMemoryRequest(
            String namespace,
            String key,
            String value
    ) {}
}
