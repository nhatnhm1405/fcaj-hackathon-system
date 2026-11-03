package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Coordinator creates a GUEST judge account and assigns it to a round in one step.
 *
 * The created account is STAFF / judgeType=GUEST, pre-approved and active. The
 * trackId rule mirrors AssignJudgeRequest (required for preliminary rounds, must
 * be null for the final round) and is enforced by the shared assignment logic.
 */
@Data
public class CreateGuestJudgeRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotNull(message = "roundId is required")
    private Integer roundId;

    // Required for preliminary rounds, must be null for the final round
    private Integer trackId;
}
