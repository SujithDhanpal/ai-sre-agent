package com.sre.agent.commons.model;

import com.sre.agent.commons.enums.AgentSource;
import com.sre.agent.commons.enums.SkillType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.List;

@Entity
@Table(name = "skill_definitions", indexes = {
        @Index(name = "idx_skill_def_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_skill_def_skill_id", columnList = "skill_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDefinition extends BaseEntity {

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "skill_id", nullable = false)
    private String skillId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private AgentSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_type", nullable = false)
    private SkillType skillType;

    @Column(name = "markdown_content", columnDefinition = "text")
    private String markdownContent;

    @Column(name = "executor_class")
    private String executorClass;

    @Type(JsonBinaryType.class)
    @Column(name = "steps", columnDefinition = "jsonb")
    private List<java.util.Map<String, Object>> steps;

    @Column(name = "input_schema", columnDefinition = "text")
    private String inputSchema;

    @Column(name = "output_schema", columnDefinition = "text")
    private String outputSchema;

    @Type(JsonBinaryType.class)
    @Column(name = "required_plugins", columnDefinition = "jsonb")
    private List<String> requiredPlugins;

    @Column(name = "git_repo_url")
    private String gitRepoUrl;

    @Column(name = "git_file_path")
    private String gitFilePath;

    @Column(name = "git_commit_sha")
    private String gitCommitSha;

    @Column(name = "active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "version")
    @Builder.Default
    private String version = "1.0.0";

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;
}
