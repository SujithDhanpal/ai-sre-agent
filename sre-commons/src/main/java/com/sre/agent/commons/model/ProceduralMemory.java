package com.sre.agent.commons.model;

import com.sre.agent.commons.enums.MemorySource;
import com.sre.agent.commons.enums.ProceduralType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "procedural_memory", indexes = {
        @Index(name = "idx_procedural_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_procedural_type", columnList = "type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProceduralMemory extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ProceduralType type;

    @Column(name = "trigger_pattern", columnDefinition = "text", nullable = false)
    private String triggerPattern;

    @Column(name = "instruction", columnDefinition = "text", nullable = false)
    private String instruction;

    @Column(name = "reasoning", columnDefinition = "text")
    private String reasoning;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private MemorySource source;

    @Column(name = "source_incident_id")
    private UUID sourceIncidentId;

    @Column(name = "effectiveness_score")
    @Builder.Default
    private double effectivenessScore = 0.5;

    @Column(name = "times_applied")
    @Builder.Default
    private int timesApplied = 0;

    @Column(name = "times_effective")
    @Builder.Default
    private int timesEffective = 0;

    @Column(name = "active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_by")
    private String createdBy;
}
