package com.seal.hackathon.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Maps to the `UserEventRole` table.
 * Pure N-N "who is ALLOWED to be what role" per event:
 * EVENT_COORDINATOR, MENTOR, JUDGE.
 *
 * Participants (FPT_STUDENT / EXTERNAL_STUDENT) are NOT stored here —
 * their identity comes from User.user_type and their team role from TeamMember.member_role.
 *
 * The actual work assignment (which round/track a judge scores, which track
 * a mentor supports) lives in JudgeAssignment / MentorAssignment.
 *
 * event_id is null for system-wide roles (e.g. a coordinator managing all events).
 */
@Entity
@Table(name = "UserEventRole", uniqueConstraints = {
    @UniqueConstraint(name = "uq_user_role_event", columnNames = {"user_id", "role_id", "event_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEventRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    // Nullable: NULL for system-wide roles (SYSTEM_ADMIN); set for event-scoped roles.
    // Who granted the role and when is captured in SystemLog (action = GRANT_ROLE).
    @Column(name = "event_id")
    private Integer eventId;
}
