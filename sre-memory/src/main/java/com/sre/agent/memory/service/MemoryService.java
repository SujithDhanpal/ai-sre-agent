package com.sre.agent.memory.service;

import com.sre.agent.commons.enums.MemorySource;
import com.sre.agent.commons.enums.ProceduralType;
import com.sre.agent.commons.model.EpisodicMemory;
import com.sre.agent.commons.model.Incident;
import com.sre.agent.commons.model.ProceduralMemory;
import com.sre.agent.commons.model.SemanticMemory;
import com.sre.agent.memory.repository.EpisodicMemoryRepository;
import com.sre.agent.memory.repository.ProceduralMemoryRepository;
import com.sre.agent.memory.repository.SemanticMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryService {

    private final EpisodicMemoryRepository episodicRepo;
    private final SemanticMemoryRepository semanticRepo;
    private final ProceduralMemoryRepository proceduralRepo;

    public MemoryContext gatherRelevantMemory(String tenantId, Incident incident) {
        List<EpisodicMemory> similarIncidents = findSimilarIncidents(tenantId, incident);
        List<ProceduralMemory> rules = findApplicableRules(tenantId, incident);
        List<SemanticMemory> knowledge = findSystemKnowledge(tenantId, incident);

        var context = new MemoryContext(similarIncidents, rules, knowledge);
        log.info("Gathered memory context for incident {}: {} similar incidents, {} rules, {} knowledge entries",
                incident.getId(), similarIncidents.size(), rules.size(), knowledge.size());
        return context;
    }

    private List<EpisodicMemory> findSimilarIncidents(String tenantId, Incident incident) {
        // For now, keyword-based search; will be upgraded to vector search when embeddings are wired
        List<EpisodicMemory> results = new ArrayList<>();
        if (incident.getAffectedServices() != null) {
            for (String service : incident.getAffectedServices()) {
                results.addAll(proceduralRepo.findMatchingRules(tenantId, service).stream()
                        .map(r -> r.getSourceIncidentId())
                        .filter(id -> id != null)
                        .flatMap(id -> episodicRepo.findByIncidentId(id).stream())
                        .toList());
            }
        }
        // Also get recent episodic memories
        var recent = episodicRepo.findByTenantIdOrderByCreatedAtDesc(tenantId);
        if (recent.size() > 10) recent = recent.subList(0, 10);
        results.addAll(recent);
        return results.stream().distinct().limit(5).toList();
    }

    private List<ProceduralMemory> findApplicableRules(String tenantId, Incident incident) {
        List<ProceduralMemory> rules = new ArrayList<>();
        if (incident.getAffectedServices() != null) {
            for (String service : incident.getAffectedServices()) {
                rules.addAll(proceduralRepo.findMatchingRules(tenantId, service));
            }
        }
        if (incident.getTitle() != null) {
            String[] keywords = incident.getTitle().split("\\s+");
            for (String keyword : keywords) {
                if (keyword.length() > 3) {
                    rules.addAll(proceduralRepo.findMatchingRules(tenantId, keyword));
                }
            }
        }
        return rules.stream().distinct().limit(10).toList();
    }

    private List<SemanticMemory> findSystemKnowledge(String tenantId, Incident incident) {
        List<SemanticMemory> knowledge = new ArrayList<>();
        if (incident.getAffectedServices() != null) {
            for (String service : incident.getAffectedServices()) {
                semanticRepo.findByTenantIdAndNamespaceAndKey(tenantId, "service-topology", service)
                        .ifPresent(knowledge::add);
                semanticRepo.findByTenantIdAndNamespaceAndKey(tenantId, "baselines", service)
                        .ifPresent(knowledge::add);
            }
        }
        return knowledge;
    }

    // --- Write operations ---

    @Transactional
    public EpisodicMemory storeEpisodicMemory(EpisodicMemory memory) {
        return episodicRepo.save(memory);
    }

    @Transactional
    public SemanticMemory storeOrUpdateSemanticMemory(String tenantId, String namespace, String key,
                                                       String value, MemorySource source, String createdBy) {
        return semanticRepo.findByTenantIdAndNamespaceAndKey(tenantId, namespace, key)
                .map(existing -> {
                    existing.setValue(value);
                    existing.setLastVerified(Instant.now());
                    existing.setUpdatedAt(Instant.now());
                    return semanticRepo.save(existing);
                })
                .orElseGet(() -> semanticRepo.save(SemanticMemory.builder()
                        .tenantId(tenantId)
                        .namespace(namespace)
                        .key(key)
                        .value(value)
                        .source(source)
                        .createdBy(createdBy)
                        .build()));
    }

    @Transactional
    public ProceduralMemory storeCorrectionRule(String tenantId, String triggerPattern,
                                                 String instruction, String reasoning,
                                                 java.util.UUID sourceIncidentId, String createdBy) {
        return proceduralRepo.save(ProceduralMemory.builder()
                .tenantId(tenantId)
                .type(ProceduralType.CORRECTION_RULE)
                .triggerPattern(triggerPattern)
                .instruction(instruction)
                .reasoning(reasoning)
                .source(MemorySource.HUMAN_PROVIDED)
                .sourceIncidentId(sourceIncidentId)
                .createdBy(createdBy)
                .build());
    }

    @Transactional
    public ProceduralMemory storePlaybook(String tenantId, String triggerPattern,
                                           String instruction, String createdBy) {
        return proceduralRepo.save(ProceduralMemory.builder()
                .tenantId(tenantId)
                .type(ProceduralType.PLAYBOOK)
                .triggerPattern(triggerPattern)
                .instruction(instruction)
                .source(MemorySource.HUMAN_PROVIDED)
                .createdBy(createdBy)
                .build());
    }

    @Transactional
    public void updateRuleEffectiveness(java.util.UUID ruleId, boolean wasEffective) {
        proceduralRepo.findById(ruleId).ifPresent(rule -> {
            rule.setTimesApplied(rule.getTimesApplied() + 1);
            if (wasEffective) {
                rule.setTimesEffective(rule.getTimesEffective() + 1);
            }
            rule.setEffectivenessScore(
                    (double) rule.getTimesEffective() / rule.getTimesApplied());
            proceduralRepo.save(rule);
        });
    }
}
