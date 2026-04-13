package com.sre.agent.commons.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "conversation_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessage extends BaseEntity {

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "content", columnDefinition = "text", nullable = false)
    private String content;
}
