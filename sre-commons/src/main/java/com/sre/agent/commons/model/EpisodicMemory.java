package com.sre.agent.commons.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "episodic_memory", indexes = {
        @Index(name = "idx_episodic_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_episodic_incident_id", columnList = "incident_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodicMemory extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(name = "incident_summary", columnDefinition = "text")
    private String incidentSummary;

    @Column(name = "diagnosis_summary", columnDefinition = "text")
    private String diagnosisSummary;

    @Column(name = "resolution_summary", columnDefinition = "text")
    private String resolutionSummary;

    @Column(name = "diagnosis_was_correct")
    private Boolean diagnosisWasCorrect;

    @Column(name = "fix_worked")
    private Boolean fixWorked;

    @Column(name = "confidence_at_diagnosis")
    private Double confidenceAtDiagnosis;

    @Column(name = "human_correction", columnDefinition = "text")
    private String humanCorrection;

    @Type(JsonBinaryType.class)
    @Column(name = "error_patterns", columnDefinition = "jsonb")
    private List<String> errorPatterns;

    @Type(JsonBinaryType.class)
    @Column(name = "root_cause_categories", columnDefinition = "jsonb")
    private List<String> rootCauseCategories;

    @Type(JsonBinaryType.class)
    @Column(name = "affected_services", columnDefinition = "jsonb")
    private List<String> affectedServices;

    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
