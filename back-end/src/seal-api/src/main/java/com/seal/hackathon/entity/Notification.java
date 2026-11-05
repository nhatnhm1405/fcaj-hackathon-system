package com.seal.hackathon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Integer notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    // ANNOUNCEMENT | RESULT | REMINDER | ASSIGNMENT | APPROVAL
    @Column(name = "type", length = 50)
    private String type;

    // Set only for ANNOUNCEMENT notifications fanned out from an Announcement;
    // lets the UI resolve the sender (From) and scope for the email-style popup.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announcement_id")
    private Announcement announcement;

    // Optional direct sender (no Announcement), e.g. a mentor support request from
    // a team leader. Lets non-announcement notifications still resolve a "From"
    // and pop the same email-style detail as announcements.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id")
    private User sender;

    // Human role/context label for the sender (e.g. "Team Leader", "Web Application").
    @Column(name = "sender_role", length = 100)
    private String senderRole;

    @Column(name = "scope_label", length = 255)
    private String scopeLabel;

    // related_event_id removed from schema — notifications are addressed to a user
    // and carry their context in the title/content.

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
