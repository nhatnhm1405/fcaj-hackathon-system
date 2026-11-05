package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One sent announcement, used for the mentor/coordinator "sent history" views. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementResponse {
    private Integer announcementId;
    private String title;
    private String content;
    private String linkUrl;
    private String senderName;
    private String senderRole;   // MENTOR | COORDINATOR
    private String scope;        // TRACK | EVENT
    private String audience;     // PARTICIPANT | JUDGE | MENTOR
    private Integer eventId;     // event this announcement belongs to (null if none)
    private String scopeLabel;   // track name (mentor) or event name (coordinator)
    private Integer recipientCount;
    private LocalDateTime createdAt;
}
