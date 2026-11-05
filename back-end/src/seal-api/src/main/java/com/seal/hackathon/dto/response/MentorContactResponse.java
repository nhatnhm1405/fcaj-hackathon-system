package com.seal.hackathon.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The mentor of a team's track, shown to participants so they know whom to ask
 * for help. One track has (at most) one mentor after the one-track-per-mentor rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MentorContactResponse {

    private Integer userId;
    private String fullName;
    private String email;
    private Integer trackId;
    private String trackName;
}
