package com.seal.hackathon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Score", uniqueConstraints = {
    @UniqueConstraint(name = "uq_score", columnNames = {"submission_id", "judge_user_id", "criteria_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Score {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "score_id")
    private Integer scoreId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "judge_user_id", nullable = false)
    private User judge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criteria_id", nullable = false)
    private ScoringCriteria criteria;

    @Column(name = "value", nullable = false, precision = 5, scale = 2)
    private BigDecimal value;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "is_draft", nullable = false)
    @Builder.Default
    private Boolean isDraft = true;

    @Column(name = "scored_at", nullable = false)
    private LocalDateTime scoredAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (scoredAt == null) {
            scoredAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
