package com.seal.hackathon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A Team's participation record for one specific event/season — mirrors the
 * User/UserEventRole split. Team itself is a stable identity (name, roster);
 * everything season-specific (track, approval status, disqualification)
 * lives here instead, one row per (team, event).
 */
@Entity
@Table(name = "TeamEventEntry", uniqueConstraints = {
    @UniqueConstraint(name = "uq_tee_team_event", columnNames = {"team_id", "event_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamEventEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private HackathonEvent event;

    // Nullable: a team may register without a track and be assigned one later
    // via the coordinator's random track draw during the SETUP phase.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    private Track track;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "disqualified_reason", columnDefinition = "TEXT")
    private String disqualifiedReason;

    @Column(name = "disqualified_at")
    private LocalDateTime disqualifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }
}
