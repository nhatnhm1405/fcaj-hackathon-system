package com.seal.hackathon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Maps to the `SystemLog` table — platform/admin events, kept SEPARATE from AuditLog.
 *
 *   AuditLog  = competition business actions (scored, disqualified, published).
 *   SystemLog = admin actions on the platform itself (user mgmt, role grants,
 *               template changes, auth events). Minimal by design: the human-
 *               readable context goes into `detail`, no entity FK fan-out.
 *
 * Visibility: SystemLog is SYSTEM_ADMIN-only.
 */
@Entity
@Table(name = "SystemLog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Integer logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actor;

    // CREATE_USER, LOCK_USER, RESET_PASSWORD, GRANT_ROLE, REVOKE_ROLE, UPDATE_TEMPLATE, LOGIN_FAILED...
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    // Human-readable context, e.g. "granted EVENT_COORDINATOR to user#5"
    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
