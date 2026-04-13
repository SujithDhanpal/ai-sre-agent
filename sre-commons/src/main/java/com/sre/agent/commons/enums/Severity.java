package com.sre.agent.commons.enums;

public enum Severity {
    P1,  // Critical — service down, revenue impacted
    P2,  // High — major degradation, users impacted
    P3,  // Medium — partial degradation, workaround available
    P4   // Low — minor issue, no immediate user impact
}
