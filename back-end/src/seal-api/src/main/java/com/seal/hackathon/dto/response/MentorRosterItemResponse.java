package com.seal.hackathon.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Coordinator-facing roster row: one mentor assigned to one track (whole event).
 * The coordinator's "who mentors which track" view — distinct from
 * MentorAssignmentResponse which is the mentor's own "which teams do I support".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MentorRosterItemResponse {

    private Integer id;
    private Integer mentorUserId;
    private String mentorName;
    private Integer trackId;
    private String trackName;
}
