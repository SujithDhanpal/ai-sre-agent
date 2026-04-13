package com.sre.agent.commons.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "agent_skill_tools", uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "skill_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSkillTool extends BaseEntity {

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "skill_id", nullable = false)
    private String skillId;
}
