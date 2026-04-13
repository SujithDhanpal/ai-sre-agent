package com.sre.agent.memory.service;

import com.sre.agent.commons.model.EpisodicMemory;
import com.sre.agent.commons.model.ProceduralMemory;
import com.sre.agent.commons.model.SemanticMemory;

import java.util.List;

public record MemoryContext(
        List<EpisodicMemory> similarIncidents,
        List<ProceduralMemory> applicableRules,
        List<SemanticMemory> systemKnowledge
) {
    public boolean hasRelevantMemory() {
        return !similarIncidents.isEmpty() || !applicableRules.isEmpty() || !systemKnowledge.isEmpty();
    }

    public String toPromptContext() {
        var sb = new StringBuilder();

        if (!similarIncidents.isEmpty()) {
            sb.append("## Similar Past Incidents\n\n");
            for (var ep : similarIncidents) {
                sb.append("- **").append(ep.getIncidentSummary()).append("**\n");
                sb.append("  Root cause: ").append(ep.getDiagnosisSummary()).append("\n");
                sb.append("  Resolution: ").append(ep.getResolutionSummary()).append("\n");
                if (ep.getDiagnosisWasCorrect() != null && !ep.getDiagnosisWasCorrect()) {
                    sb.append("  ⚠️ CORRECTION: ").append(ep.getHumanCorrection()).append("\n");
                }
                sb.append("\n");
            }
        }

        if (!applicableRules.isEmpty()) {
            sb.append("## Correction Rules (MUST FOLLOW)\n\n");
            for (var rule : applicableRules) {
                sb.append("- **RULE**: ").append(rule.getInstruction()).append("\n");
                sb.append("  Trigger: ").append(rule.getTriggerPattern()).append("\n");
                sb.append("  Reason: ").append(rule.getReasoning()).append("\n");
                sb.append("  Effectiveness: ").append(String.format("%.0f%%", rule.getEffectivenessScore() * 100)).append("\n\n");
            }
        }

        if (!systemKnowledge.isEmpty()) {
            sb.append("## System Knowledge\n\n");
            for (var sk : systemKnowledge) {
                sb.append("- **").append(sk.getNamespace()).append("/").append(sk.getKey()).append("**: ");
                sb.append(sk.getValue()).append("\n");
            }
        }

        return sb.toString();
    }

    public static MemoryContext empty() {
        return new MemoryContext(List.of(), List.of(), List.of());
    }
}
