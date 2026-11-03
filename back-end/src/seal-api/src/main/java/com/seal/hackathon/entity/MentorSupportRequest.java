package com.seal.hackathon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A team (via its leader) asks its track's mentor for help. Support happens
 * IN PERSON — this is a lightweight ticket, not a chat: the mentor is notified,
 * comes to help, and marks it resolved. The leader may cancel while it is open.
 *
 * Business rules (enforced in the service):
 * - Only the team LEADER creates/cancels.
 * - At most ONE OPEN request per team at a time.
 * - Only a mentor assigned to the team's track can resolve it.
 */
@Entity
@Table(name = "MentorSupportRequest")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorSupportRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Integer requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    // The team's track at request time — lets a mentor query "requests in my track".
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    // The leader who raised it.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_user_id", nullable = false)
    private User requester;

    // RULES | TECHNICAL | DIRECTION | OTHER
    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // OPEN | RESOLVED | CANCELLED
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Mentor who marked it resolved (null until resolved).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "OPEN";
        }
    }
}
