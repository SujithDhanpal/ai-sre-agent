package com.sre.agent.skill.registry;

import com.sre.agent.commons.enums.AgentSource;
import com.sre.agent.commons.enums.SkillType;
import com.sre.agent.commons.model.SkillDefinition;
import com.sre.agent.core.repository.SkillDefinitionRepository;
import com.sre.agent.memory.service.EmbeddingService;
import com.sre.agent.skill.engine.SkillEngine;
import com.sre.agent.skill.parser.ParsedSkill;
import com.sre.agent.skill.parser.SkillMarkdownParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillRegistryService {

    private final SkillDefinitionRepository skillRepository;
    private final SkillMarkdownParser parser;
    private final EmbeddingService embeddingService;
    private final SkillEngine skillEngine;

    public List<SkillDefinition> getAvailableSkills(String tenantId) {
        return skillRepository.findActiveSkillsForTenant(tenantId);
    }

    public Optional<SkillDefinition> getSkill(String tenantId, String skillId) {
        return skillRepository.findBySkillIdAndTenantId(skillId, tenantId)
                .or(() -> skillRepository.findBySkillIdAndTenantIdIsNull(skillId));
    }

    @Transactional
    public SkillDefinition registerMarkdownSkill(String tenantId, String markdownContent, String createdBy) {
        ParsedSkill parsed = parser.parse(markdownContent);
        ParsedSkill.SkillMetadata meta = parsed.metadata();

        log.info("Registering markdown skill: id={}, name={}, tenant={}", meta.id(), meta.name(), tenantId);

        // Generate embedding from skill name + description for discovery
        String embeddingText = meta.name() + " " + (meta.description() != null ? meta.description() : "");
        float[] embedding = embeddingService.generateEmbedding("default", embeddingText);

        // Check for existing
        Optional<SkillDefinition> existing = skillRepository.findBySkillIdAndTenantId(meta.id(), tenantId);
        if (existing.isPresent()) {
            SkillDefinition skill = existing.get();
            skill.setMarkdownContent(markdownContent);
            skill.setDisplayName(meta.name());
            skill.setDescription(meta.description());
            skill.setRequiredPlugins(meta.requiredPlugins());
            skill.setVersion(meta.version());
            if (embedding.length > 0) skill.setEmbedding(embedding);
            return skillRepository.save(skill);
        }

        SkillDefinition skill = SkillDefinition.builder()
                .tenantId(tenantId)
                .skillId(meta.id())
                .displayName(meta.name())
                .description(meta.description())
                .source(tenantId == null ? AgentSource.PLATFORM : AgentSource.TENANT)
                .skillType(SkillType.PROMPT_BASED)
                .markdownContent(markdownContent)
                .requiredPlugins(meta.requiredPlugins())
                .version(meta.version())
                .createdBy(createdBy)
                .embedding(embedding.length > 0 ? embedding : null)
                .build();

        return skillRepository.save(skill);
    }

    @Transactional
    public SkillDefinition registerPlatformSkill(String markdownContent) {
        ParsedSkill parsed = parser.parse(markdownContent);
        ParsedSkill.SkillMetadata meta = parsed.metadata();

        String embeddingText = meta.name() + " " + (meta.description() != null ? meta.description() : "");
        float[] embedding = embeddingService.generateEmbedding("default", embeddingText);

        Optional<SkillDefinition> existing = skillRepository.findBySkillIdAndTenantIdIsNull(meta.id());
        if (existing.isPresent()) {
            SkillDefinition skill = existing.get();
            skill.setMarkdownContent(markdownContent);
            if (embedding.length > 0) skill.setEmbedding(embedding);
            return skillRepository.save(skill);
        }

        SkillDefinition skill = SkillDefinition.builder()
                .skillId(meta.id())
                .displayName(meta.name())
                .description(meta.description())
                .source(AgentSource.PLATFORM)
                .skillType(SkillType.PROMPT_BASED)
                .markdownContent(markdownContent)
                .requiredPlugins(meta.requiredPlugins())
                .version(meta.version())
                .createdBy("platform")
                .embedding(embedding.length > 0 ? embedding : null)
                .build();

        return skillRepository.save(skill);
    }

    public SkillEngine.SkillExecutionResult executeSkill(String tenantId, String skillId,
                                                          Map<String, String> params, String incidentId) {
        SkillDefinition skill = getSkill(tenantId, skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));

        if (skill.getMarkdownContent() == null || skill.getMarkdownContent().isBlank()) {
            throw new IllegalStateException("Skill has no markdown content: " + skillId);
        }

        return skillEngine.execute(skill.getMarkdownContent(), params, tenantId, incidentId);
    }

    @Transactional
    public void deleteSkill(String tenantId, UUID skillDbId) {
        skillRepository.findById(skillDbId).ifPresent(skill -> {
            if (tenantId.equals(skill.getTenantId())) {
                skillRepository.delete(skill);
                log.info("Deleted skill: {}", skill.getSkillId());
            }
        });
    }
}
