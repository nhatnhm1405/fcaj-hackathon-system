package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for POST /api/coordinator/announcements.
 * A coordinator broadcasts an announcement to every participant of an event.
 */
@Data
public class CoordinatorAnnouncementRequest {

    @NotNull(message = "eventId is required")
    private Integer eventId;

    // PARTICIPANT | JUDGE | MENTOR
    @NotBlank(message = "audience is required")
    private String audience;

    @NotBlank(message = "title is required")
    @Size(max = 255, message = "title must be at most 255 characters")
    private String title;

    @NotBlank(message = "content is required")
    @Size(max = 5000, message = "content must be at most 5000 characters")
    private String content;

    // Optional attachment link.
    @Size(max = 1000, message = "link must be at most 1000 characters")
    private String linkUrl;
}
