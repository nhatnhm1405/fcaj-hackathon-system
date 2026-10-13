package com.seal.hackathon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A frozen, per-participant snapshot of one event's result — deliberately
 * NOT a live reference (no FK to Team, no re-derivation from TeamMember/
 * TeamEventEntry at read time). Written once a participant either leaves
 * their team early (before the event completes, so their result would
 * otherwise be lost when the TeamMember row is hard-deleted) or the event
 * itself completes (for whoever is still on the team then). One row per
 * (user, event), upserted — a coordinator can reopen a COMPLETED event to
 * fix something and complete it again, which should overwrite, not
 * duplicate.
 */
@Entity
@Table(name = "ParticipantEventHistory", uniqueConstraints = {
    @UniqueConstraint(name = "uq_participant_event_history_user_event", columnNames = {"user_id", "event_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipantEventHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Plain int, not a FK — used only to look up the event's *current* live
    // status for TeamQueryService's read-merge rule, never for display. Display
    // data (event name/season/team/rounds/...) lives entirely in resultJson.
    @Column(name = "event_id", nullable = false)
    private Integer eventId;

    // COMPLETED | LEFT_TEAM | REMOVED_BY_LEADER | REMOVED_BY_COORDINATOR
    @Column(name = "snapshot_reason", nullable = false, length = 30)
    private String snapshotReason;

    @Column(name = "snapshot_at", nullable = false)
    private LocalDateTime snapshotAt;

    // Full serialized TeamHistoryResponse — the actual export.
    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @PrePersist
    protected void onCreate() {
        if (snapshotAt == null) {
            snapshotAt = LocalDateTime.now();
        }
    }
}
