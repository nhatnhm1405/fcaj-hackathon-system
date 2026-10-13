package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for POST /api/coordinator/assignments/judges.
 *
 * trackId rule (enforced in the service against Round.is_final):
 *   - preliminary round (is_final = false): trackId REQUIRED.
 *   - final round       (is_final = true):  trackId must be null (judge scores all).
 *
 * judge_type is NOT passed here — it is a property of the user (set when the
 * admin creates the account); internal judges default to INTERNAL on assignment.
 */
@Data
public class AssignJudgeRequest {

    @NotNull(message = "judgeUserId is required")
    private Integer judgeUserId;

    @NotNull(message = "roundId is required")
    private Integer roundId;

    // Required for preliminary rounds, must be null for the final round
    private Integer trackId;
}
