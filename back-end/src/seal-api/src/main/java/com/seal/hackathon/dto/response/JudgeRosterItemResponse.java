package com.seal.hackathon.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Coordinator-facing roster row: one judge assigned to one round (+track for
 * preliminary rounds). Names resolved so the UI never shows raw IDs. This is the
 * coordinator's "who is assigned where" view, distinct from JudgeAssignmentResponse
 * which is the judge's own "what do I score" view.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JudgeRosterItemResponse {

    private Integer id;
    private Integer judgeUserId;
    private String judgeName;
    private String judgeType;     // INTERNAL | GUEST
    private Integer roundId;
    private String roundName;
    private Boolean isFinal;
    private Integer trackId;      // null for final-round assignments
    private String trackName;
}
