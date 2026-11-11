package com.seal.hackathon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Exactly-once ledger for timer milestone fan-out. There is no scheduler, so
 * milestone notifications are materialised lazily when a client reads the timer
 * state. The unique (round, phase, milestoneKey) key makes each mark fan out to
 * the audience exactly once, no matter how many clients trigger the read at once.
 */
@Entity
@Table(name = "RoundTimerNotice", uniqueConstraints = {
    @UniqueConstraint(name = "uq_timer_notice", columnNames = {"round_id", "phase", "milestone_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoundTimerNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "round_id", nullable = false)
    private Integer roundId;

    @Column(name = "phase", nullable = false, length = 20)
    private String phase;

    // STARTED | REM_30 | REM_15 | REM_5 | REM_1 | HALF | EXPIRED | STOPPED
    @Column(name = "milestone_key", nullable = false, length = 30)
    private String milestoneKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
