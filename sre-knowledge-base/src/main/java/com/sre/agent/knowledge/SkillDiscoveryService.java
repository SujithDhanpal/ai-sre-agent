package com.sre.agent.knowledge;

import com.sre.agent.commons.model.SkillDefinition;
import com.sre.agent.core.repository.SkillDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Skill Discovery Layer.
 *
 * When you have 100+ skills, you can't bind all of them as tools to the agent —
 * it would exhaust the context window. Instead, this service finds the top N
 * relevant skills for a given investigation query.
 *
 * Two modes:
 *   1. Keyword matching (works now, no dependencies)
 *   2. Vector similarity via embeddings (future, when pgvector embeddings are available)
 *
 * Usage:
 *   List<SkillDefinition> relevant = skillDiscovery.findRelevantSkills("connection pool timeout", 5);
 *   // Returns top 5 skills whose name/description match the query
 *   // These get bound as tools to the agent for THIS specific investigation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillDiscoveryService {

    private final SkillDefinitionRepository skillRepo;

    /**
     * Find skills relevant to the investigation query.
     * Uses keyword matching on skill name + description.
     */
    public List<SkillDefinition> findRelevantSkills(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();

        List<SkillDefinition> allSkills = skillRepo.findActiveSkillsForTenant("default");

        if (allSkills.size() <= limit) {
            return allSkills;
        }

        // Score each skill by keyword overlap with the query
        Set<String> queryWords = tokenize(query);

        List<ScoredSkill> scored = allSkills.stream()
                .map(skill -> {
                    String text = (skill.getDisplayName() + " " +
                            (skill.getDescription() != null ? skill.getDescription() : "")).toLowerCase();
                    Set<String> skillWords = tokenize(text);

                    long overlap = queryWords.stream().filter(skillWords::contains).count();
                    double score = queryWords.isEmpty() ? 0 : (double) overlap / queryWords.size();

                    return new ScoredSkill(skill, score);
                })
                .sorted(Comparator.comparingDouble(ScoredSkill::score).reversed())
                .toList();

        List<SkillDefinition> result = scored.stream()
                .limit(limit)
                .filter(s -> s.score() > 0)
                .map(ScoredSkill::skill)
                .collect(Collectors.toList());

        log.debug("Skill discovery for '{}': {} total skills, {} matched (top {})",
                query, allSkills.size(), result.size(), limit);

        return result;
    }

    /**
     * Find skills using vector similarity search.
     * Uses embeddings auto-generated when skills are registered.
     */
    public List<SkillDefinition> findRelevantSkillsByEmbedding(float[] queryEmbedding, int limit) {
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            log.debug("No query embedding available, falling back to keyword search");
            return List.of();
        }

        // Check if any skills have embeddings
        List<SkillDefinition> allSkills = skillRepo.findActiveSkillsForTenant("default");
        boolean hasEmbeddings = allSkills.stream().anyMatch(s -> s.getEmbedding() != null);

        if (!hasEmbeddings) {
            log.debug("No skill embeddings available, falling back to keyword search");
            return List.of();
        }

        // Score by cosine similarity
        List<ScoredSkill> scored = allSkills.stream()
                .filter(s -> s.getEmbedding() != null)
                .map(skill -> new ScoredSkill(skill, cosineSimilarity(queryEmbedding, skill.getEmbedding())))
                .sorted(Comparator.comparingDouble(ScoredSkill::score).reversed())
                .toList();

        List<SkillDefinition> result = scored.stream()
                .limit(limit)
                .filter(s -> s.score() > 0.3) // minimum similarity threshold
                .map(ScoredSkill::skill)
                .collect(Collectors.toList());

        log.debug("Vector skill discovery: {} skills with embeddings, {} matched (threshold 0.3)",
                scored.size(), result.size());
        return result;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return (normA == 0 || normB == 0) ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().replaceAll("[^a-z0-9 ]", " ").split("\\s+"))
                .filter(w -> w.length() > 2)
                .collect(Collectors.toSet());
    }

    private record ScoredSkill(SkillDefinition skill, double score) {}
}
