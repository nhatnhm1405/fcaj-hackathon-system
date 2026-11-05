package com.seal.hackathon.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * A live, server-authoritative countdown for ONE phase of a round.
 *
 * - phase = CONTEST  → gates team submission (audience: participants)
 * - phase = JUDGING  → gates judge scoring   (audience: assigned judges)
 *
 * The remaining time is ALWAYS derived from {@code endsAt} (never the client
 * clock), so a page reload or backend restart recomputes the same countdown.
 * While PAUSED, {@code endsAt} is frozen and {@code remainingAtPause} holds the
 * seconds left; on resume {@code endsAt = now + remainingAtPause}.
 */
@Entity
@Table(name = "RoundTimer", uniqueConstraints = {
    @UniqueConstraint(name = "uq_roundtimer_round_phase", columnNames = {"round_id", "phase"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoundTimer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "timer_id")
    private Integer timerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) // delete the round → drop its timers
    private Round round;

    // CONTEST | JUDGING
    @Column(name = "phase", nullable = false, length = 20)
    private String phase;

    // IDLE | RUNNING | PAUSED | STOPPED | EXPIRED
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "IDLE";

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "remaining_at_pause")
    private Integer remainingAtPause;

    // CSV of "minutes remaining" reminder marks, e.g. "30,15,5,1". Only marks
    // strictly smaller than the duration ever fire (adaptive to short contests).
    @Column(name = "milestone_minutes", nullable = false, length = 100)
    @Builder.Default
    private String milestoneMinutes = "30,15,5,1";

    // TRUE = also remind when 50% of the duration has elapsed.
    @Column(name = "notify_at_half", nullable = false)
    @Builder.Default
    private Boolean notifyAtHalf = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
