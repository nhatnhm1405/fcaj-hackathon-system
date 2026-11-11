package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for POST /api/coordinator/assignments/mentors.
 * A mentor supports one track for the whole event.
 */
@Data
public class AssignMentorRequest {

    @NotNull(message = "mentorUserId is required")
    private Integer mentorUserId;

    @NotNull(message = "trackId is required")
    private Integer trackId;
}
