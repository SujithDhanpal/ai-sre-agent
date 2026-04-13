package com.sre.agent.commons.enums;

public enum SkillType {
    PROMPT_BASED,    // Markdown with prompt template — anyone can create via UI
    CODE_BASED,      // Java class implementing SkillExecutor — developers deploy
    COMPOSITE        // Chain of other skills — anyone can compose via UI
}
