package com.sre.agent.commons.enums;

public enum ProceduralType {
    CORRECTION_RULE,     // "When X, don't conclude Y — check Z first"
    PLAYBOOK,            // Step-by-step procedure from human
    LEARNED_PROCEDURE    // Auto-extracted procedure from past resolutions
}
