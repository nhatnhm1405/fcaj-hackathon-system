package com.seal.hackathon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Submission", uniqueConstraints = {
    @UniqueConstraint(name = "uq_submission_team_round", columnNames = {"team_id", "round_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "submission_id")
    private Integer submissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @Column(name = "repo_url", length = 500)
    private String repoUrl;

    @Column(name = "demo_url", length = 500)
    private String demoUrl;

    @Column(name = "slide_url", length = 500)
    private String slideUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "SUBMITTED"; // DRAFT | SUBMITTED | LATE | INVALID

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }
    }
}
