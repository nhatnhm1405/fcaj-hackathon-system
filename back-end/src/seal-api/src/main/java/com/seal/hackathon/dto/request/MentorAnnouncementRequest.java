package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for POST /api/mentor/announcements.
 * A mentor broadcasts an announcement to every participant in one of their tracks.
 */
@Data
public class MentorAnnouncementRequest {

    @NotNull(message = "trackId is required")
    private Integer trackId;

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
