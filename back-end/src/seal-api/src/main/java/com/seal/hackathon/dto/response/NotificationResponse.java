package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Integer notificationId;
    private String title;
    private String content;
    private String type;
    private Boolean isRead;
    private LocalDateTime createdAt;

    // Populated only for ANNOUNCEMENT notifications (from the linked Announcement).
    // Drives the "From: <name> · <role> · <scope>" line in the email-style popup.
    private String senderName;
    private String senderRole;   // MENTOR | COORDINATOR
    private String scopeLabel;   // track name (mentor) or event name (coordinator)
    private String linkUrl;      // optional attachment link from the announcement
}
