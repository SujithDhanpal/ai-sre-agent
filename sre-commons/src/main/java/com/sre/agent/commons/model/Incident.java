package com.sre.agent.commons.model;

import com.sre.agent.commons.enums.AlertSource;
import com.sre.agent.commons.enums.IncidentStatus;
import com.sre.agent.commons.enums.Severity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "incidents", indexes = {
        @Index(name = "idx_incidents_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_incidents_status", columnList = "status"),
        @Index(name = "idx_incidents_severity", columnList = "severity"),
        @Index(name = "idx_incidents_correlation_id", columnList = "correlation_id", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Incident extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "correlation_id", nullable = false, unique = true)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_source", nullable = false)
    private AlertSource alertSource;

    @Column(name = "external_alert_id")
    private String externalAlertId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IncidentStatus status;

    @Column(name = "title", columnDefinition = "text", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Type(JsonBinaryType.class)
    @Column(name = "raw_alert_payload", columnDefinition = "jsonb")
    private Map<String, Object> rawAlertPayload;

    @Type(JsonBinaryType.class)
    @Column(name = "affected_services", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> affectedServices = new ArrayList<>();

    @Column(name = "environment")
    private String environment;

    @Column(name = "source_plugin")
    private String sourcePlugin;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolution_summary", columnDefinition = "text")
    private String resolutionSummary;

}
