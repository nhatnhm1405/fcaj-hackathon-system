package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionEligibilityResponse {
    private Integer roundId;
    private String roundName;
    private Boolean eligible;
    /** ELIGIBLE | ADVANCED | ELIMINATED | WAITING_FOR_RESULTS */
    private String status;
    private String reason;
    private Integer previousRoundId;
    private String previousRoundName;
    private Integer rankPosition;
    private Integer topNAdvance;
}
