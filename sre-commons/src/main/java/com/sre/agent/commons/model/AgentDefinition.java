package com.sre.agent.commons.model;

import com.sre.agent.commons.enums.AgentSource;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.List;

@Entity
@Table(name = "agent_definitions", indexes = {
        @Index(name = "idx_agent_def_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_agent_def_agent_id", columnList = "agent_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDefinition extends BaseEntity {

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private AgentSource source;

    @Column(name = "system_prompt", columnDefinition = "text", nullable = false)
    private String systemPrompt;

    @Type(JsonBinaryType.class)
    @Column(name = "assigned_skills", columnDefinition = "jsonb")
    private List<String> assignedSkills;

    @Type(JsonBinaryType.class)
    @Column(name = "assigned_plugins", columnDefinition = "jsonb")
    private List<String> assignedPlugins;

    @Column(name = "llm_model")
    private String llmModel;

    @Column(name = "max_tool_iterations")
    @Builder.Default
    private int maxToolIterations = 15;

    @Column(name = "max_token_budget")
    @Builder.Default
    private int maxTokenBudget = 50000;

    @Column(name = "is_reviewer")
    @Builder.Default
    private boolean reviewer = false;

    @Column(name = "active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "version")
    @Builder.Default
    private String version = "1.0.0";

    @Column(name = "created_by")
    private String createdBy;
}
