package com.sre.agent.commons.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "investigation_context")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationContext extends BaseEntity {

    @Column(name = "incident_id", nullable = false, unique = true)
    private UUID incidentId;

    @Column(name = "log_context", columnDefinition = "text")
    private String logContext;

    @Column(name = "memory_context", columnDefinition = "text")
    private String memoryContext;

    @Type(JsonBinaryType.class)
    @Column(name = "services", columnDefinition = "jsonb")
    private List<String> services;
}
