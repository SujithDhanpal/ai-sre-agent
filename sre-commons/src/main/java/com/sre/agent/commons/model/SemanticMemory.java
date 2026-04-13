package com.sre.agent.commons.model;

import com.sre.agent.commons.enums.MemorySource;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "semantic_memory", indexes = {
        @Index(name = "idx_semantic_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_semantic_namespace_key", columnList = "namespace, key")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticMemory extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "namespace", nullable = false)
    private String namespace;

    @Column(name = "key", nullable = false)
    private String key;

    @Column(name = "value", columnDefinition = "text", nullable = false)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private MemorySource source;

    @Column(name = "confidence")
    @Builder.Default
    private double confidence = 1.0;

    @Column(name = "last_verified")
    private Instant lastVerified;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;
}
