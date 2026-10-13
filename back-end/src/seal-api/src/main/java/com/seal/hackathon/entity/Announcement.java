package com.seal.hackathon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An announcement composed by a Mentor (track-scoped) or a Coordinator
 * (event-scoped). Each announcement fans out into one {@link Notification}
 * per recipient, and is the source of truth for the "sent history" views.
 */
@Entity
@Table(name = "Announcement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "announcement_id")
    private Integer announcementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id", nullable = false)
    private User sender;

    // MENTOR | COORDINATOR
    @Column(name = "sender_role", nullable = false, length = 20)
    private String senderRole;

    // TRACK | EVENT
    @Column(name = "scope", nullable = false, length = 20)
    private String scope;

    // Which audience the coordinator targeted: PARTICIPANT | JUDGE | MENTOR.
    // Always PARTICIPANT for mentor (track) announcements.
    @Column(name = "audience", length = 20)
    private String audience;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private HackathonEvent event;

    // NULL when scope = EVENT
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    private Track track;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    // Optional attachment link (Drive/Form/Repo...).
    @Column(name = "link_url", length = 1000)
    private String linkUrl;

    @Column(name = "recipient_count", nullable = false)
    @Builder.Default
    private Integer recipientCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
