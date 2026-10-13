package com.seal.hackathon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Maps to the `AuditLog` table — competition BUSINESS actions, kept SEPARATE from
 * SystemLog.
 *
 *   AuditLog  = competition business actions (create/complete event, approve/
 *               disqualify team, update score, publish result, assign judge/mentor,
 *               draw tracks...). Carries target_type/target_id, an optional reason,
 *               and a metadata_json before/after snapshot.
 *   SystemLog = admin actions on the platform itself (user mgmt, role grants, auth).
 *
 * Visibility: AuditLog is readable by SYSTEM_ADMIN and by the owning event's
 * EVENT_COORDINATOR.
 */
@Entity
@Table(name = "AuditLog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Integer logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actor;

    // CREATE_EVENT, COMPLETE_EVENT, APPROVE_TEAM, DISQUALIFY_TEAM, UPDATE_SCORE,
    // PUBLISH_RESULT, ASSIGN_JUDGE, ASSIGN_MENTOR, DRAW_TRACKS...
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    // TEAM, SUBMISSION, SCORE, EVENT, ROUND...
    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Integer targetId;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    // JSON snapshot of before/after state.
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
