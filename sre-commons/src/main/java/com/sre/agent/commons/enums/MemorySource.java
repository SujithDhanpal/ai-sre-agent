package com.sre.agent.commons.enums;

public enum MemorySource {
    AUTO_LEARNED,         // System discovered from incident resolution
    HUMAN_PROVIDED,       // Explicitly entered by a human
    AGENT_DISCOVERED,     // An agent discovered during investigation
    IMPORTED              // Imported from external source
}
