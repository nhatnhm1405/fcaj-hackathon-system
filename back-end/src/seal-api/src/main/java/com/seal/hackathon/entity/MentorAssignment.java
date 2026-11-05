package com.seal.hackathon.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Maps to the `MentorAssignment` table — which track a mentor supports
 * (for the whole event, not per round).
 *
 * Who assigned this and when is captured in AuditLog (action = ASSIGN_MENTOR).
 */
@Entity
@Table(name = "MentorAssignment", uniqueConstraints = {
    @UniqueConstraint(name = "uq_mentor_track", columnNames = {"mentor_user_id", "track_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_user_id", nullable = false)
    private User mentor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
